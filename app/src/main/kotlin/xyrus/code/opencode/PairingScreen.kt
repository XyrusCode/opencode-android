package com.xyruscode.opencode

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Column
import androidx.compose.ui.layout.Row
import androidx.compose.ui.layout.Spacer
import androidx.compose.ui.text.key.KeyKeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.outlinedTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.unit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onConnect: (ip: String, port: Int) -> Unit,
    onError: (message: String) -> Unit,
    onBack: () -> Unit = {}
) {
    var ip by remember { mutableStateOf("192.168.1.1") }
    var port by remember { mutableStateOf(9876) }
    var token by remember { mutableStateOf("opencode-2026-mobile-companion") }
    var error by remember { mutableStateOf("") }
    
    Column(
        modifier = fillMaxSize()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "opencode Companion",
            style = MaterialTheme.typography.headLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = height(16.dp))
        
        // IP Address
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Desktop IP Address") },
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
            keyboardOptions = KeyboardOptions(
                keyKeyboardType = KeyKeyboardType.ipAddress
            )
        )
        
        // Port
        OutlinedTextField(
            value = port.toString(),
            onValueChange = { port = it.toInt() },
            label = { Text("Port") },
            modifier = modifier
                .fillMaxWidth()
                .keyboardOptions(KeyboardOptions(keyKeyboardType = KeyKeyboardType.numberPassword))
                .padding(bottom = 8.dp)
        )
        
        // Auth Token (display only, read-only)
        OutlinedTextField(
            value = token,
            onValueChange = {},
            label = { Text("Auth Token") },
            modifier = modifier
                .fillMaxWidth()
                .isEnabled(false)  // Read-only
                .padding(bottom = 8.dp)
        }
        
        // Error message
        if (error.isNotEmpty()) {
            OutlinedTextField(
                value = error,
                onValueChange = {},
                readOnly = true,
                modifier = modifier
                    .fillMaxWidth()
                    .background(Color.Red.copyAlpha(50))
                    .padding(bottom = 8.dp)
            )
        }
        
        // Connect button
        Button(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            onClick = {
                // Validation
                if (ip.isNotEmpty() && port > 0) {
                    // Store last IP for reconnection
                    viewModel.storeLastIp(ip)
                    onConnect(ip, port)
                } else {
                    error = "Please enter IP address and port"
                }
            },
            enabled = ip.isNotEmpty() && port > 0,
            content = {
                Text("Connect", style = ButtonStyle.Large)
            }
        )
        
        // Instructions
        Spacer(modifier = height(24.dp))
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = modifier .fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                text = "Auth token: opencode-2026-mobile-companion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Make sure desktop plugin is running first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}