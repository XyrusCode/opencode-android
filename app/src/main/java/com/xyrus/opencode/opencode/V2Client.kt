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
                startEventStream()
                startPromptPolling()
            } catch (e: Exception) {
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

    private fun get(path: String): JSONObject? {
        http.newCall(request(path).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, body.take(200))
            return if (body.isBlank()) null else unwrap(JSONObject(body))
        }
    }

    private fun post(path: String, body: JSONObject? = null): JSONObject? {
        val payload = (body ?: JSONObject()).toString().toRequestBody(JSON)
        http.newCall(request(path).post(payload).build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OpencodeHttpException(resp.code, text.take(200))
            return if (text.isBlank()) null else unwrap(JSONObject(text))
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
        false
    }

    // --- FrameSource -----------------------------------------------------

    override fun listSessions(): Boolean = attempt {
        val payload = get("/api/session?limit=500") ?: return@attempt
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

    override fun createSession(title: String): Boolean = attempt {
        val body = JSONObject().put("title", title.ifEmpty { "From phone" })
        pendingDirectory?.let { body.put("location", JSONObject().put("directory", it)) }
        val payload = post("/api/session", body) ?: return@attempt
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

    override fun attach(sessionId: String): Boolean = attempt {
        attachedSession = sessionId
        val payload = get("/api/session/$sessionId/message?limit=200")
        val arr = payload?.optJSONArray("data") ?: JSONArray()
        val messages = JSONArray()
        for (i in 0 until arr.length()) {
            historyMessage(arr.getJSONObject(i))?.let { messages.put(it) }
        }
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

    override fun send(sessionId: String, text: String): Boolean = attempt {
        // v2 has no per-message model field, so the session is switched first.
        pendingModel?.let { (modelID, providerID) -> setModelOnServer(sessionId, modelID, providerID) }
        pendingAgent?.let { setAgentOnServer(sessionId, it) }
        post("/api/session/$sessionId/prompt", JSONObject().put("text", text))
    }

    override fun abort(sessionId: String): Boolean = attempt { post("/api/session/$sessionId/interrupt") }

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

    override fun renameSession(sessionId: String, title: String): Boolean = attempt {
        val payload = patch("/api/session/$sessionId", JSONObject().put("title", title))
        val updated = payload?.let { parseSession(it) } ?: return@attempt
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

    override fun deleteSession(sessionId: String): Boolean = attempt {
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

    override fun replyPermission(requestID: String, reply: String, message: String?): Boolean = attempt {
        val sessionId = attachedSession ?: return@attempt
        val body = JSONObject().put("decision", reply)
        message?.let { body.put("message", it) }
        post("/api/session/$sessionId/permission/$requestID/reply", body)
    }

    override fun replyQuestion(requestID: String, answers: List<List<String>>): Boolean = attempt {
        val sessionId = attachedSession ?: return@attempt
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

    private fun pushCatalogs() = run {
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
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "providers")
                    .put("providers", providers)
                    .toString()
            )
        )

        val agents = get("/api/agent")?.optJSONArray("data") ?: JSONArray()
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "agents")
                    .put("agents", JSONArray().apply {
                        for (i in 0 until agents.length()) {
                            val a = agents.getJSONObject(i)
                            put(
                                JSONObject()
                                    .put("name", a.optString("id"))
                                    .put("description", a.optString("description"))
                            )
                        }
                    })
                    .toString()
            )
        )

        val projects = get("/api/project")?.optJSONArray("data") ?: JSONArray()
        onMessage(
            SessionMessage.parse(
                JSONObject()
                    .put("type", "projects")
                    .put("projects", JSONArray().apply {
                        for (i in 0 until projects.length()) {
                            val p = projects.getJSONObject(i)
                            put(
                                JSONObject()
                                    .put("id", p.optString("id"))
                                    .put("name", p.optString("canonical").substringAfterLast('\\').substringAfterLast('/'))
                                    .put("worktree", p.optString("canonical"))
                            )
                        }
                    })
                    .toString()
            )
        )
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
                                "reasoning" -> put(JSONObject().put("type", "reasoning").put("text", c.optString("text")))
                                "tool" -> put(
                                    JSONObject()
                                        .put("type", "tool")
                                        .put("tool", c.optString("name"))
                                        .put("callID", c.optString("id"))
                                        .put("state", c.optJSONObject("state") ?: JSONObject())
                                )
                                else -> Unit
                            }
                        }
                    })
            }

            // Control messages (idle, model-switched, ...) are not transcript.
            else -> null
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
    }
}