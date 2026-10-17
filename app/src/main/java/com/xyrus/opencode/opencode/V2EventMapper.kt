package com.xyrus.opencode.opencode

import com.xyrus.opencode.bridge.SessionMessage
import org.json.JSONObject

/**
 * Translate opencode v2 server-sent events into the frames the phone already
 * renders.
 *
 * v1 streamed `message.part.updated` with the whole part inside `properties`.
 * v2 is a durable event log instead: flat `session.*` events carrying `data`,
 * where text arrives as deltas keyed by `assistantMessageID` + `ordinal`. Both
 * are mapped onto the same frames so the transcript, composer and status
 * handling stay untouched.
 */
object V2EventMapper {

    /** One streamed fragment, before it becomes a phone frame. */
    sealed interface Chunk {
        data class Text(val messageID: String, val delta: String) : Chunk
        data class Reasoning(val messageID: String, val delta: String) : Chunk
        data class PartDone(val messageID: String, val part: JSONObject) : Chunk
        data class Busy(val busy: Boolean, val reason: String?) : Chunk
        data class Done(val messageID: String) : Chunk
    }

    fun map(event: JSONObject): Chunk? {
        val type = event.optString("type")
        val data = event.optJSONObject("data") ?: return null

        // v2 keys streamed text by the assistant message plus an ordinal, since
        // one assistant turn can interleave several text and reasoning streams.
        fun messageId() = data.optString("assistantMessageID").ifEmpty { return null }

        return when (type) {
            "session.text.delta" -> {
                val id = messageId() ?: return null
                data.optString("delta").takeIf { it.isNotEmpty() }?.let { Chunk.Text(id, it) }
            }

            "session.reasoning.delta" -> {
                val id = messageId() ?: return null
                data.optString("delta").takeIf { it.isNotEmpty() }
                    ?.let { Chunk.Reasoning(id, it) }
            }

            "session.text.ended", "session.reasoning.ended" -> {
                val id = messageId() ?: return null
                val text = data.optString("text")
                val partType = if (type.contains("reasoning")) "reasoning" else "text"
                // A finished stream repeats text already sent as deltas; send the
                // part whole anyway so a reconnect fills the gap.
                if (text.isEmpty()) {
                    Chunk.Done(id)
                } else {
                    Chunk.PartDone(
                        id,
                        JSONObject()
                            .put("type", partType)
                            .put("text", text)
                            .put("messageID", id)
                            .put("time", JSONObject().put("end", System.currentTimeMillis())),
                    )
                }
            }

            "session.step.ended" -> {
                val id = messageId() ?: return null
                Chunk.Done(id)
            }

            "session.execution.started" -> Chunk.Busy(true, null)

            "session.execution.succeeded", "session.execution.failed" ->
                Chunk.Busy(false, if (type.endsWith("failed")) "failed" else null)

            else -> null
        }
    }

    /** Render a mapped chunk as the frame the phone expects. */
    fun toFrame(chunk: Chunk): SessionMessage? = when (chunk) {
        // The transcript appends deltas by message id, so text and reasoning
        // both arrive the same way; the closing part decides the block type.
        is Chunk.Text -> SessionMessage(
            type = "output_delta",
            messageID = chunk.messageID,
            delta = chunk.delta,
        )

        is Chunk.Reasoning -> SessionMessage(
            type = "output_delta",
            messageID = chunk.messageID,
            delta = chunk.delta,
        )

        is Chunk.PartDone -> SessionMessage(
            type = "part",
            messageID = chunk.messageID,
            part = chunk.part,
        )

        is Chunk.Done -> SessionMessage(type = "message_done", messageID = chunk.messageID)

        is Chunk.Busy -> SessionMessage(
            type = "status",
            status = if (chunk.busy) "busy" else "idle",
            reason = chunk.reason,
        )
    }
}