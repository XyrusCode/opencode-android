package com.xyrus.opencode.opencode

import com.xyrus.opencode.bridge.SessionMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These payloads are copied from events captured off a live v2 server while a
 * real prompt ran, so the mapping is pinned to the shapes opencode actually
 * emits rather than guesses.
 */
class V2EventMapperTest {

    private fun event(type: String, data: JSONObject) =
        JSONObject().put("type", type).put("data", data)

    @Test
    fun `streams text deltas`() {
        val chunk = V2EventMapper.map(
            event(
                "session.text.delta",
                JSONObject().put("sessionID", "ses_1").put("assistantMessageID", "msg_1").put("delta", "1\n"),
            )
        )
        assertEquals(V2EventMapper.Chunk.Text("msg_1", "1\n"), chunk)
        val frame = V2EventMapper.toFrame(chunk!!)!!
        assertEquals("output_delta", frame.type)
        assertEquals("msg_1", frame.messageID)
        assertEquals("1\n", frame.delta)
    }

    @Test
    fun `streams reasoning deltas`() {
        val chunk = V2EventMapper.map(
            event(
                "session.reasoning.delta",
                JSONObject().put("assistantMessageID", "msg_1").put("delta", "The"),
            )
        )
        assertEquals(V2EventMapper.Chunk.Reasoning("msg_1", "The"), chunk)
        assertEquals("output_delta", V2EventMapper.toFrame(chunk!!)!!.type)
    }

    @Test
    fun `a finished text stream carries the whole text`() {
        val chunk = V2EventMapper.map(
            event(
                "session.text.ended",
                JSONObject()
                    .put("assistantMessageID", "msg_1")
                    .put("text", "1\n2\n3\n4\n5"),
            )
        )
        val frame = V2EventMapper.toFrame(chunk!!)!!
        assertEquals("part", frame.type)
        val part = frame.part!!
        assertEquals("text", part.getString("type"))
        assertEquals("1\n2\n3\n4\n5", part.getString("text"))
    }

    @Test
    fun `an ended stream with no text just closes the turn`() {
        val chunk = V2EventMapper.map(
            event("session.reasoning.ended", JSONObject().put("assistantMessageID", "msg_1"))
        )
        assertEquals(V2EventMapper.Chunk.Done("msg_1"), chunk)
        assertEquals("message_done", V2EventMapper.toFrame(chunk!!)!!.type)
    }

    @Test
    fun `execution start and finish drive the busy indicator`() {
        val busy = V2EventMapper.map(event("session.execution.started", JSONObject()))
        assertEquals(V2EventMapper.Chunk.Busy(true, null), busy)
        assertEquals("busy", V2EventMapper.toFrame(busy!!)!!.status)

        val done = V2EventMapper.map(event("session.execution.succeeded", JSONObject()))
        assertEquals("idle", V2EventMapper.toFrame(done!!)!!.status)

        val failed = V2EventMapper.map(event("session.execution.failed", JSONObject()))
        assertEquals(V2EventMapper.Chunk.Busy(false, "failed"), failed)
    }

    @Test
    fun `events the phone has no use for are dropped`() {
        listOf(
            event("server.connected", JSONObject()),
            event("session.inbox.enqueued", JSONObject()),
            event("provider.updated", JSONObject()),
        ).forEach { assertNull("expected drop for ${it.getString("type")}", V2EventMapper.map(it)) }
    }

    @Test
    fun `a delta without a message id is dropped rather than mis-attributed`() {
        val orphan = V2EventMapper.map(event("session.text.delta", JSONObject().put("delta", "hi")))
        assertNull(orphan)
    }

    @Test
    fun `an empty delta does not produce a frame`() {
        val empty = V2EventMapper.map(
            event("session.text.delta", JSONObject().put("assistantMessageID", "msg_1").put("delta", ""))
        )
        assertNull(empty)
    }

    @Test
    fun `step completion ends the turn`() {
        val chunk = V2EventMapper.map(
            event("session.step.ended", JSONObject().put("assistantMessageID", "msg_1"))
        )
        assertTrue(V2EventMapper.toFrame(chunk!!) is SessionMessage)
        assertEquals("message_done", V2EventMapper.toFrame(chunk)!!.type)
    }
}