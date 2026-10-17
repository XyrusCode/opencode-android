package com.xyrus.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.bridge.Project
import com.xyrus.opencode.bridge.Session
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim
import java.util.Calendar

private const val ALL_PROJECTS = ""

private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun Session.belongsTo(project: Project?): Boolean {
    val worktree = project?.worktree ?: return true
    val dir = directory ?: return false
    return dir == worktree || dir.endsWith(worktree)
}

/**
 * ChatGPT-style navigation drawer: opencode wordmark, new session, project
 * chips, searchable conversation list grouped Today/Older, and a footer.
 */
@Composable
fun ConversationsDrawer(
    viewModel: BridgeViewModel,
    onPick: (Session) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf(ALL_PROJECTS) }
    val project = state.projects.firstOrNull { it.id == projectId }

    ModalDrawerSheet(drawerContainerColor = Bg) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "opencode",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                fontFamily = PlexMono,
                color = TextCol,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            Text(
                if (state.isConnected) "connected" else "not connected",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isConnected) Accent else TextDim,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Panel2)
                    .clickable(enabled = state.isConnected) { viewModel.createSession() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New session", style = MaterialTheme.typography.labelLarge, color = TextCol)
            }

            TextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search sessions", color = TextDim) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = transparentFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCol),
            )

            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DrawerChip("All", project == null) { projectId = ALL_PROJECTS } }
                items(state.projects, key = { it.id.orEmpty() }) { p ->
                    DrawerChip(p.name ?: p.id.orEmpty(), project?.id == p.id) { projectId = p.id.orEmpty() }
                }
            }

            val cutoff = startOfTodayMillis()
            val sessions = state.sessions
                .filter { it.belongsTo(project) }
                .filter { search.isBlank() || it.title.contains(search, true) }
                .sortedByDescending { it.updated ?: 0L }
            val today = sessions.filter { (it.updated ?: 0L) >= cutoff }
            val older = sessions.filter { (it.updated ?: 0L) < cutoff }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (today.isNotEmpty()) {
                    item { DrawerGroupLabel("Today") }
                    items(today, key = { it.id }) {
                        DrawerSessionRow(it, state.attachedSession?.id == it.id) { onPick(it) }
                    }
                }
                if (older.isNotEmpty()) {
                    item { DrawerGroupLabel("Older") }
                    items(older, key = { it.id }) {
                        DrawerSessionRow(it, state.attachedSession?.id == it.id) { onPick(it) }
                    }
                }
                if (sessions.isEmpty()) {
                    item {
                        Text(
                            if (search.isBlank()) "No sessions here." else "No matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderCol)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenUpdates) {
                    Icon(Icons.Default.Settings, contentDescription = "App updates", tint = TextDim)
                }
                Text("Updates", style = MaterialTheme.typography.labelLarge, color = TextDim)
                Spacer(Modifier.weight(1f))
                Text(
                    "Disconnect",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.disconnect() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextDim, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun DrawerSessionRow(session: Session, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(Panel2) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            session.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Accent else TextCol,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Accent else TextDim,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Panel2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
