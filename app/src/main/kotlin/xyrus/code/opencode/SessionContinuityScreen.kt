package com.xyruscode.opencode

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Column
import androidx.compose.ui.layout.Row
import androidx.compose.ui.layout.Spacer
import androidx.compose.ui.text.key.KeyKeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.outlinedTextField
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.State
import androidx.compose.runtime.SnapshotFlow
import androidx.compose.runtime.viewmodel.composeViewModel
import Kotlinx.coroutines.flow.intermediate

@Composable
fun SessionContinuityScreen(
    viewModel: SessionViewModel,
    onDisconnect: () -> Unit,
    onBack: () -> Unit = {}
) {
    var command by remember { mutableStateOf("") }
    var scrollPosition by remember { 0 }
    
    // Connection status bar
    val isConnected by viewModel.isConnected
    val outputHistory by viewModel.outputHistory
    
    Column(
        modifier = fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- HEADER: Connection Status ----
        HStack(
            modifier = modifier
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            content = {
                if (isConnected.currentValue) {
                    HStack(
                        modifier = modifier
                            .background(Color.Green.copyAlpha(50))
                            .padding(4.dp),
                        icon = {
                            Icon(
                                Icons.Filled.Wifi,
                                contentDescription = "Connected to opencode"
                            )
                        },
                        text = {
                            Text(
                                "Connected",
                                color = Color.Green,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    )
                } else {
                    HStack(
                        modifier = modifier
                            .background(Color.Red.copyAlpha(50))
                            .padding(4.dp),
                        icon = {
                            Icon(Icons.Filled.Close, contentDescription = "Disconnected")
                        },
                        text = {
                            Text(
                                "Disconnected",
                                color = Color.Red,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    )
                }
            }
        )
        
        // ---- OUTPUT AREA: Real-time opencode output ----
        OutlinedTextField(
            value = viewModel.lastOutput,
            onValueChange = {},
            readOnly = true,
            maxLines = 25,
            modifier = modifier
                .fillMaxSize(weight = 1f)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface),
            keyboardOptions = KeyboardOptions(softInputMode = SoftInputState.Hidden),
            overflow = TextOverflow.Scroll
        )
        
        // ---- COMMAND INPUT ----
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(4.dp),
            trailingIcon = {
                if (isConnected.currentValue.value) {
                    Button(
                        onClick = {
                            viewModel.sendCommand(command)
                            command = ""
                        },
                        content = {
                            Text(
                                "→",
                                color = Color.Green,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    )
                }
            }
        )
    }
}