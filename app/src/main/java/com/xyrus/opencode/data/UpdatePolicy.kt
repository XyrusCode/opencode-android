package com.xyrus.opencode.data

import java.io.File

/**
 * App self-update constants and policy.
 *
 * The pure policy (version comparison + cached-APK partitioning) is separated
 * from the Android-bound [UpdateChecker] so it is testable without a device, a
 * PackageManager, or a real APK — mirroring how Video-Plucker keeps its cache
 * policy in `UpdateCachePolicyTest`.
 *
 * Release manifests live at:
 *   https://software.xyruscode.com/opencode/android/latest/version.json
 * and APKs at:
 *   https://software.xyruscode.com/opencode/android/v$VERSION/<apk>
 */

/** Root for release-hosted files (APKs + the Bridge manifest). */
const val BASE_URL = "https://software.xyruscode.com"

/** Directory under `noBackupFilesDir` holding update APKs. Must match the
 * FileProvider root declared in `res/xml/file_paths.xml`. */
const val UPDATES_DIR_NAME = "updates"

private const val APK_SUFFIX = ".apk"
const val PART_SUFFIX = ".part"

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

data class DownloadProgress(
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
)

/** A downloaded update APK found on disk, with the versionCode read from its manifest. */
data class CachedApk(val file: File, val versionCode: Int)

/**
 * Result of an update check. [Failed] covers any network/parse error: checking
 * for an update must never throw and crash the coroutine that asked for it.
 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object None : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/** Filename for a downloaded update APK, version-stamped for the cache policy. */
fun apkFileName(version: String): String = "opencode-$version$APK_SUFFIX"

private fun parseSemver(v: String): List<Int> =
    v.split(".").mapNotNull { it.toIntOrNull() }

/**
 * Lexicographic-by-component version comparison. Returns <0, 0, or >0.
 * Versions that fail to parse component-wise are treated as 0 for that slot.
 */
fun compareVersions(a: String, b: String): Int {
    val pa = parseSemver(a)
    val pb = parseSemver(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val va = pa.getOrElse(i) { 0 }
        val vb = pb.getOrElse(i) { 0 }
        if (va != vb) return va - vb
    }
    return 0
}

/**
 * Decide which cached update APK to keep, and which to delete.
 *
 * Cleanup is a versionCode comparison rather than a heuristic: nothing can
 * observe whether an install actually succeeded, but an APK at or below the
 * installed versionCode has already been installed or superseded, so it is dead
 * weight — and an older cached APK has been overtaken by a newer one.
 *
 * A versionCode of 0 means the manifest could not be parsed, which makes the
 * file unusable, so it is always dropped.
 */
fun partitionCachedApks(
    candidates: List<CachedApk>,
    installedVersionCode: Int,
): Pair<CachedApk?, List<CachedApk>> {
    val keep = candidates
        .filter { it.versionCode > installedVersionCode }
        .maxByOrNull { it.versionCode }
    return keep to candidates.filter { it !== keep }
}
