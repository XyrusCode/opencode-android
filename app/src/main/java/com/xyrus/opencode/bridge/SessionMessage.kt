package com.xyrus.opencode.bridge

import org.json.JSONObject

/**
 * A frame on the wire between the phone and the desktop bridge.
 *
 * Mirrors `bridge/protocol.py` on the desktop side. Both ends must agree, so
 * changes here belong in the same commit as changes there.
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
    val lines: List<String> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val session: Session? = null,
) {
    val isAuthenticated get() = type == "welcome"
    val isError get() = type == "error"

    companion object {
        fun parse(raw: String): SessionMessage {
            val json = JSONObject(raw)
            val sessions = json.optJSONArray("sessions")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { Session.parse(it) }
                }
            }.orEmpty()

            val lines = json.optJSONArray("lines")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            }.orEmpty()

            return SessionMessage(
                type = json.optString("type"),
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
                lines = lines,
                sessions = sessions,
                session = json.optJSONObject("session")?.let { Session.parse(it) },
            )
        }
    }
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

/**
 * JSONObject.optString returns "" for missing keys, which is indistinguishable
 * from a genuinely empty value. Null lets callers tell the two apart.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
