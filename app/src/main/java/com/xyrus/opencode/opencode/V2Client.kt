package com.xyrus.opencode.opencode

import android.util.Log
import com.xyrus.opencode.bridge.Session
import com.xyrus.opencode.bridge.SessionMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Talks straight to an opencode v2 server over HTTP.
 *
 * Why this exists: the desktop app writes sessions to the `session_v2` table
 * while the v1 server the bridge spoke reads `session`, so everything started
 * in the desktop was invisible on the phone. v2 is the same store the desktop
 * reads, which makes those sessions show up.
 *
 * Auth is HTTP Basic with the username "opencode" and either a pairing token or
 * a server password as the password; a bearer token is rejected by v2.
 */
class V2Client(
    private val server: ServerConfig,
    private val onMessage: (SessionMessage) -> Unit,
    private val onStateChange: (FrameSource.State) -> Unit,
    private val catalogCache: File? = null,
) : FrameSource {

    override fun connect() {
        if (state != FrameSource.State.Disconnected) return
        onStateChange(FrameSource.State.Connecting)
        worker.execute {
            try {
                // Cheap round trip that proves the credential before we commit.
                get("/api/session?limit=1")
                onStateChange(FrameSource.State.Ready)
                listSessions()
                // Warm the pickers now so opening a session does not wait on them.
                pushCatalogs()
                startEventStream()
                startPromptPolling()
            } catch (e: Exception) {
                // A client that was deliberately torn down mid-connect is not a
                // failure worth showing: switching servers interrupts the old
                // one's in-flight request, and that is not an error the user
                // did anything about.
                if (state == FrameSource.State.Disconnected) {
                    Log.d(TAG, "connect abandoned (client closed)")
                    return@execute
                }
                Log.w(TAG, "connect failed", e)
                onStateChange(FrameSource.State.Failed)
            }
        }
    }

    // --- HTTP ------------------------------------------------------------

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // The event stream is long lived and must not be torn down.
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val streamHttp: OkHttpClient by lazy {
        OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "v2-source").apply { isDaemon = true }
    }

    /**
     * Catalogs are large and slow to fetch (1105 models, 108 projects) so they
     * get their own thread. Sharing the worker meant opening a session waited
     * behind them, and refetching on every attach made it worse.
     */
    private val catalogWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "v2-catalogs").apply { isDaemon = true }
    }

    private val catalogsPushed = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var state: FrameSource.State = FrameSource.State.Disconnected

    @Volatile
    private var attachedSession: String? = null

    /** Picked model/agent, re-applied before each prompt the way v1 required. */
    @Volatile
    private var pendingModel: Pair<String, String>? = null

    @Volatile
    private var pendingAgent: String? = null

    @Volatile
    private var pendingDirectory: String? = null

    private fun authHeader() =
        okhttp3.Credentials.basic(server.username, server.secret)

    private fun request(path: String): Request.Builder =
        Request.Builder()
            .url(server.baseUrl.trimEnd('/') + path)
            .header("Authorization", authHeader())

    /**
 * Not every v2 endpoint answers with the same envelope. Most wrap their
 * payload as {"data": ...}, but /api/project answers with a bare array, which
 * JSONObject() refuses to parse. Normalising an array into {"data": [...]}
 * means callers can always read optJSONArray("data").
 */
private fun parseBody(body: String): JSONObject {
    val trimmed = body.trim()
    return if (trimmed.startsWith("[")) {
        JSONObject().put("data", JSONArray(trimmed))
    } else {
        JSONObject(trimmed)
    }
}

private fun get(path: String): JSONObject? {
    http.newCall(request(path).get().build()).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, body.take(200))
        return if (body.isBlank()) null else unwrap(parseBody(body))
    }
}

private fun post(path: String, body: JSONObject? = null): JSONObject? {
    val payload = (body ?: JSONObject()).toString().toRequestBody(JSON)
    http.newCall(request(path).post(payload).build()).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, text.take(200))
        return if (text.isBlank()) null else unwrap(parseBody(text))
    }
}

    private fun patch(path: String, body: JSONObject): JSONObject? =
        http.newCall(request(path).patch(body.toString().toRequestBody(JSON)).build())
            .execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, text.take(200))
                if (text.isBlank()) null else unwrap(JSONObject(text))
            }

    private fun delete(path: String) {
        http.newCall(request(path).delete().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw OpencodeHttpException(resp.code, resp.body?.string().orEmpty().take(200))
            }
        }
    }

    /** v2 wraps most responses in {"data": ...}; unwrap so callers see the payload. */
    private fun unwrap(json: JSONObject): JSONObject =
        json.optJSONObject("data") ?: json

    /** Run a request, turning any failure into an error frame rather than a crash. */
private inline fun attempt(body: () -> Unit): Boolean = try {
        body()
        true
    } catch (e: Exception) {
        reportFailure(e)
        false
    }

    /**
 * Hand work to the background thread and return immediately.
 *
 * Callers include the main thread: `onConnectionChange` fires `loadSessions`
 * from the ViewModel scope, and doing the HTTP inline there throws
 * NetworkOnMainThreadException. Like the bridge client, these are fire-and-
 * forget; results arrive as frames.
 */
private inline fun offThread(crossinline block: () -> Unit): Boolean {
    val executor = worker
    return if (executor.isShutdown) {
        false
    } else {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                reportFailure(e)
            }
        }
        true
    }
}

private fun reportFailure(e: Exception) {
        // Same reasoning as connect(): an interrupt means this client was
        // replaced, not that the server is wrong.
        if (e is java.io.InterruptedIOException && state == FrameSource.State.Disconnected) {
            Log.d(TAG, "request abandoned (client closed)")
            return
        }
        Log.w(TAG, "request failed", e)
    onMessage(
        SessionMessage(
            type = "error",
            message = when {
                (e as? OpencodeHttpException)?.code == 401 ->
                    "That server rejected the credential. Pair again, or check the password."
                else -> e.message ?: "opencode request failed"
            },
        )
    )
}

// --- FrameSource -----------------------------------------------------

    override fun listSessions(): Boolean = offThread {
        val payload = get("/api/session?limit=$SESSION_PAGE") ?: return@offThread
        val arr = payload.optJSONArray("data") ?: JSONArray()
        val sessions = (0 until arr.length()).map { parseSession(arr.getJSONObject(it)) }
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "sessions")
                    .put("kind", "list")
                    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
                    .toString()
            )
        )
    }

    override fun createSession(title: String): Boolean = offThread {
        val body = JSONObject().put("title", title.ifEmpty { "From phone" })
        pendingDirectory?.let { body.put("location", JSONObject().put("directory", it)) }
        val payload = post("/api/session", body) ?: return@offThread
        val session = parseSession(payload)
        attachedSession = session.id
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "session")
                    .put("kind", "created")
                    .put("session", session.toJson())
                    .toString()
            )
        )
        pushCatalogs()
        attach(session.id)
    }

    override fun attach(sessionId: String): Boolean = offThread {
        attachedSession = sessionId
        // Newest first. This session runs to thousands of messages, so asking for the
        // first page ascending loads the beginning of the conversation and never
        // reaches what just happened.
        val payload = get("/api/session/$sessionId/message?limit=$MESSAGE_PAGE&order=desc")
        val arr = payload?.optJSONArray("data") ?: JSONArray()
        val messages = JSONArray()
        var skipped = 0
        // Reverse into chronological order so the transcript reads top to bottom.
        for (i in arr.length() - 1 downTo 0) {
            val mapped = historyMessage(arr.getJSONObject(i))
            if (mapped != null) messages.put(mapped) else skipped++
        }
        Log.i(
            TAG,
            "history for $sessionId: ${arr.length()} returned, ${messages.length()} rendered, $skipped skipped",
        )
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "history")
                    .put("sessionID", sessionId)
                    .put("messages", messages)
                    .toString()
            )
        )
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "session")
                    .put("kind", "attached")
                    .put("sessionID", sessionId)
                    .toString()
            )
        )
        pushCatalogs()
    }

    override fun send(sessionId: String, text: String): Boolean = offThread {
        // v2 has no per-message model field, so the session is switched first.
        pendingModel?.let { (modelID, providerID) -> setModelOnServer(sessionId, modelID, providerID) }
        pendingAgent?.let { setAgentOnServer(sessionId, it) }
        post("/api/session/$sessionId/prompt", JSONObject().put("text", text))
    }

    override fun abort(sessionId: String): Boolean = offThread { post("/api/session/$sessionId/interrupt") }

    override fun selectModel(sessionId: String?, modelID: String, providerID: String): Boolean {
        pendingModel = modelID to providerID
        val target = sessionId ?: attachedSession ?: return true
        return attempt { setModelOnServer(target, modelID, providerID) }
    }

    override fun selectAgent(agent: String): Boolean {
        pendingAgent = agent
        val target = attachedSession ?: return true
        return attempt { setAgentOnServer(target, agent) }
    }

    override fun pickProject(directory: String): Boolean {
        pendingDirectory = directory
        return true
    }

    override fun renameSession(sessionId: String, title: String): Boolean = offThread {
        val payload = patch("/api/session/$sessionId", JSONObject().put("title", title))
        val updated = payload?.let { parseSession(it) } ?: return@offThread
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "session")
                    .put("kind", "renamed")
                    .put("session", updated.toJson())
                    .toString()
            )
        )
    }

    override fun deleteSession(sessionId: String): Boolean = offThread {
        delete("/api/session/$sessionId")
        if (attachedSession == sessionId) attachedSession = null
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "session")
                    .put("kind", "deleted")
                    .put("sessionID", sessionId)
                    .toString()
            )
        )
    }

    override fun replyPermission(requestID: String, reply: String, message: String?): Boolean = offThread {
        val sessionId = attachedSession ?: return@offThread
        val body = JSONObject().put("decision", reply)
        message?.let { body.put("message", it) }
        post("/api/session/$sessionId/permission/$requestID/reply", body)
    }

    override fun replyQuestion(requestID: String, answers: List<List<String>>): Boolean = offThread {
        val sessionId = attachedSession ?: return@offThread
        // v2 replaced questions with forms; labels are replied as an answer list.
        val body = JSONObject()
            .put("answers", JSONArray().apply { answers.forEach { put(JSONArray(it)) } })
        post("/api/session/$sessionId/form/$requestID/reply", body)
    }

    override fun disconnect() {
        attachedSession = null
        state = FrameSource.State.Disconnected
        onStateChange(FrameSource.State.Disconnected)
    }

    override fun shutdown() {
        disconnect()
        worker.shutdownNow()
        catalogWorker.shutdownNow()
    }

    // --- streaming -------------------------------------------------------

    private fun startEventStream() {
        thread(isDaemon = true, name = "v2-events") {
            var backoff = 1.0
            while (state == FrameSource.State.Ready) {
                try {
                    val req = request("/api/event")
                        .header("Accept", "text/event-stream")
                        .build()
                    streamHttp.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, "event stream")
                        backoff = 1.0
                        val source = resp.body?.source() ?: return@use
                        while (state == FrameSource.State.Ready) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) continue
                            val event = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            V2EventMapper.map(event)?.let { chunk ->
                                V2EventMapper.toFrame(chunk)?.let(onMessage)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (state != FrameSource.State.Ready) return@thread
                    Log.w(TAG, "event stream dropped, retrying in ${backoff}s", e)
                    Thread.sleep((backoff * 1000).toLong())
                    backoff = (backoff * 2).coerceAtMost(30.0)
                }
            }
        }
    }

    /**
     * v2's global permission and form endpoints are scoped to a `location` and
     * silently default to the server's working directory, which would hide
     * prompts raised in another project. Polling the attached session's own
     * endpoints is always correct, so that is what we do.
     */
    private fun startPromptPolling() {
        thread(isDaemon = true, name = "v2-prompts") {
            val seenPerms = mutableSetOf<String>()
            val seenForms = mutableSetOf<String>()
            while (state == FrameSource.State.Ready) {
                try {
                    val sessionId = attachedSession
                    if (sessionId != null) {
                        val perms = get("/api/session/$sessionId/permission")
                            ?.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until perms.length()) {
                            val p = perms.getJSONObject(i)
                            val id = p.optString("id")
                            if (id.isNotEmpty() && seenPerms.add(id)) {
                                // The phone reads tool ids off a nested "tool"
                                // object, so emit that shape rather than v2's flat one.
                                onMessage(
                                    SessionMessage.parse(
                                        JSONObject()
                                            .put("type", "permission_prompt")
                                            .put("requestID", id)
                                            .put("permission", p.optString("action"))
                                            // v2 explains itself here. Without it the
                                            // phone can only say "run a tool", which
                                            // is not something you can judge.
                                            .put("message", p.optString("message"))
                                            .put(
                                                "patterns",
                                                JSONArray().apply {
                                                    p.optJSONArray("resources")?.let { src ->
                                                        for (r in 0 until src.length()) put(src.opt(r))
                                                    }
                                                    p.optJSONArray("save")?.let { src ->
                                                        for (r in 0 until src.length()) put(src.opt(r))
                                                    }
                                                }
                                            )
                                            .put("tool", JSONObject().put("callID", id))
                                            .toString()
                                    )
                                )
                            }
                        }

                        val forms = get("/api/session/$sessionId/form")?.optJSONArray("data")
                            ?: JSONArray()
                        for (i in 0 until forms.length()) {
                            val f = forms.getJSONObject(i)
                            val id = f.optString("id")
                            if (id.isNotEmpty() && seenForms.add(id)) {
                                onMessage(
                                    SessionMessage.parse(
                                        JSONObject()
                                            .put("type", "question_prompt")
                                            .put("requestID", id)
                                            .put(
                                                "questions",
                                                JSONArray().put(
                                                    JSONObject()
                                                        .put("header", f.optString("title"))
                                                        .put("question", f.optString("description"))
                                                        .put("multiple", true)
                                                        .put("options", formOptions(f.optJSONArray("options")))
                                                )
                                            )
                                            .toString()
                                    )
                                )
                            }
                        }
                    }
                    Thread.sleep(2000)
                } catch (e: Exception) {
                    if (state != FrameSource.State.Ready) return@thread
                    Thread.sleep(4000)
                }
            }
        }
    }

    private fun setModelOnServer(sessionId: String, modelID: String, providerID: String) {
        post(
            "/api/session/$sessionId/model",
            JSONObject().put(
                "model",
                JSONObject().put("id", modelID).put("providerID", providerID)
            )
        )
    }

    private fun setAgentOnServer(sessionId: String, agent: String) {
        post("/api/session/$sessionId/agent", JSONObject().put("agent", agent))
    }

    /**
     * Push the model and agent pickers once per connection, off the worker
     * thread.
     *
     * `/api/model` is 892 KB and answers with 1107 models, identically on every
     * connect. Over a tunnel that is seconds the picker never needed to block on.
     * So: serve the last good copy from disk immediately, then refresh in the
     * background. The pickers are usable on the first frame and correct by the
     * time anyone opens them.
     */
    private fun pushCatalogs() {
        if (!catalogsPushed.compareAndSet(false, true)) return
        catalogWorker.execute {
            try {
                CatalogCache.read(catalogCache)?.let { emitCatalogs(it) }
                fetchCatalogs()
            } catch (e: Exception) {
                // Let a later attempt retry rather than leaving the pickers empty.
                catalogsPushed.set(false)
                reportFailure(e)
            }
        }
    }

    private fun emitCatalogs(catalogs: CatalogCache.Catalogs) {
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "providers")
                    .put("providers", catalogs.providers)
                    .toString()
            )
        )
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "agents")
                    .put("agents", catalogs.agents)
                    .toString()
            )
        )
    }

    private fun fetchCatalogs() {
        val models = get("/api/model")?.optJSONArray("data") ?: JSONArray()
        val providers = JSONArray()
        val byProvider = linkedMapOf<String, JSONArray>()
        for (i in 0 until models.length()) {
            val m = models.getJSONObject(i)
            val pid = m.optString("providerID")
            if (pid.isEmpty()) continue
            byProvider.getOrPut(pid) { JSONArray() }.put(
                JSONObject()
                    .put("id", m.optString("modelID"))
                    .put("name", m.optString("name"))
                    .put("providerID", pid)
                    .put("cost", m.optJSONArray("cost")?.optJSONObject(0) ?: JSONObject())
            )
        }
        for ((pid, modelsForProvider) in byProvider) {
            providers.put(
                JSONObject()
                    .put("id", pid)
                    .put("name", pid)
                    .put("models", modelsForProvider)
            )
        }

        val agents = get("/api/agent")?.optJSONArray("data") ?: JSONArray()
        val agentList = JSONArray().apply {
            for (i in 0 until agents.length()) {
                val a = agents.getJSONObject(i)
                put(
                    JSONObject()
                        .put("name", a.optString("id"))
                        .put("description", a.optString("description"))
                )
            }
        }

        val catalogs = CatalogCache.Catalogs(providers, agentList)
        CatalogCache.write(catalogCache, catalogs)
        emitCatalogs(catalogs)
    }

    // --- shape translation ----------------------------------------------

    /** v2 nests the directory under `location` and the times under `time`. */
    private fun parseSession(json: JSONObject): Session {
        val model = json.optJSONObject("model")
        return Session(
            id = json.optString("id"),
            title = json.optString("title").ifEmpty { "(untitled)" },
            directory = json.optJSONObject("location")?.optString("directory"),
            model = model?.optString("id"),
            provider = model?.optString("providerID"),
            agent = json.optString("agent"),
            updated = json.optJSONObject("time")?.optLong("updated", 0L)?.takeIf { it > 0L },
        )
    }

    /** v2 messages carry `type` + `content[]`; the transcript wants role + parts. */
    private fun historyMessage(message: JSONObject): JSONObject? {
        val type = message.optString("type")
        return when (type) {
            "user" -> {
                val text = message.optString("text")
                if (text.isBlank()) null else JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            }

            "assistant" -> {
                val content = message.optJSONArray("content") ?: JSONArray()
                if (content.length() == 0) null else JSONObject()
                    .put("role", "assistant")
                    .put("parts", JSONArray().apply {
                        for (i in 0 until content.length()) {
                            val c = content.getJSONObject(i)
                            when (c.optString("type")) {
                                "text" -> put(JSONObject().put("type", "text").put("text", c.optString("text")))
                                // reasoning/text carry no id of their own. Borrowing the
                                // message id keeps the card key stable across rebuilds, so
                                // an expanded card stays expanded.
                                "reasoning" -> put(
                                    JSONObject()
                                        .put("type", "reasoning")
                                        .put("id", "${message.optString("id")}-r$i")
                                        .put("text", c.optString("text"))
                                )
                                "tool" -> put(toolPart(c))
                                // Attachments. v2 models them as Tool.FileContent
                                // ({type, uri, mime, name}) and the prompt variants
                                // carry base64 `data`. These used to fall into a
                                // silent `else -> Unit`, which is why an image sent
                                // from the desktop simply did not exist on the phone.
                                "file", "attachment", "image" -> put(filePart(c, message.optString("id"), i))
                                else -> {
                                    // Never swallow a type we do not know. A model
                                    // can add content types ahead of this client,
                                    // and a blank card is indistinguishable from a
                                    // bug to whoever is using the phone.
                                    Log.w(TAG, "unrendered content type '${c.optString("type")}' keys=${c.keys()}")
                                }
                            }
                        }
                    })
            }

            // Control messages (idle, model-switched, ...) are not transcript.
            else -> null
        }
    }

    /**
 * Flatten a v2 tool call into the flat part shape the transcript renderer
 * expects.
 *
 * v2 nests everything the card needs one level down inside `state`
 * (status/input/output), and keys the call `id` at the top level. Passing the
 * nested object straight through produced cards with no status, no input and no
 * output, so tool cards had nothing to expand.
 */
private fun toolPart(call: JSONObject): JSONObject {
    val state = call.optJSONObject("state") ?: JSONObject()
    val output = state.optString("output")
        .takeIf { it.isNotBlank() }
        ?: state.optJSONObject("metadata")?.optString("output").orEmpty()
        .takeIf { it.isNotBlank() }
        ?: state.optString("error")
    return JSONObject().apply {
        put("type", "tool")
        put("id", call.optString("id"))
        put("tool", call.optString("name"))
        put("state", state.optString("status"))
        state.optJSONObject("input")?.let { put("input", it) }
        if (output.isNotBlank()) put("output", output)
    }
}

/**
 * Flatten an attachment into the flat part shape.
 *
 * There is no `/api/file` in v2 to fetch bytes back through, so the card carries
 * the reference and the mime and the client caches the image itself on first
 * sight rather than re-fetching per render.
 */
private fun filePart(part: JSONObject, messageId: String, index: Int): JSONObject {
    val name = part.optString("name")
        .ifBlank { part.optString("filename") }
        .ifBlank { part.optString("uri").substringAfterLast('/').ifBlank { "attachment" } }
    return JSONObject().apply {
        put("type", "file")
        put("id", part.optString("id").ifBlank { "$messageId-f$index" })
        put("filename", name)
        put("mime", part.optString("mime"))
        put("uri", part.optString("uri"))
        // Inline attachments carry the bytes directly; reference ones only point
        // at a path on the machine running opencode.
        part.optString("data").takeIf { it.isNotBlank() }?.let { put("data", it) }
    }
}

/** v2 form options arrive as objects or bare strings; the phone wants labels. */
    private fun formOptions(options: JSONArray?): JSONArray = JSONArray().apply {
        if (options == null) return@apply
        for (o in 0 until options.length()) {
            when (val opt = options.opt(o)) {
                is JSONObject -> put(JSONObject().put("label", opt.optString("label")))
                else -> put(JSONObject().put("label", opt?.toString().orEmpty()))
            }
        }
    }

    private fun Session.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("directory", directory ?: JSONObject.NULL)
        .put("model", model ?: JSONObject.NULL)
        .put("provider", provider ?: JSONObject.NULL)
        .put("agent", agent ?: JSONObject.NULL)
        .put("updated", updated ?: 0L)

    class OpencodeHttpException(val code: Int, detail: String) :
        RuntimeException("opencode HTTP $code: $detail")

    private companion object {
        const val TAG = "V2Client"
        val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Page sizes.
         *
         * These were 500 and 200. Over a remote tunnel that was 258 KB for the
         * drawer and 4.1 MB to open one session, which is what made connecting
         * and opening feel broken. The server's own defaults are 50 for both, so
         * that is what we ask for; paging fills in the rest as the user scrolls.
         */
        const val SESSION_PAGE = 50
        const val MESSAGE_PAGE = 50
    }
}