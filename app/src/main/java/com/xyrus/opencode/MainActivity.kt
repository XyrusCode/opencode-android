package com.xyrus.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xyrus.opencode.ui.PairingScreen
import com.xyrus.opencode.ui.SessionScreen
import com.xyrus.opencode.ui.theme.OpencodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OpencodeTheme {
                val viewModel: BridgeViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (state.attachedSession != null) {
                        SessionScreen(viewModel)
                    } else {
                        PairingScreen(viewModel)
                    }
                }
            }
        }
    }
}
