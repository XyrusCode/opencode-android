package com.xyrus.opencode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.bridge.BridgeClient
import com.xyrus.opencode.bridge.Session

/**
 * Pairing and session picker.
 *
 * Shown when there is no connected bridge or no attached session; the session
 * screen takes over as soon as one is attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: BridgeViewModel,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let(viewModel::setPort)
                },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isConnected,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.isConnected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::disconnect) {
                        Text("Disconnect")
                    }
                    OutlinedButton(onClick = viewModel::loadSessions) {
                        Text("Refresh")
                    }
                    OutlinedButton(onClick = viewModel::createSession) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New session")
                    }
                }
                Spacer(Modifier.height(4.dp))
                SessionList(viewModel)
            } else {
                Button(
                    onClick = viewModel::connect,
                    enabled = state.host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.connection == BridgeClient.State.Failed) "Retry" else "Connect")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Start the bridge on your desktop first: python -m bridge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionList(viewModel: BridgeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.sessions.isEmpty()) {
        Text(
            text = "No sessions yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.sessions, key = { it.id }) { session ->
            SessionRow(session) { viewModel.attach(session) }
        }
    }
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
