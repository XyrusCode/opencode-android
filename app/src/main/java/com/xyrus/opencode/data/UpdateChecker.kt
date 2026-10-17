package com.xyrus.opencode.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import com.xyrus.opencode.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val MANIFEST_PATH = "opencode/android/latest/version.json"
private const val DOWNLOAD_TIMEOUT_SECONDS = 60L

/**
 * In-app self-update. Pulls a `version.json` manifest and APKs from the R2
 * bucket the release workflow publishes to (see `android-build.yml`), modeled on
 * Video-Plucker's UpdateChecker.
 *
 * version.json at `BASE_URL/opencode/android/latest/version.json`:
 *   { "version": "0.2.2",
 *     "apkFiles": { "arm64_v8a": "opencode-0.2.2.apk", "universal": "opencode-0.2.2.apk" },
 *     "releaseNotes": "..." }
 *
 * APKs live under the versioned key `opencode/android/v{version}/{apk}`.
 *
 * The cache policy (version comparison + which APK to keep/delete) lives in
 * [UpdatePolicy.kt] and is unit-tested without a device.
 */
class UpdateChecker(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val updatesDir = File(context.noBackupFilesDir, UPDATES_DIR_NAME).apply { mkdirs() }

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _downloadedApk = MutableStateFlow<File?>(null)
    val downloadedApk: StateFlow<File?> = _downloadedApk.asStateFlow()

    init {
        _downloadedApk.value = restoreDownloadedApk()
    }

    /** Fetch the manifest from R2. Null if no usable manifest is reachable. */
    private suspend fun fetchManifest(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("$BASE_URL/$MANIFEST_PATH").build()).execute()
                .use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.string()?.let { JSONObject(it) }
                }
        }.getOrNull()
    }

    suspend fun latestRelease(): UpdateInfo? = fetchManifest()?.let { json ->
        json.optString("version", "").takeIf { it.isNotBlank() }?.let { version ->
            UpdateInfo(
                latestVersion = version,
                downloadUrl = json.optString("downloadUrl", ""),
                releaseNotes = json.optString("releaseNotes", ""),
            )
        }
    }

    /** Compare the manifest version to the one currently installed. */
    suspend fun checkForUpdate(): UpdateCheckResult = fetchManifest()?.let { json ->
        val version = json.optString("version", "")
        val installed = installedVersionName()
        when {
            version.isBlank() -> UpdateCheckResult.Failed
            compareVersions(version, installed) <= 0 -> UpdateCheckResult.None
            else -> resolveDownload(json, version)?.let { UpdateCheckResult.Available(it) }
                ?: UpdateCheckResult.None
        }
    } ?: UpdateCheckResult.Failed

    private fun resolveDownload(json: JSONObject, version: String): UpdateInfo? {
        val apkFiles = json.optJSONObject("apkFiles") ?: return null
        val filename = apkFiles.optString(preferredAbi())
            .takeIf { it.isNotBlank() }
            ?: apkFiles.optString("universal").takeIf { it.isNotBlank() } ?: return null
        return UpdateInfo(
            latestVersion = version,
            downloadUrl = "$BASE_URL/opencode/android/v$version/$filename",
            releaseNotes = json.optString("releaseNotes", ""),
        )
    }

    private fun preferredAbi(): String = when {
        "arm64-v8a" in Build.SUPPORTED_ABIS -> "arm64_v8a"
        "armeabi-v7a" in Build.SUPPORTED_ABIS -> "armeabi_v7a"
        "x86_64" in Build.SUPPORTED_ABIS -> "x86_64"
        "x86" in Build.SUPPORTED_ABIS -> "x86"
        else -> "universal"
    }

    /**
     * Stream [info]'s APK into `updates/` as a `.part` file, fsync, then atomically
     * rename — a crash mid-download never leaves a half-installed APK behind.
     */
    suspend fun downloadApk(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val target = File(updatesDir, apkFileName(info.latestVersion))
            val part = File(updatesDir, apkFileName(info.latestVersion) + PART_SUFFIX)
            if (part.exists()) part.delete()

            val response = http.newCall(
                Request.Builder().url(info.downloadUrl).build(),
            ).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            val expected = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buf)
                    while (read != -1) {
                        if (read > 0) {
                            out.write(buf, 0, read)
                            downloaded += read
                            if (expected > 0) {
                                _downloadProgress.value = DownloadProgress(
                                    (downloaded / expected.toFloat()).coerceIn(0f, 1f),
                                    downloaded,
                                    expected,
                                )
                            }
                        }
                        read = input.read(buf)
                    }
                    out.flush()
                }
            }
            syncFile(part)
            if (!part.renameTo(target)) return@withContext null
            _downloadedApk.value = target
            target
        } catch (_: Exception) {
            null
        } finally {
            _downloadProgress.value = null
        }
    }

    private fun syncFile(file: File) {
        try {
            FileOutputStream(file).use { it.fd.sync() }
        } catch (_: Exception) {
        }
    }

    /** Re-surface a crash-safe cached APK that is newer than the install. */
    private fun restoreDownloadedApk(): File? {
        val installed = installedVersionCode()
        val candidates = updatesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk") }
            ?.mapNotNull { parseVersionCode(it)?.let { vc -> CachedApk(it, vc) } }
            ?: emptyList()
        val (keep, drop) = partitionCachedApks(candidates, installed)
        drop.forEach { it.file.delete() }
        return keep?.file
    }

    private fun installedVersionCode(): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toInt()
    } catch (_: Exception) {
        0
    }

    /** versionName of the currently installed build, for update comparison. */
    private fun installedVersionName(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun parseVersionCode(file: File): Int? = try {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?.let { PackageInfoCompat.getLongVersionCode(it).toInt() }
    } catch (_: Exception) {
        null
    }

    /**
     * Install [file] via a FileProvider URI — no external-storage permission.
     * On Android 11+ package visibility makes resolveActivity() return null for the
     * installer, so we launch directly and treat only a real launch failure as an
     * error (surfaced to the caller).
     */
    fun installApk(file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "could not launch the package installer", e)
            false
        }
    }

    /**
     * True when we may prompt for "install unknown apps".
     *
     * Android 8+ blocks installing an APK until the user grants this per-app. The
     * install intent below silently does nothing without it, which is why the
     * updater used to look broken: the download succeeded, the tap did nothing.
     * There is no Intent for this grant, so the caller must send the user to
     * Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES.
     */
    fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Intent that opens the per-app "allow from this source" screen. */
    @Suppress("UnspecifiedImmutableFlag")
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}"),
        )

    /** Android 13+ needs POST_NOTIFICATIONS or the update notice is never shown. */
    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Notify the user an update is ready to install. */
    fun notifyUpdateReady(info: UpdateInfo) {
        val channelId = "updates"
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.update_ready_title))
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentText(context.getString(R.string.update_ready_text, info.latestVersion))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
