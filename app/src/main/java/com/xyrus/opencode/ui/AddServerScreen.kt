package com.xyrus.opencode.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xyrus.opencode.opencode.AuthKind
import com.xyrus.opencode.opencode.PairLink
import com.xyrus.opencode.opencode.Pairer
import com.xyrus.opencode.opencode.ServerConfig
import com.xyrus.opencode.ui.theme.Accent
import com.xyrus.opencode.ui.theme.Bg
import com.xyrus.opencode.ui.theme.Err
import com.xyrus.opencode.ui.theme.Panel
import com.xyrus.opencode.ui.theme.Panel2
import com.xyrus.opencode.ui.theme.PlexMono
import com.xyrus.opencode.ui.theme.TextCol
import com.xyrus.opencode.ui.theme.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Add a server.
 *
 * One screen, three ways in, because they are the same thing underneath:
 *  - scan the QR from `opencode pair`, which carries one code plus every address
 *    the machine answers on, already ranked;
 *  - type that code, if scanning is not convenient;
 *  - use a host and password for a server started with one.
 *
 * Whatever supplies the code, the same exchange runs: try each candidate address
 * until one answers.
 */
@Composable
fun AddServerScreen(
    onBack: () -> Unit,
    onSaved: (ServerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // What the user has supplied, however they supplied it.
    var scanned by remember { mutableStateOf<PairLink.Parts?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var hostInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AuthKind.Pairing) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showScanner = true else permissionDenied = true
    }

    // The candidate addresses for whatever code we ended up with.
    val parts = scanned ?: PairLink.resolve(
        addressInput = hostInput,
        codeInput = codeInput,
    )

    fun connect() {
        val current = parts
        if (current == null) {
            error = "Scan the QR, or enter a pairing code"
            return
        }
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                Pairer.pair(current.code, current.bases.filter { it.isNotBlank() })
            }
            busy = false
            when (outcome) {
                is Pairer.Outcome.Paired -> onSaved(
                    ServerConfig(
                        id = UUID.randomUUID().toString(),
                        name = name.ifBlank { outcome.base.substringAfter("//") },
                        baseUrl = outcome.base,
                        secret = outcome.token,
                        kind = AuthKind.Pairing,
                    )
                )

                is Pairer.Outcome.Rejected -> error =
                    "That code was rejected. Codes work once and last 5 minutes, " +
                        "so run opencode pair again."

                is Pairer.Outcome.Unreachable -> {
                    val tried = outcome.tried
                    error = if (tried.isEmpty()) {
                        "Could not reach the server. Check the address."
                    } else {
                        "Could not reach any of the addresses in that pairing " +
                            "(${tried.size} tried). If you are off the network, use " +
                            "opencode pair --remote, or bind the service with " +
                            "opencode service set hostname 0.0.0.0."
                    }
                }
            }
        }
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
            Text("Add server", style = MaterialTheme.typography.titleLarge, color = TextCol)
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "On your computer, run this, then scan the QR.",
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )
        Spacer(Modifier.height(8.dp))
        CommandBlock("opencode pair --remote")
        Text(
            "First run takes about a minute. The service starts if it is not " +
                "already running.",
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )

        Spacer(Modifier.height(18.dp))
        Text("Same Wi-Fi instead", style = MaterialTheme.typography.bodyMedium, color = TextCol)
        Text(
            "Plain pair shows a QR once the server is reachable beyond localhost.",
            style = MaterialTheme.typography.bodySmall, color = TextDim,
        )
        Spacer(Modifier.height(8.dp))
        CommandBlock("opencode service set hostname 0.0.0.0")
        Spacer(Modifier.height(6.dp))
        CommandBlock("opencode pair")

        Spacer(Modifier.height(24.dp))

        // --- the three doors ------------------------------------------------
        Button(
            onClick = {
                error = null
                cameraPermission.launch(Manifest.permission.CAMERA)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Scan QR") }

        if (permissionDenied) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Camera access is off. Enter the code instead, or enable the " +
                    "camera in system settings.",
                color = Err, style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = mode == AuthKind.Pairing,
                onClick = { mode = AuthKind.Pairing },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Pairing code") }
            SegmentedButton(
                selected = mode == AuthKind.Password,
                onClick = { mode = AuthKind.Password },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Password") }
        }

        Spacer(Modifier.height(12.dp))

        if (mode == AuthKind.Pairing) {
            OutlinedTextField(
                value = if (scanned != null) "Scanned from QR" else codeInput,
                onValueChange = { if (scanned == null) codeInput = it else scanned = null },
                label = { Text("Pairing code") },
                placeholder = { Text("or paste the whole link") },
                singleLine = true,
                readOnly = scanned != null,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (scanned != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Addresses in this pairing: ${parts?.bases?.filter { it.isNotBlank() }?.size ?: 0}. " +
                        "They are tried in order and the first that answers wins.",
                    color = TextDim, style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { scanned = null }) {
                    Text("Scan again", color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Address (only if typing a code)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Host and port") },
                placeholder = { Text("192.168.1.5:4096") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Err, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (mode == AuthKind.Password) {
                    val resolved = PairLink.resolve(hostInput, password)
                    if (resolved == null) {
                        error = "Enter a host and password"
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            Pairer.pair(resolved.code, resolved.bases.filter { it.isNotBlank() })
                        }
                        busy = false
                        when (outcome) {
                            is Pairer.Outcome.Paired -> onSaved(
                                ServerConfig(
                                    id = UUID.randomUUID().toString(),
                                    name = name.ifBlank { outcome.base.substringAfter("//") },
                                    baseUrl = outcome.base,
                                    secret = outcome.token,
                                    kind = AuthKind.Password,
                                )
                            )
                            else -> error = "Could not sign in with that password."
                        }
                    }
                } else {
                    connect()
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Bg)
            } else {
                Text("Connect")
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showScanner) {
        QrScannerSheet(
            onScanned = { raw ->
                showScanner = false
                val parsed = PairLink.fromScan(raw)
                if (parsed == null) {
                    error = "That QR was not an opencode pairing code."
                } else {
                    scanned = parsed
                    error = null
                }
            },
            onDismiss = { showScanner = false },
        )
    }
}

@Composable
private fun CommandBlock(command: String) {
    Text(
        command,
        color = TextCol,
        fontFamily = PlexMono,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}