package com.xyrus.opencode.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.UiState
import com.xyrus.opencode.bridge.BridgeClient
import com.xyrus.opencode.bridge.Project
import com.xyrus.opencode.bridge.Session
import java.util.Calendar

/** Sentinel project id meaning "show every project". */
private const val ALL_PROJECTS = ""

private val BridgeClient.State.isBusy: Boolean
    get() = this == BridgeClient.State.Connecting || this == BridgeClient.State.Authenticating

private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** A session belongs to a project when its directory sits inside the project's worktree. */
private fun Session.belongsTo(project: Project?): Boolean {
    val worktree = project?.worktree ?: return true
    val dir = directory ?: return false
    return dir == worktree || dir.endsWith(worktree)
}

/**
 * Pairing and session picker.
 *
 * Project-first: the bridge pushes a project catalog, and the sessions shown are
 * the ones inside the selected project (grouped Today / Older, searchable),
 * rather than one flat dump of every session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: BridgeViewModel,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    // "" = All projects; a project id selects one.
    var selectedProjectId by rememberSaveable { mutableStateOf(ALL_PROJECTS) }
    var projectTouched by rememberSaveable { mutableStateOf(false) }

    // Default to the first real project (project-first view) once catalogs land.
    LaunchedEffect(state.projects) {
        if (!projectTouched && selectedProjectId == ALL_PROJECTS) {
            state.projects.firstOrNull { it.worktree != null }?.id?.let {
                selectedProjectId = it.orEmpty()
            }
        }
    }

    val selectedProject = state.projects.firstOrNull { it.id == selectedProjectId }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("opencode") },
                actions = {
                    IconButton(onClick = onOpenUpdates) {
                        Icon(Icons.Default.Settings, contentDescription = "App updates")
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
                .padding(horizontal = 16.dp),
        ) {
            if (!state.isConnected) {
                ConnectPanel(viewModel)
            } else {
                ConnectedPanel(
                    viewModel = viewModel,
                    state = state,
                    search = search,
                    onSearchChange = { search = it },
                    selectedProject = selectedProject,
                    onSelectProject = { id ->
                        projectTouched = true
                        selectedProjectId = id
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectPanel(viewModel: BridgeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::setHost,
            label = { Text("Desktop IP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.port.toString(),
            onValueChange = { raw -> raw.toIntOrNull()?.let(viewModel::setPort) },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.connection.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = viewModel::connect,
            enabled = state.host.isNotBlank() && !state.connection.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.connection.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    when (state.connection) {
                        BridgeClient.State.Connecting -> "Connecting…"
                        else -> "Authenticating…"
                    },
                )
            } else {
                Text(if (state.connection == BridgeClient.State.Failed) "Retry" else "Connect")
            }
        }

        Spacer(Modifier.size(4.dp))
        Text(
            text = "Start the bridge on your desktop first: python -m bridge",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectedPanel(
    viewModel: BridgeViewModel,
    state: UiState,
    search: String,
    onSearchChange: (String) -> Unit,
    selectedProject: Project?,
    onSelectProject: (String) -> Unit,
) {
    val cutoff = startOfTodayMillis()
    val sessions = state.sessions
        .filter { it.belongsTo(selectedProject) }
        .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
        .sortedByDescending { it.updated ?: 0L }
    val today = sessions.filter { (it.updated ?: 0L) >= cutoff }
    val older = sessions.filter { (it.updated ?: 0L) < cutoff }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = viewModel::createSession) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New session")
            }
            OutlinedButton(onClick = viewModel::loadSessions) { Text("Refresh") }
            OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
        }

        // Projects drive the view; a chip row stands in for the desktop sidebar.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = { onSelectProject(ALL_PROJECTS) },
                    label = { Text("All projects") },
                    leadingIcon = if (selectedProject == null) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
            items(state.projects, key = { it.id.orEmpty() }) { project ->
                AssistChip(
                    onClick = { onSelectProject(project.id.orEmpty()) },
                    label = { Text(project.name ?: project.id.orEmpty()) },
                    leadingIcon = if (selectedProject?.id == project.id) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = {
                Text("Search sessions in ${selectedProject?.name ?: "all projects"}")
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        if (sessions.isEmpty()) {
            Text(
                text = if (search.isBlank()) {
                    "No sessions here. Tap New session to start one."
                } else {
                    "No sessions match \"$search\"."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (today.isNotEmpty()) {
                    item { GroupHeader("Today") }
                    items(today, key = { it.id }) { session ->
                        SessionRow(session) { viewModel.attach(session) }
                    }
                }
                if (older.isNotEmpty()) {
                    item { GroupHeader("Older") }
                    items(older, key = { it.id }) { session ->
                        SessionRow(session) { viewModel.attach(session) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            if (session.subtitle.isNotBlank()) {
                Text(
                    session.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}