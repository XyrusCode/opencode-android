package com.xyruscode.opencode.viewmodel

import com.xyruscode.opencode.bridge.SessionWebSocketClient
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.saveInstanceState
import kotlinx.coroutines.flow.*
import android.content.Context
import android.content.SharedPreferences

@saveInstanceState
class PairingViewModel : ViewModel() {
    
    // WebSocket client (created lazily)
    private val webSocketClient by lazy {
        SessionWebSocketClient(
            onMessage = { message ->
                when (message.type) {
                    "welcome" -> {
                        // Authentication success - signal UI to switch screens
                    }
                    "error" -> {
                        // Show error from desktop
                    }
                }
            },
            onError = { e ->
                // Connection error - show in UI
            },
            onConnect = {
                // Connected - notify UI
            },
            onDisconnect = {
                // Disconnected - show error or retry option
            }
        )
    }
    
    // State flows
    private val _ip by MutableStateFlow("")
    val ip: StateFlow<String> = _ip
    
    private val _port by MutableStateFlow(9876)
    val port: StateFlow<Int> = _port
    
    private val _token by MutableStateFlow("opencode-2026-mobile-companion")
    val token: StateFlow<String> = _token
    
    // Last stored IP (for reconnection)
    @SharedPref("last_opencode_ip")
    var String lastStoredIp: String = ""
    
    // Callbacks (set by UI)
    var onConnectClicked: (String, Int) -> Unit = { _ip, _port -> }
    var onError: (String) -> Unit = { _ -> }
    
    // Store last IP for reconnection
    fun storeLastIp(ip: String) {
        _ip.value = ip
        lastStoredIp = ip
    }
    
    // Connect action
    fun connect() {
        val currentIp = _ip.value
        val currentPort = _port.value
        
        if (currentIp.isNotEmpty() && currentPort > 0) {
            // WebSocket client will connect internally
            onError.invoke("Connecting to $currentIp:$currentPort")
        } else {
            onError.invoke("Please enter IP and port first")
        }
    }
    
    // Validation
    fun validate(): Boolean {
        return _ip.value.isNotEmpty() && _port.value > 0
    }
}