package com.xyrus.opencode.bridge

import android.util.Log
import com.xyrus.opencode.opencode.FrameSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for the desktop bridge.
 *
 * Kept as a fallback transport for setups where talking to opencode directly is
 * not wanted. It emits the same frames as the direct v2 client, so nothing
 * above it needs to know which one is in use.
 *
 * Owns exactly one socket and one OkHttpClient. Callbacks arrive on OkHttp's
 * dispatcher thread, so consumers are expected to hop to their own scope before
 * touching state.
 */
class BridgeClient(
    private val token: String,
    private val onMessage: (SessionMessage) -> Unit,
    private val onStateChange: (FrameSource.State) -> Unit,
) : FrameSource {

    private var host: String = ""
    private var port: Int = 9876

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // the socket is long lived
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    /** Point the client at a bridge and connect. */
    fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        connect()
    }

    override fun connect() {
        if (socket != null || host.isEmpty()) return
        onStateChange(FrameSource.State.Connecting)

        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStateChange(FrameSource.State.Authenticating)
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
                onStateChange(FrameSource.State.Failed)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onStateChange(FrameSource.State.Disconnected)
            }
        })
    }

    private fun send(frame: JSONObject): Boolean {
        val current = socket ?: return false
        return current.send(frame.toString())
    }

    override fun listSessions() = send(JSONObject().put("type", "list_sessions"))

    override fun createSession(title: String) =
        send(JSONObject().put("type", "create_session").put("title", title))

    override fun attach(sessionId: String) =
        send(JSONObject().put("type", "attach").put("sessionID", sessionId))

    override fun send(sessionId: String, text: String) =
        send(
            JSONObject()
                .put("type", "prompt")
                .put("sessionID", sessionId)
                .put("text", text),
        )

    // --- Model / agent / project selection -------------------------------
    // The bridge remembers these per-socket and pops them into the NEXT prompt
    // (per-message override is the only setter opencode supports), so the
    // ViewModel re-sends the active selection before every message.

    override fun selectModel(sessionId: String?, modelID: String, providerID: String) =
        send(
            JSONObject().put("type", "select_model").apply {
                if (sessionId != null) put("sessionID", sessionId)
                put("modelID", modelID)
                put("providerID", providerID)
            },
        )

    override fun selectAgent(agent: String) =
        send(JSONObject().put("type", "select_agent").put("agent", agent))

    /** Remembered until the next create_session, which is where it applies. */
    override fun pickProject(directory: String) =
        send(JSONObject().put("type", "pick_project").put("worktree", directory))

    // --- Interactive prompt replies ----------------------------------------

    /** reply is one of "once", "always", "reject". */
    override fun replyPermission(requestID: String, reply: String, message: String?): Boolean =
        send(
            JSONObject().put("type", "reply_permission").apply {
                put("requestID", requestID)
                put("reply", reply)
                if (message != null) put("message", message)
            },
        )

    /** answers is one list of selected labels per question, in order. */
    override fun replyQuestion(requestID: String, answers: List<List<String>>): Boolean {
        val array = JSONArray()
        answers.forEach { selected -> array.put(JSONArray(selected)) }
        return send(
            JSONObject().put("type", "reply_question")
                .put("requestID", requestID)
                .put("answers", array),
        )
    }

    override fun abort(sessionId: String) =
        send(JSONObject().put("type", "abort").put("sessionID", sessionId))

    override fun renameSession(sessionId: String, title: String) =
        send(
            JSONObject().put("type", "rename_session")
                .put("sessionID", sessionId)
                .put("title", title),
        )

    /** Permanent: opencode drops the session and its messages. */
    override fun deleteSession(sessionId: String) =
        send(JSONObject().put("type", "delete_session").put("sessionID", sessionId))

    override fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        onStateChange(FrameSource.State.Disconnected)
    }

    /** Releases the connection pool. Call when the client is no longer needed. */
    override fun shutdown() {
        disconnect()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private companion object {
        const val TAG = "BridgeClient"
    }
}
