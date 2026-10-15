package com.xyrus.opencode.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `parses structured history messages with parts`() {
        val raw = """
            {"type":"history","sessionID":"ses_1",
             "messages":[
               {"role":"user","parts":[{"type":"text","text":"build a button"}]},
               {"role":"assistant","parts":[
                 {"type":"text","text":"ok"},
                 {"type":"tool","tool":"bash","state":{"result":"ran clean"}}
               ]}
             ]}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals("history", frame.type)
        assertEquals("ses_1", frame.sessionID)
        assertEquals(2, frame.messages.size)
        assertEquals("user", frame.messages[0].role)
        assertEquals("build a button", frame.messages[0].parts[0].optString("text"))
        assertEquals("assistant", frame.messages[1].role)
        assertEquals(2, frame.messages[1].parts.size)
        assertEquals("tool", frame.messages[1].parts[1].optString("type"))
    }

    @Test
    fun `parses an attached marker`() {
        val frame = SessionMessage.parse(
            """{"type":"session","kind":"attached","sessionID":"ses_9"}""",
        )
        assertEquals("attached", frame.kind)
        assertEquals("ses_9", frame.sessionID)
    }

    @Test
    fun `parses a streamed text delta`() {
        val frame = SessionMessage.parse(
            """{"type":"output_delta","sessionID":"ses_1","messageID":"msg_1","delta":"hi"}""",
        )
        assertEquals("output_delta", frame.type)
        assertEquals("msg_1", frame.messageID)
        assertEquals("hi", frame.delta)
    }

    @Test
    fun `parses a finalized tool part as a raw part object`() {
        // The bridge forwards tool/snapshot/file parts whole; the phone renders
        // by inspecting part.type. The raw JSONObject is passed through untouched.
        val frame = SessionMessage.parse(
            """{"type":"part","sessionID":"ses_1","messageID":"msg_1",
               "part":{"type":"tool","tool":"bash","callID":"c1",
                       "state":{"result":"ran clean"}}}""",
        )
        assertEquals("part", frame.type)
        assertNotNull(frame.part)
        assertEquals("tool", frame.part!!.optString("type"))
        assertEquals("bash", frame.part!!.optString("tool"))
        assertEquals("c1", frame.part!!.optString("callID"))
    }

    @Test
    fun `parses message_done with idle reason`() {
        val frame = SessionMessage.parse(
            """{"type":"message_done","sessionID":"ses_1","reason":"idle"}""",
        )
        assertEquals("message_done", frame.type)
        assertEquals("idle", frame.reason)
        assertNull(frame.messageID)
    }

    @Test
    fun `parses the provider catalog`() {
        val raw = """
            {"type":"providers",
             "providers":[
               {"id":"openai","name":"OpenAI",
                "models":[{"id":"gpt-4o","name":"GPT-4o","providerID":"openai"}]}
             ]}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals("providers", frame.type)
        assertEquals(1, frame.providers.size)
        val model = frame.providers[0]
        assertEquals("OpenAI", model.name)
        assertEquals("gpt-4o", model.models[0].id)
        assertEquals("openai", model.models[0].providerID)
    }

    @Test
    fun `parses the agent catalog`() {
        val frame = SessionMessage.parse(
            """{"type":"agents","agents":[{"name":"build","description":"default"}]}""",
        )
        assertEquals("agents", frame.type)
        assertEquals("build", frame.agents[0].name)
        assertEquals("default", frame.agents[0].description)
    }

    @Test
    fun `parses the project catalog with an icon payload`() {
        val frame = SessionMessage.parse(
            """{"type":"projects","projects":[
               {"id":"proj_1","name":"marcuscassel.org","worktree":"/srv/m",
                "vcs":"git","icon":{"url":"data:image/png;base64,AAAA"}}
             ]}""",
        )
        assertEquals("projects", frame.type)
        val project = frame.projects[0]
        assertEquals("proj_1", project.id)
        assertEquals("/srv/m", project.worktree)
        assertEquals("data:image/png;base64,AAAA", project.icon!!.optString("url"))
    }

    @Test
    fun `parses a permission prompt`() {
        val raw = """
            {"type":"permission_prompt","requestID":"per_1","sessionID":"ses_1",
             "permission":"bash","patterns":["rm -rf /"],"always":[],
             "tool":{"callID":"c1","messageID":"msg_1"}}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals("permission_prompt", frame.type)
        val prompt = frame.permissionPrompt!!
        assertEquals("per_1", prompt.requestID)
        assertEquals("bash", prompt.permission)
        assertEquals(listOf("rm -rf /"), prompt.patterns)
        assertEquals("c1", prompt.toolCallID)
        assertEquals("msg_1", prompt.toolMessageID)
        assertNull(frame.questionPrompt)
    }

    @Test
    fun `parses a question prompt`() {
        val raw = """
            {"type":"question_prompt","requestID":"que_1","sessionID":"ses_1",
             "questions":[{"header":"Confirm","question":"Proceed?","multiple":false,
               "options":[{"label":"Yes"},{"label":"No"}]}],
             "tool":{"callID":"c1","messageID":"msg_1"}}
        """.trimIndent()

        val frame = SessionMessage.parse(raw)
        assertEquals("question_prompt", frame.type)
        val prompt = frame.questionPrompt!!
        assertEquals("que_1", prompt.requestID)
        assertEquals(false, prompt.questions[0].multiple)
        assertEquals("Yes", prompt.questions[0].options[0].label)
        assertEquals("No", prompt.questions[0].options[1].label)
    }

    @Test
    fun `parses a model selection echo`() {
        val frame = SessionMessage.parse(
            """{"type":"model","sessionID":"ses_1","modelID":"gpt-4o","providerID":"openai"}""",
        )
        assertEquals("model", frame.type)
        assertEquals("gpt-4o", frame.modelID)
        assertEquals("openai", frame.providerID)
    }

    @Test(expected = org.json.JSONException::class)
    fun `rejects malformed json`() {
        SessionMessage.parse("not json at all")
    }
}
