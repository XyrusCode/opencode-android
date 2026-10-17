package com.xyrus.opencode.opencode

/**
 * How the phone reaches opencode.
 *
 * Two implementations exist and the UI cannot tell them apart:
 *  - [V2Client] talks to opencode's own HTTP API directly (pair or password).
 *  - the bridge WebSocket client remains available as a fallback.
 *
 * Everything is expressed as "send a frame in, get frames back" so the
 * ViewModel, transcript and composer stay identical either way. Every call
 * reports whether it was accepted, which lets callers surface a dead socket
 * instead of silently doing nothing.
 */
interface FrameSource {

    enum class State { Disconnected, Connecting, Authenticating, Ready, Failed }

    fun connect()

    fun listSessions(): Boolean

    fun createSession(title: String = ""): Boolean

    fun attach(sessionId: String): Boolean

    fun send(sessionId: String, text: String): Boolean

    fun abort(sessionId: String): Boolean

    fun selectModel(sessionId: String?, modelID: String, providerID: String): Boolean

    fun selectAgent(agent: String): Boolean

    /** Remembers a project directory, applied to the next created session. */
    fun pickProject(directory: String): Boolean

    fun renameSession(sessionId: String, title: String): Boolean

    fun deleteSession(sessionId: String): Boolean

    /** one of "once", "always", "reject". Replies go to whichever session is attached. */
    fun replyPermission(requestID: String, reply: String, message: String? = null): Boolean

    /** One list of selected labels per question, in order. */
    fun replyQuestion(requestID: String, answers: List<List<String>>): Boolean

    fun disconnect()

    fun shutdown()
}