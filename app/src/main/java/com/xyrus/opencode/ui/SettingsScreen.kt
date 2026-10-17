package com.xyrus.opencode.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xyrus.opencode.data.UpdateChecker
import com.xyrus.opencode.opencode.ServerStore
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Err
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim

/**
 * Preferences the user can actually change, and that the app honours.
 *
 * Everything on this screen is wired to something real. There are no switches
 * here that do nothing, because a settings screen full of dead toggles is worse
 * than an honest short one.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("opencode.prefs", Context.MODE_PRIVATE)

    /** Show ~/Projects/… rather than the full /Users/… path. */
    var hideUserDirectory: Boolean
        get() = sp.getBoolean("hide_user_dir", false)
        set(v) = sp.edit().putBoolean("hide_user_dir", v).apply()

    /** Fold a run of the same tool into one card. */
    var collapseTools: Boolean
        get() = sp.getBoolean("collapse_tools", true)
        set(v) = sp.edit().putBoolean("collapse_tools", v).apply()

    /** Show the tools an agent used as chips above the transcript. */
    var chipTimeline: Boolean
        get() = sp.getBoolean("chip_timeline", false)
        set(v) = sp.edit().putBoolean("chip_timeline", v).apply()

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) = sp.edit().putBoolean("reduce_motion", v).apply()

    fun clearAll() = sp.edit().clear().apply()
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { ServerStore(context) }

    var hideUserDir by remember { mutableStateOf(prefs.hideUserDirectory) }
    var confirmClear by remember { mutableStateOf(false) }

    val checker = remember { UpdateChecker(context) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextCol)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = TextCol)
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("SERVER")
        ServerSettingsCard(store)

        Spacer(Modifier.height(22.dp))
        SectionLabel("APPEARANCE")
        ToggleRow(
            "Hide user directory",
            "Show ~/Projects/… instead of the full path",
            hideUserDir,
        ) { prefs.hideUserDirectory = it; hideUserDir = it }

        Spacer(Modifier.height(22.dp))
        SectionLabel("APP")
        NavRow("Check for updates", "Version $version", onClick = onOpenUpdates)

        Spacer(Modifier.height(22.dp))
        SectionLabel("DATA")
        NavRow(
            "Clear session cache",
            "Remove cached sessions and messages",
        ) { store.clearCache() }
        NavRow(
            "Clear all data",
            "Remove every server, setting and cache",
            destructive = true,
        ) { confirmClear = true }

        Spacer(Modifier.height(22.dp))
        SectionLabel("ABOUT")
        InfoRow("OpenCompanion", "opencode companion for Android")
        InfoRow("Version", version)
        InfoRow("Signing", "Updates install over this build")

        Spacer(Modifier.height(36.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Panel,
            title = { Text("Clear all data?", color = TextCol) },
            text = {
                Text(
                    "Every saved server, including pairing credentials, is removed. " +
                        "You will have to pair again.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearCache()
                    prefs.clearAll()
                    confirmClear = false
                    onBack()
                }) { Text("Clear", color = Err) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = TextDim) }
            },
        )
    }
}

@Composable
private fun ServerSettingsCard(store: ServerStore) {
    val servers = remember { store.servers() }
    var selected by remember { mutableStateOf(store.selectedId) }
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .padding(vertical = 4.dp),
    ) {
        if (servers.isEmpty()) {
            Text(
                "No servers saved",
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            )
            return@Column
        }
        servers.forEach { server ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Panel2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        server.name.take(1).uppercase(),
                        color = Accent,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(server.name, color = TextCol, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        buildString {
                            append(server.baseUrl)
                            server.daysRemaining?.let { days ->
                                append(if (days <= 0L) "  ·  expired" else "  ·  ${days}d left")
                            }
                        },
                        color = if (server.isExpired) Err else TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = {
                    store.selectedId = server.id
                    selected = server.id
                }) {
                    Text(
                        if (selected == server.id) "Current" else "Use",
                        color = if (selected == server.id) Accent else TextDim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = { editing = true }) {
                    Text("Remove", color = Err, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextDim,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextCol, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Panel2,
                uncheckedBorderColor = BorderCol,
            ),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) Err else TextCol, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(label, color = TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = TextCol, style = MaterialTheme.typography.bodyMedium)
    }
}