package com.xyrus.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.RealProject
import com.xyrus.opencode.bridge.Session
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Err
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim

/** Compact "just now / 6h / 7d / 3w" age for a session, like Antigravity's list. */
private fun relativeAge(updated: Long?): String {
    if (updated == null) return ""
    val mins = (System.currentTimeMillis() - updated) / 60_000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 1440 -> "${mins / 60}h"
        mins < 10_080 -> "${mins / 1440}d"
        mins < 43_200 -> "${mins / 10_080}w"
        else -> "${mins / 43_200}mo"
    }
}

/** How many recent sessions a project shows before the list gets unwieldy. */
private const val PROJECT_SESSION_PREVIEW = 5

/**
 * Group sessions into real projects for the drawer, newest session first, capped
 * to [preview] sessions each.
 *
 * Scratch sessions under a Temp directory are dropped so navigation stays real.
 * Searching narrows to matching sessions (or the whole project when the project
 * name matches) *before* the cap, so a hit older than the preview still shows.
 */
fun groupSessionsByProject(
    sessions: List<Session>,
    search: String = "",
    preview: Int = PROJECT_SESSION_PREVIEW,
): List<Pair<RealProject, List<Session>>> =
    sessions
        .filter { s ->
            val d = s.directory
            d != null && !d.contains("\\Temp\\", ignoreCase = true) && !d.contains("/Temp/", ignoreCase = true)
        }
        .groupBy { it.directory!! }
        .map { (dir, inDir) ->
            val name = dir.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
            val project = RealProject(name, dir)
            // Newest first. While searching, narrow to sessions whose title
            // matches so the preview cap surfaces the hit instead of the five
            // newest; a project-name match shows that whole project.
            val ordered = inDir.sortedByDescending { it.updated ?: 0L }
            val visible = when {
                search.isBlank() -> ordered
                project.name.contains(search, true) -> ordered
                else -> ordered.filter { it.title.contains(search, true) }
            }
            project to visible
        }
        .filter { (_, visible) -> visible.isNotEmpty() }
        .map { (project, visible) -> project to visible.take(preview) }
        .sortedBy { it.first.name.lowercase() }

/**
 * Navigation drawer, Antigravity-style: New Conversation, search, then real
 * projects (grouped from session directories) that expand to reveal their
 * sessions with relative ages. Settings/Updates and Disconnect in the footer.
 */
@Composable
fun ConversationsDrawer(
    viewModel: BridgeViewModel,
    onPick: (Session) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val expanded = remember { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Session?>(null) }
    var renaming by remember { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    val groups: List<Pair<RealProject, List<Session>>> = remember(state.sessions, search) {
        groupSessionsByProject(state.sessions, search)
    }

    ModalDrawerSheet(drawerContainerColor = Bg) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "OpenCompanion",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                fontFamily = PlexMono,
                color = TextCol,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            Text(
                if (state.isConnected) "connected to opencode" else "not connected",
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
                Text("New Conversation", style = MaterialTheme.typography.labelLarge, color = TextCol)
            }

            TextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search conversations", color = TextDim) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = transparentFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCol),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Projects", style = MaterialTheme.typography.labelMedium, color = TextDim)
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (groups.isEmpty()) {
                    item {
                        Text(
                            if (search.isBlank()) "No projects yet. Start a session on your machine." else "No matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                groups.forEach { (project, sessions) ->
                    val isOpen = project.directory in expanded
                    item(key = "p-${project.directory}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isOpen) expanded.remove(project.directory) else expanded.add(project.directory)
                                }
                                .padding(end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = TextDim,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                project.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextCol,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = if (isOpen) "Collapse" else "Expand",
                                tint = TextDim,
                                modifier = Modifier.size(18.dp).rotate(if (isOpen) 180f else 0f),
                            )
                        }
                    }
                    if (isOpen) {
                        items(sessions, key = { it.id }) { session ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (session.id == state.attachedSession?.id) Modifier.background(Panel2) else Modifier,
                                    )
                                    .clickable { onPick(session) }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (session.id == state.attachedSession?.id) Accent else TextCol,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    relativeAge(session.updated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim,
                                )
                                Spacer(Modifier.width(4.dp))
                                // Overflow menu: rename / delete without a long-press.
                                Box {
                                    IconButton(
                                        onClick = { menuFor = session },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Session options",
                                            tint = TextDim,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuFor?.id == session.id,
                                        onDismissRequest = { menuFor = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                menuFor = null
                                                renaming = session
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = Err) },
                                            onClick = {
                                                menuFor = null
                                                deleting = session
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderCol)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gear and label are one tap target; the label alone used to be dead.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenUpdates() }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextDim)
                    Spacer(Modifier.width(10.dp))
                    Text("Settings", style = MaterialTheme.typography.labelLarge, color = TextDim)
                }
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

            // Rename: a plain text field, since titles are short and free-form.
            renaming?.let { target ->
                var draft by remember(target.id) { mutableStateOf(target.title) }
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    title = { Text("Rename session", color = TextCol) },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            colors = transparentFieldColors(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextCol),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.renameSession(target.id, draft)
                            renaming = null
                        }) { Text("Save", color = Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { renaming = null }) { Text("Cancel", color = TextDim) }
                    },
                    containerColor = Panel2,
                )
            }

            // Delete is permanent on opencode's side, so make that explicit.
            deleting?.let { target ->
                AlertDialog(
                    onDismissRequest = { deleting = null },
                    title = { Text("Delete session?", color = TextCol) },
                    text = {
                        Text(
                            "\"${target.title}\" and its messages are removed for good. This cannot be undone.",
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteSession(target.id)
                            deleting = null
                        }) { Text("Delete", color = Err) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = null }) { Text("Cancel", color = TextDim) }
                    },
                    containerColor = Panel2,
                )
            }
        }
    }
}
