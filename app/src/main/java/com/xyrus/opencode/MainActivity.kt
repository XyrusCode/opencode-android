package com.xyrus.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xyrus.opencode.data.UpdateChecker
import com.xyrus.opencode.ui.PairingScreen
import com.xyrus.opencode.ui.SessionScreen
import com.xyrus.opencode.ui.UpdatesScreen
import com.xyrus.opencode.ui.theme.OpencodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OpencodeTheme {
                val viewModel: BridgeViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                val context = LocalContext.current
                val checker = remember { UpdateChecker(context) }
                var showUpdates by rememberSaveable { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        showUpdates -> UpdatesScreen(
                            checker = checker,
                            onBack = { showUpdates = false },
                        )

                        state.attachedSession != null -> SessionScreen(viewModel)

                        else -> PairingScreen(
                            viewModel = viewModel,
                            onOpenUpdates = { showUpdates = true },
                        )
                    }
                }
            }
        }
    }
}