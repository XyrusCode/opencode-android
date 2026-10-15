package com.xyrus.opencode.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * A frame on the wire between the phone and the desktop bridge.
 *
 * Mirrors `bridge/protocol.py` on the desktop side. Both ends must agree, so
 * changes here belong in the same release as changes there.
 *
 * The phone renders richly: instead of flat `lines`, the bridge now forwards
 * structured parts (text deltas, tool-call cards, file/snapshot diffs, errors),
 * model/agent/project catalogs, and interactive permission/question prompts.
 * Each of those arrives as its own typed frame and is parsed here.
 */
data class SessionMessage(
    val type: String,
    val sessionID: String? = null,
    val messageID: String? = null,
    val delta: String? = null,
    val status: String? = null,
    val reason: String? = null,
    val kind: String? = null,
    val title: String? = null,
    val text: String? = null,
    val message: String? = null,
    val token: String? = null,
    // Transcripts and catalogs (kept for compatibility; history is structured
    // below).
    val lines: List<String> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val session: Session? = null,
    // Rich-rendering and selection payloads.
    val providers: List<Provider> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val projects: List<Project> = emptyList(),
    val messages: List<HistoryMessage> = emptyList(),
    val part: JSONObject? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val requestID: String? = null,
    val permissionPrompt: PermissionPrompt? = null,
    val questionPrompt: QuestionPrompt? = null,
) {
    val isAuthenticated get() = type == "welcome"
    val isError get() = type == "error"

    // region Parsing

    companion object {
        fun parse(raw: String): SessionMessage {
            val json = JSONObject(raw)
            val type = json.optString("type")
            return SessionMessage(
                type = type,
                sessionID = json.optStringOrNull("sessionID"),
                messageID = json.optStringOrNull("messageID"),
                delta = json.optStringOrNull("delta"),
                status = json.optStringOrNull("status"),
                reason = json.optStringOrNull("reason"),
                kind = json.optStringOrNull("kind"),
                title = json.optStringOrNull("title"),
                text = json.optStringOrNull("text"),
                message = json.optStringOrNull("message"),
                token = json.optStringOrNull("token"),
                sessions = parseSessions(json),
                session = json.optJSONObject("session")?.let { Session.parse(it) },
                providers = parseProviders(json),
                agents = parseAgents(json),
                projects = parseProjects(json),
                messages = parseHistory(json),
                part = json.optJSONObject("part"),
                modelID = json.optStringOrNull("modelID"),
                providerID = json.optStringOrNull("providerID"),
                requestID = json.optStringOrNull("requestID"),
                permissionPrompt = parsePermissionPrompt(json, type),
                questionPrompt = parseQuestionPrompt(json, type),
            )
        }

        private fun parseSessions(json: JSONObject): List<Session> =
            json.optJSONArray("sessions")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { Session.parse(it) }
                }
            }.orEmpty()

        private fun parseProviders(json: JSONObject): List<Provider> =
            json.optJSONArray("providers")?.let { array ->
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Provider(
                        id = o.optStringOrNull("id"),
                        name = o.optStringOrNull("name"),
                        models = parseModels(o.optJSONArray("models")),
                    )
                }
            }.orEmpty()

        private fun parseModels(array: JSONArray?): List<Model> =
            array?.let {
                (0 until it.length()).map { i ->
                    val o = it.getJSONObject(i)
                    Model(
                        id = o.optStringOrNull("id"),
                        name = o.optStringOrNull("name"),
                        providerID = o.optStringOrNull("providerID"),
                        cost = o.optJSONObject("cost"),
                        limit = o.optJSONObject("limit"),
                    )
                }
            }.orEmpty()

        private fun parseAgents(json: JSONObject): List<Agent> =
            json.optJSONArray("agents")?.let { array ->
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Agent(
                        name = o.optStringOrNull("name"),
                        description = o.optStringOrNull("description"),
                    )
                }
            }.orEmpty()

        private fun parseProjects(json: JSONObject): List<Project> =
            json.optJSONArray("projects")?.let { array ->
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    Project(
                        id = o.optStringOrNull("id"),
                        name = o.optStringOrNull("name") ?: o.optStringOrNull("id"),
                        worktree = o.optStringOrNull("worktree"),
                        vcs = o.optStringOrNull("vcs"),
                        icon = o.optJSONObject("icon"),
                    )
                }
            }.orEmpty()

        private fun parseHistory(json: JSONObject): List<HistoryMessage> =
            json.optJSONArray("messages")?.let { array ->
                (0 until array.length()).map { i ->
                    val m = array.getJSONObject(i)
                    val parts = m.optJSONArray("parts")?.let { pa ->
                        (0 until pa.length()).mapNotNull { pa.optJSONObject(it) }
                    }.orEmpty()
                    HistoryMessage(m.optStringOrNull("role"), parts)
                }
            }.orEmpty()

        private fun parsePermissionPrompt(
            json: JSONObject,
            type: String,
        ): PermissionPrompt? =
            if (type == "permission_prompt") {
                val tool = json.optJSONObject("tool")
                PermissionPrompt(
                    requestID = json.optStringOrNull("requestID"),
                    permission = json.optStringOrNull("permission"),
                    patterns = json.optStringArray("patterns"),
                    toolCallID = tool?.optStringOrNull("callID"),
                    toolMessageID = tool?.optStringOrNull("messageID"),
                )
            } else {
                null
            }

        private fun parseQuestionPrompt(
            json: JSONObject,
            type: String,
        ): QuestionPrompt? =
            if (type == "question_prompt") {
                val tool = json.optJSONObject("tool")
                QuestionPrompt(
                    requestID = json.optStringOrNull("requestID"),
                    questions = parseQuestions(json.optJSONArray("questions")),
                    toolCallID = tool?.optStringOrNull("callID"),
                    toolMessageID = tool?.optStringOrNull("messageID"),
                )
            } else {
                null
            }

        private fun parseQuestions(array: JSONArray?): List<Question> =
            array?.let {
                (0 until it.length()).map { i ->
                    val q = it.getJSONObject(i)
                    Question(
                        header = q.optStringOrNull("header"),
                        question = q.optStringOrNull("question"),
                        multiple = q.optNullableBoolean("multiple"),
                        options = (0 until (q.optJSONArray("options")?.length() ?: 0)).map { j ->
                            Option(q.optJSONArray("options")!!.optJSONObject(j)?.optStringOrNull("label"))
                        },
                    )
                }
            }.orEmpty()
    }

    // endregion
}

/** Summary of an opencode session, as trimmed by the bridge. */
data class Session(
    val id: String,
    val title: String,
    val directory: String?,
    val model: String?,
) {
    val subtitle: String
        get() = listOfNotNull(
            directory?.substringAfterLast('\\')?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
            model,
        ).joinToString(" · ")

    companion object {
        fun parse(json: JSONObject) = Session(
            id = json.optString("id"),
            title = json.optString("title"),
            directory = json.optStringOrNull("directory"),
            model = json.optStringOrNull("model"),
        )
    }
}

// --- Catalog + prompt models ---------------------------------------------

data class Provider(val id: String?, val name: String?, val models: List<Model>)
data class Model(
    val id: String?,
    val name: String?,
    val providerID: String?,
    val cost: JSONObject? = null,
    val limit: JSONObject? = null,
)

data class Agent(val name: String?, val description: String?)

data class Project(
    val id: String?,
    val name: String?,
    val worktree: String?,
    val vcs: String?,
    val icon: JSONObject? = null,
)

data class HistoryMessage(val role: String?, val parts: List<JSONObject>)

data class PermissionPrompt(
    val requestID: String?,
    val permission: String?,
    val patterns: List<String>,
    val toolCallID: String?,
    val toolMessageID: String?,
)

data class QuestionPrompt(
    val requestID: String?,
    val questions: List<Question>,
    val toolCallID: String?,
    val toolMessageID: String?,
)

data class Question(
    val header: String?,
    val question: String?,
    val multiple: Boolean?,
    val options: List<Option>,
)

data class Option(val label: String?)

// --- JSONObject helpers ----------------------------------------------------

/**
 * JSONObject.optString returns "" for missing keys, which is indistinguishable
 * from a genuinely empty value. Null lets callers tell the two apart.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

internal fun JSONObject.optStringArray(key: String): List<String> =
    optJSONArray(key)?.let { (0 until it.length()).map { i -> it.optString(i) } }.orEmpty()

/** Like optBoolean but distinguishes missing/absent from an explicit false. */
internal fun JSONObject.optNullableBoolean(key: String): Boolean? =
    if (isNull(key)) null else optBoolean(key)
