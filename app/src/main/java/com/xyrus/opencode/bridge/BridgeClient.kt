package com.xyrus.opencode.bridge

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for the desktop bridge.
 *
 * Owns exactly one socket and one OkHttpClient. Callbacks arrive on OkHttp's
 * dispatcher thread, so consumers are expected to hop to their own scope before
 * touching state.
 */
class BridgeClient(
    private val token: String,
    private val onMessage: (SessionMessage) -> Unit,
    private val onStateChange: (State) -> Unit,
) {
    enum class State { Disconnected, Connecting, Authenticating, Ready, Failed }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // the socket is long lived
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    /** Open the socket and authenticate. Safe to call when already open. */
    fun connect(host: String, port: Int) {
        if (socket != null) return
        onStateChange(State.Connecting)

        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStateChange(State.Authenticating)
                val auth = JSONObject().put("type", "auth").put("token", token)
                webSocket.send(auth.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = try {
                    SessionMessage.parse(text)
                } catch (e: Exception) {
                    Log.w(TAG, "dropping unparseable frame: ${text.take(200)}", e)
                    return
                }
                if (frame.isError) {
                    Log.w(TAG, "bridge error: ${frame.message}")
                }
                onMessage(frame)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failed", t)
                socket = null
                onStateChange(State.Failed)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onStateChange(State.Disconnected)
            }
        })
    }

    private fun send(frame: JSONObject): Boolean {
        val current = socket ?: return false
        return current.send(frame.toString())
    }

    fun listSessions() = send(JSONObject().put("type", "list_sessions"))

    fun createSession(title: String = "") =
        send(JSONObject().put("type", "create_session").put("title", title))

    fun attach(sessionId: String) =
        send(JSONObject().put("type", "attach").put("sessionID", sessionId))

    fun prompt(sessionId: String, text: String) =
        send(
            JSONObject()
                .put("type", "prompt")
                .put("sessionID", sessionId)
                .put("text", text),
        )

    fun abort(sessionId: String) =
        send(JSONObject().put("type", "abort").put("sessionID", sessionId))

    fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        onStateChange(State.Disconnected)
    }

    /** Releases the connection pool. Call when the client is no longer needed. */
    fun shutdown() {
        disconnect()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private companion object {
        const val TAG = "BridgeClient"
    }
}
