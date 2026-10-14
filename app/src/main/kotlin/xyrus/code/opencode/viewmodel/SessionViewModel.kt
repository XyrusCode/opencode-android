package com.xyruscode.opencode.viewmodel

import com.xyruscode.opencode.bridge.SessionMessage
import com.xyruscode.opencode.bridge.SessionWebSocketClient
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.saveInstanceState
import kotlinx.coroutines.flow.*
import android.content.Context
import android.content.SharedPreferences

@saveInstanceState
class SessionViewModel : ViewModel() {
    
    // WebSocket client
    private val webSocketClient by lazy {
        SessionWebSocketClient(
            onMessage = { message ->
                when (message.type) {
                    "welcome" -> {
                        // Authentication success
                        _isConnected.value = true
                        _lastOutput = "[opencode] Connected and ready"
                    }
                    "output" -> {
                        // Real-time output from opencode
                        val current = _outputHistory.value
                        _outputHistory.value = current + listOf("[opencode] ${message.content ?: ""}")
                        // Keep history manageable - limit to 200 chars approx
                        if (_outputHistory.value.size > 15) {
                            _outputHistory.value = _outputHistory.value.drop(1)
                        }
                    }
                    "command_sent" -> {
                        // Command forwarded to opencode
                        _lastOutput = "[sent to opencode: ${message.command ?: ""}]"
                    }
                    "error" -> {
                        _lastOutput = "Error: ${message.message ?: "Unknown error"}"
                        _isConnected.value = false
                    }
                    "ping" -> {
                        // Keep-alive, respond with pong
                    }
                    else -> {
                        _lastOutput = "Received: ${message.type}"
                    }
                }
            },
            onError = { e ->
                _isConnected.value = false
                _lastOutput = "WebSocket error: ${e.message}"
            },
            onConnect = {
                // Connected - already handled in welcome message
            },
            onDisconnect = {
                _isConnected.value = false
                _lastOutput = "Disconnected from opencode"
                // Auto-reconnect logic could go here
            }
        )
    }
    
    // State flows
    private val _isConnected by MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    // Output history - rolling list of opencode output lines
    private val _outputHistory by MutableStateFlow<List<String>>(emptyList())
    val outputHistory: StateFlow<List<String>> = _outputHistory
    
    // Current last output single string (for the UI OutlinedTextField)
    private val _lastOutput by MutableStateFlow("")
    val lastOutput: String = _lastOutput.value
    
    // Current command being typed
    private val _currentCommand by MutableStateFlow("")
    val currentCommand: StateFlow<String> = _currentCommand
    
    // Callbacks (set by UI)
    var onSendCommand: (String) -> Unit = { _ -> }
    var onShowError: (String) -> Unit = { _ -> }
    var onDisconnectFromDesktop: () -> Unit = {}
    
    // Shared preferences for last IP
    private val sessionPreferences: androidx.preference.SharedPreferences
        get() = androidx.preference.SharedPreferences.getDefault(this.context!!)
    
    // Constructor - attempt reconnection to last known IP
    init {
        val lastIp = sessionPreferences.getString("last_opencode_ip", "")
        if (lastIp.isNotEmpty()) {
            // Will be connected when UI initiates
        }
    }
    
    // Functions
    
    /** Connect to opencode desktop at given IP */
    fun connectToDesktop(ip: String) {
        // Store IP for future reconnection
        sessionPreferences.edit().putString("last_opencode_ip", ip).apply()
        webSocketClient.connect(ip)
    }
    
    /** Disconnect from desktop */
    fun disconnectFromDesktop() {
        webSocketClient.disconnect()
        _isConnected.value = false
        onDisconnectFromDesktop.invoke()
    }
    
    /** Send current command to desktop */
    fun sendCommand(command: String) {
        onSendCommand.invoke(command)
        _currentCommand.value = ""
        // Add to output history as sent
        val current = _outputHistory.value
        _outputHistory.value = current + listOf("[sent] $command")
    }
    
    /** Set the current command being typed */
    fun setCurrentCommand(cmd: String) {
        _currentCommand.value = cmd
    }
    
    /** Clear the current command */
    fun clearCurrentCommand() {
        _currentCommand.value = ""
    }
    
    /** Handle incoming WebSocket messages */
    private fun handleMessage(message: SessionMessage) {
        when (message.type) {
            "welcome" -> {
                _isConnected.value = true
            }
            "output" -> {
                val current = _outputHistory.value
                _outputHistory.value = current + listOf("[opencode] ${message.content ?: ""}")
                if (_outputHistory.value.size > 15) {
                    _outputHistory.value = _outputHistory.value.drop(1)
                }
            }
            "command_sent" -> {
                // Already handled when user sent, but can add acknowledgment
            }
            "error" -> {
                _isConnected.value = false
                onShowError.invoke(message.message ?: "Unknown error from desktop")
            }
            "ping" -> {
                // Keep-alive
            }
            else -> {
                _lastOutput.value = "Received: ${message.type}"
            }
        }
    }
    
    /** Handle errors from WebSocket */
    private fun handleError(error: Throwable) {
        val errorMsg = "WebSocket error: ${error.message}"
        println(errorMsg)
        onShowError.invoke(errorMsg)
        _isConnected.value = false
    }
    
    /** Context provider */
    protected val context: Context?
        get() = this@SessionViewModel.applicationContext
}