package com.xyruscode.opencode.bridge

import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.Request
import kotlinx.WebSocket
import kotlinx.WebSocketListener
import org.json.JSONObject
import kotlinx.coroutines.delay

/**
 * WebSocket client for connecting to the opencode desktop plugin.
 * Handles authentication, command sending, and output receiving.
 */
class SessionWebSocketClient(
    private val onMessage: (SessionMessage) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onConnect: () -> Unit,
    private val onDisconnect: () -> Unit
) {
    private var socket: WebSocket? = null
    private val authToken = "opencode-2026-mobile-companion"
    
    /** Connect to the desktop plugin at the given IP and port */
    fun connect(ip: String, port: Int = 9876) {
        val url = "ws://$ip:$port"
        println("Connecting to WebSocket at $url...")
        
        try {
            socket = OkHttpClient()
                .newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket) {
                            println("WebSocket connected, sending authentication...")
                            // Authenticate with the desktop plugin
                            val authJson = """{"type":"auth","token":"opencode-2026-mobile-companion"}"""
                            webSocket.send(authJson)
                            onConnect.invoke()
                        }
                        
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val message = SessionMessage.fromJson(text)
                                onMessage(message)
                            } catch (e: Exception) {
                                // Ignore malformed messages for now
                                println("Failed to parse WebSocket message: $e")
                            }
                        }
                        
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            println("WebSocket closed: $reason (code: $code)")
                            socket = null
                            onDisconnect.invoke()
                            // Auto-reconnect after delay
                            delay(3000)
                            // Reconnect using last known IP - would need to store it
                            // For now, just log and wait
                            println("Attempting reconnection in 3 seconds...")
                        }
                        
                        override fun onFailure(webSocket: WebSocket, t: Throwable) {
                            println("WebSocket failure: $t")
                            onError.invoke(t)
                        }
                    }
                )
        } catch (e: Exception) {
            println("Failed to connect to WebSocket: $e")
            onError.invoke(e)
        }
    }
    
    /** Send a command to the desktop opencode plugin */
    fun sendCommand(command: String) {
        if (socket?.isOpen == true) {
            val json = """{"type":"command","command":"$command"}"""
            socket.send(json)
            println("Command sent: $command")
        } else {
            println("WebSocket not connected, cannot send command")
        }
    }
    
    /** Disconnect from the WebSocket server */
    fun disconnect() {
        socket?.close()
        socket = null
        onDisconnect.invoke()
    }
    
    /** Check if currently connected */
    val isConnected: Boolean
        get() = socket?.isOpen == true
}