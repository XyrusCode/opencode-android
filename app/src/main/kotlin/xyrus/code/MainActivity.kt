package com.xyruscode.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.setContentView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.material3.Column
import androidx.compose.material3.Button
import androidx.compose.material3.outlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import org.json.JSONObject

// Main activity - hosts the opencode companion UI
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpencodeCompanionTheme {
                // A Surface container wraping user's UI enables edge-to-edge
                // layout direction-insensitive behavior, and some platforms
                // require this to make edge-to-edge UI work correctly.
                Surface(
                    modifier = fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Start with pairing screen, then transition to session screen
                    // after successful connection
                    val viewModel = rememberViewModel()
                    // Track connection state across composition
                    var by remember { mutableStateOf(false) }
                    
                    // Simplified flow: start with pairing, then show session
                    // In a full implementation, this would use navigation between
                    // composables based on connection state
                    Column(
                        modifier = fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pairing section
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Desktop IP Address") },
                            modifier = fillMaxWidth().padding(4.dp)
                        )
                        OutlinedTextField(
                            value = "9876",
                            onValueChange = {},
                            label = { Text("Port") },
                            modifier = fillMaxWidth().padding(4.dp)
                            .keyboardOptions(KeyboardOptions(inputType = InputType.number))
                        )
                        OutlinedTextField(
                            value = "opencode-2026-mobile-companion",
                            onValueChange = {},
                            label = { Text("Auth Token") },
                            modifier = fillMaxWidth().padding(4.dp)
                            .isEnabled(false)
                        )
                        Button(onClick = {
                            // TODO: Connect to desktop plugin
                        }) {
                            Text("Connect to opencode Desktop")
                        }
                        Spacer(modifier = height(32.dp))
                        Text(
                            text = "Auth token: opencode-2026-mobile-companion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}