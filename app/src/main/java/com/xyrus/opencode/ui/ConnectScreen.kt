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
import com.xyrus.opencode.opencode.PairCredential
import com.xyrus.opencode.opencode.ServerConfig
import com.xyrus.opencode.opencode.ServerStore
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ServerStore(context) }

    var servers by remember { mutableStateOf(store.servers()) }
    var selected by remember { mutableStateOf(store.selectedId) }
    var adding by remember { mutableStateOf(false) }
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
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (servers.isEmpty()) {
            Text("No servers saved", style = MaterialTheme.typography.labelMedium, color = TextDim)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { adding = true },
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
                        viewModel.connectDirect(server)
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
                onClick = { adding = true },
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

        if (adding) {
            AddServerDialog(
                onDismiss = { adding = false },
                onSave = { server ->
                    store.save(server)
                    store.selectedId = server.id
                    selected = server.id
                    servers = store.servers()
                    adding = false
                    viewModel.connectDirect(server)
                },
            )
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

/**
 * Add a server. Pairing trades a one-time code for a token; password sends the
 * password directly. Both end up as Basic auth against the same API.
 */
@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AuthKind.Pairing) }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // `opencode pair` prints http://HOST:PORT/auth/connect/CODE. Accept the whole
    // URL or just the code, since both are what people paste.
    fun extractCode(raw: String): String =
        raw.substringAfterLast("/auth/connect/", raw.trim()).trim()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Panel,
        title = { Text("Add a server", color = TextCol) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    placeholder = { Text("http://192.168.1.5:4096") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = kind == AuthKind.Pairing,
                        onClick = { kind = AuthKind.Pairing },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Pairing code") }
                    SegmentedButton(
                        selected = kind == AuthKind.Password,
                        onClick = { kind = AuthKind.Password },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Password") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = {
                        Text(if (kind == AuthKind.Pairing) "Pairing code" else "Password")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (kind == AuthKind.Pairing) {
                        "On your computer run  opencode pair --remote  and paste the link or its " +
                            "code here. Same Wi-Fi instead:  opencode service set hostname 0.0.0.0  " +
                            "then  opencode pair ."
                    } else {
                        "For a server started with a password. Set the address to the host and " +
                            "port the phone can reach, not localhost."
                    },
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && url.isNotBlank() && secret.isNotBlank(),
                onClick = {
                    val base = url.trim().let {
                        if (it.startsWith("http://") || it.startsWith("https://")) it
                        else "http://$it"
                    }
                    busy = true
                    error = null
                    scope.launch {
                        if (kind == AuthKind.Pairing) {
                            val token = exchangePairingCode(base, extractCode(secret))
                            if (token == null) {
                                busy = false
                                error = "Could not pair. Check the address and that the code is fresh."
                            } else {
                                busy = false
                                onSave(
                                    ServerConfig(
                                        id = UUID.randomUUID().toString(),
                                        name = name.ifBlank { base.substringAfter("//") },
                                        baseUrl = base,
                                        secret = token,
                                        kind = AuthKind.Pairing,
                                    )
                                )
                            }
                        } else {
                            busy = false
                            onSave(
                                ServerConfig(
                                    id = UUID.randomUUID().toString(),
                                    name = name.ifBlank { base.substringAfter("//") },
                                    baseUrl = base,
                                    secret = secret.trim(),
                                    kind = AuthKind.Password,
                                )
                            )
                        }
                    }
                },
            ) { Text("Connect", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) }
        },
    )
}

/**
 * Trade a one-time pairing code for a credential.
 *
 * opencode returns `{"token": "..."}` and expects that token as the Basic auth
 * password. Returns null on any failure so the caller can explain.
 */
private suspend fun exchangePairingCode(baseUrl: String, code: String): String? =
    withContext(Dispatchers.IO) {
        if (code.isBlank()) return@withContext null
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/auth/connect/$code")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                org.json.JSONObject(body).optString("token").ifEmpty { null }
            }
        }.getOrNull()
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
