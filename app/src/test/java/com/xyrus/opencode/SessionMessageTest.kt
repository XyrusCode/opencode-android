package com.xyrus.opencode.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMessageTest {

    @Test
    fun `parses a welcome frame`() {
        val frame = SessionMessage.parse("""{"type":"welcome","message":"authenticated"}""")
        assertEquals("welcome", frame.type)
        assertTrue(frame.isAuthenticated)
        assertEquals("authenticated", frame.message)
    }

    @Test
    fun `parses an error frame`() {
        val frame = SessionMessage.parse("""{"type":"error","message":"boom","fatal":true}""")
        assertTrue(frame.isError)
        assertEquals("boom", frame.message)
    }

    @Test
    fun `missing optional fields become null not empty string`() {
        val frame = SessionMessage.parse("""{"type":"output_delta","delta":"hi"}""")
        assertNull(frame.sessionID)
        assertNull(frame.status)
        assertEquals("hi", frame.delta)
    }

    @Test
    fun `parses a session list`() {
        val raw = """
            {"type":"sessions","kind":"list","sessions":[
              {"id":"ses_1","title":"Fix CI","directory":"C:\\work\\repo","model":"m1"},
              {"id":"ses_2","title":"Docs","directory":"C:\\work","model":null}
            ]}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals("list", frame.kind)
        assertEquals(2, frame.sessions.size)
        assertEquals("Fix CI", frame.sessions[0].title)
        assertEquals("m1", frame.sessions[0].model)
        assertEquals("repo · m1", frame.sessions[0].subtitle)
        // No model on the second session: subtitle falls back to the folder.
        assertEquals("work", frame.sessions[1].subtitle)
    }

    @Test
    fun `parses history lines`() {
        val raw = """
            {"type":"sessions","kind":"history","sessionID":"ses_1",
             "lines":["you: hello","opencode: hi there"]}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals(listOf("you: hello", "opencode: hi there"), frame.lines)
        assertEquals("ses_1", frame.sessionID)
    }

    @Test
    fun `parses an attached marker`() {
        val frame = SessionMessage.parse(
            """{"type":"session","kind":"attached","sessionID":"ses_9"}""",
        )
        assertEquals("attached", frame.kind)
        assertEquals("ses_9", frame.sessionID)
    }

    @Test(expected = org.json.JSONException::class)
    fun `rejects malformed json`() {
        SessionMessage.parse("not json at all")
    }
}
