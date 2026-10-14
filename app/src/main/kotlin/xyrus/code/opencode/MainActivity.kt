package com.xyruscode.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.setContentView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.State
import androidx.compose.material3.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
                    OpencodeStartupScreen()
                }
            }
        }
    }
}

// Theme for the opencode companion
@Composable
fun OpencodeCompanionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) {
            lightColorScheme()
        } else {
            darkColorScheme()
        }
    ) {
        content()
    }
}

private val isDarkTheme: Boolean
    get() = true // Simplified - could check system theme

// Welcome/startup screen shown when app launches
@Composable
fun OpencodeStartupScreen() {
    Column(
        modifier = fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "opencode Companion",
            style = MaterialTheme.typography.headLarge
        )
        Spacer(modifier = height(16.dp))
        Text(
            text = "Continue your opencode sessions from desktop to mobile",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = height(32.dp))
        
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            hint = { Text("Enter desktop IP address to connect") },
            modifier = fillMaxWidth().padding(4.dp)
        )
        
        Spacer(modifier = height(8.dp))
        
        Button(onClick = { /* TODO: Show pairing screen */ }) {
            Text("Connect to opencode Desktop")
        }
        
        Spacer(modifier = height(8.dp))
        
        Text(
            text = "Auth token: opencode-2026-mobile-companion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}