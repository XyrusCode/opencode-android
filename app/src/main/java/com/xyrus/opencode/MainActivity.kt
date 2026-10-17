package com.xyrus.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xyrus.opencode.data.UpdateChecker
import com.xyrus.opencode.ui.ConnectScreen
import com.xyrus.opencode.ui.ConversationsDrawer
import com.xyrus.opencode.ui.SessionScreen
import com.xyrus.opencode.ui.SettingsScreen
import com.xyrus.opencode.ui.UpdatesScreen
import com.xyrus.opencode.ui.theme.OpencodeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpencodeTheme {
                val viewModel: BridgeViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                val context = LocalContext.current
                val checker = remember { UpdateChecker(context) }
                var showUpdates by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize()) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ConversationsDrawer(
                                viewModel = viewModel,
                                onPick = { session ->
                                    viewModel.attach(session)
                                    scope.launch { drawerState.close() }
                                },
                                onOpenUpdates = {
                                    scope.launch { drawerState.close() }
                                    showSettings = true
                                },
                            )
                        },
                    ) {
                        when {
                            showSettings -> SettingsScreen(
                                onBack = { showSettings = false },
                                onOpenUpdates = {
                                    showSettings = false
                                    showUpdates = true
                                },
                            )

                            showUpdates -> UpdatesScreen(
                                checker = checker,
                                onBack = { showUpdates = false },
                            )

                            state.attachedSession != null -> SessionScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )

                            else -> ConnectScreen(
                                viewModel = viewModel,
                                onOpenUpdates = { showSettings = true },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
                        }
                    }
                }
            }
        }
    }
}