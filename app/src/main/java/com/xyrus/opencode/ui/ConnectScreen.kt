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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xyrus.opencode.BridgeViewModel
import com.xyrus.opencode.opencode.AuthKind
import com.xyrus.opencode.opencode.FrameSource
import com.xyrus.opencode.opencode.PairLink
import com.xyrus.opencode.opencode.ServerConfig
import com.xyrus.opencode.opencode.ServerStore
import com.xyrus.opencode.opencode.catalogCacheFile
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.BorderCol
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * "Connect to your server".
 *
 * Two ways in, both against opencode's own HTTP API:
 *  - a pairing code from `opencode pair`, which trades for a token;
 *  - a host and password, for a server started with a password.
 *
 * The desktop bridge is still offered underneath as a fallback.
 */
@Composable
fun ConnectScreen(
    viewModel: BridgeViewModel,
    onOpenUpdates: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    /**
     * Add-server is a full screen with its own scroller, so it cannot be
     * composed inside this one: nesting a verticalScroll inside another gives
     * the inner one infinite height and Compose throws. Navigation happens at
     * the Activity level instead.
     */
    onAddServer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ServerStore(context) }

    var servers by remember { mutableStateOf(store.servers()) }
    var selected by remember { mutableStateOf(store.selectedId) }
    var showBridge by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Connect to your server",
                style = MaterialTheme.typography.titleLarge,
                color = TextCol,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenUpdates) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextDim)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "OpenCompanion drives sessions on an opencode server running on your machine.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )

        Spacer(Modifier.height(28.dp))

        // Connected but no session open: the drawer is the only way onward, and
        // it is otherwise only reachable from the session screen.
        if (state.isConnected) {
            Button(
                onClick = onOpenDrawer,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Browse sessions") }
            Spacer(Modifier.height(20.dp))
        } else if (state.connection == FrameSource.State.Failed) {
            // A failed connection has to say so. Showing this only when
            // connected meant a connection that never came up looked identical
            // to an idle screen.
            Text(
                state.error ?: "Could not reach that server.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            servers.firstOrNull { it.id == selected }?.let { server ->
                OutlinedButton(
                    onClick = { viewModel.connectDirect(server) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Try again") }
                Spacer(Modifier.height(20.dp))
            }
        }

        state.error?.takeIf { state.isConnected }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
        }

        if (servers.isEmpty()) {
            Text("No servers saved", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAddServer,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add a server") }
        } else {
            servers.forEach { server ->
                ServerRow(
                    server = server,
                    connected = selected == server.id && state.isConnected,
                    onClick = {
                        selected = server.id
                        store.selectedId = server.id
                        viewModel.connectDirect(server, catalogCacheFile(context, server.id))
                    },
                    onRemove = {
                        store.delete(server.id)
                        servers = store.servers()
                        if (selected == server.id) selected = null
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onAddServer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a server")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Use the desktop bridge instead",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showBridge = !showBridge }
                .padding(vertical = 8.dp),
        )
        if (showBridge) {
            BridgeFallback(viewModel, store)
        }

    }
}

@Composable
private fun ServerRow(
    server: ServerConfig,
    connected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Panel2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                server.name.take(1).uppercase(),
                color = Accent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, color = TextCol, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(server.baseUrl)
                    val days = server.daysRemaining
                    if (days != null) {
                        append(if (days <= 0L) " · expired" else " · ${days}d left")
                    }
                },
                color = if (server.isExpired) MaterialTheme.colorScheme.error else TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (connected) {
            Text("live", color = Accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = onRemove) {
            Text("Remove", color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun BridgeFallback(viewModel: BridgeViewModel, store: ServerStore) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = state.host,
            onValueChange = { viewModel.setHost(it) },
            label = { Text("Bridge host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                store.useBridge = true
                store.bridgeHost = state.host
                viewModel.connectBridge()
            },
            enabled = state.host.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect via bridge") }
        Spacer(Modifier.height(6.dp))
        Text(
            "Runs through opencode-bridge on your machine. Sees only sessions from the v1 server.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
