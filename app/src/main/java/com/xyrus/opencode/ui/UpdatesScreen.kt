package com.xyrus.opencode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.data.UpdateCheckResult
import com.xyrus.opencode.data.UpdateChecker
import com.xyrus.opencode.data.UpdateInfo
import com.xyrus.opencode.ui.components.GlassCard
import com.xyrus.opencode.ui.components.GradientProgressIndicator
import com.xyrus.opencode.ui.components.GlowButton
import com.xyrus.opencode.ui.components.SectionHeader
import kotlinx.coroutines.launch

/** In-app updater: shows the installed version, downloads a newer APK, installs it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    checker: UpdateChecker,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }

    val progress by checker.downloadProgress.collectAsStateWithLifecycle()
    val downloaded by checker.downloadedApk.collectAsStateWithLifecycle()

    val installed = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    suspend fun refresh() {
        checking = true
        result = checker.checkForUpdate()
        info = (result as? UpdateCheckResult.Available)?.info
        checking = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("App updates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Installed", subtitle = "Version $installed")
            }

            when {
                checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium)
                }

                info != null -> GlassCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        "Update available",
                        subtitle = "Version ${info?.latestVersion}",
                    )
                    if (!info?.releaseNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info?.releaseNotes.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(14.dp))
                    val apk = downloaded
                    if (apk != null) {
                        GlowButton("Install update", onClick = { checker.installApk(apk) })
                    } else {
                        GlowButton(
                            "Download",
                            enabled = progress == null,
                            onClick = { scope.launch { info?.let { checker.downloadApk(it) } } },
                        )
                        progress?.let { p ->
                            Spacer(Modifier.height(10.dp))
                            GradientProgressIndicator(progress = p.percent)
                        }
                    }
                }

                result is UpdateCheckResult.None ->
                    Text("You’re up to date.", style = MaterialTheme.typography.bodyMedium)

                else -> Text(
                    "Could not check for updates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            GlowButton("Check again", onClick = { scope.launch { refresh() } })
        }
    }
}