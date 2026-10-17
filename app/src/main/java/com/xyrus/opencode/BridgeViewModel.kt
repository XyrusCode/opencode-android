package com.xyrus.opencode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrus.opencode.bridge.Agent
import com.xyrus.opencode.bridge.BridgeClient
import com.xyrus.opencode.bridge.HistoryMessage
import com.xyrus.opencode.bridge.PermissionPrompt
import com.xyrus.opencode.bridge.Project
import com.xyrus.opencode.bridge.Provider
import com.xyrus.opencode.bridge.QuestionPrompt
import com.xyrus.opencode.bridge.Session
import com.xyrus.opencode.bridge.SessionMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

const val DEFAULT_AUTH_TOKEN = "opencode-2026-mobile-companion"
const val DEFAULT_PORT = 9876

/**
 * One rendered entry in the transcript. The bridge forwards the assistant's work
 * as structured parts (tool calls, files, snapshots, patches, errors), so the
 * transcript is a mixed list rather than a list of plain strings.
 */
sealed interface TranscriptItem {
    val id: String
}

/** A user or assistant text block. Streamed assistant text grows via [id]. */
data class TextItem(
    override val id: String,
    val role: String,
    val text: String,
) : TranscriptItem

/**
 * A structured part rendered as a card: a tool call, a file, a snapshot diff, a
 * patch, or an error. Fields are pre-flattened so the UI does no JSON digging.
 */
data class PartItem(
    override val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val state: String?,
    val isError: Boolean,
) : TranscriptItem

/** Which composer picker sheet is open (null = none). */
enum class PickerTarget { Model, Agent, Project }

/**
 * A real project, derived from the sessions' own working directories. The bridge's
 * `/project` catalog only exposes opencode worktrees (e.g. "global" and git-hash ids),
 * which are meaningless for navigation, so the UI groups sessions by their real cwd.
 */
data class RealProject(val name: String, val directory: String)

fun realProjects(sessions: List<Session>): List<RealProject> =
    sessions.mapNotNull { s ->
        s.directory?.takeIf { it.isNotBlank() }?.let { dir ->
            RealProject(dir.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'), dir)
        }
    }.distinctBy { it.directory }.sortedBy { it.name.lowercase() }

/** The model the next message will use, plus a human label for the chip. */
data class ModelChoice(
    val modelID: String,
    val providerID: String,
    val label: String,
)

data class UiState(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val connection: BridgeClient.State = BridgeClient.State.Disconnected,
    val sessions: List<Session> = emptyList(),
    val attachedSession: Session? = null,
    val transcript: List<TranscriptItem> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
    // Catalogs for the picker sheets.
    val providers: List<Provider> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val projects: List<Project> = emptyList(),
    // Active selection, re-sent before every prompt (the bridge pops it).
    val selectedModel: ModelChoice? = null,
    val selectedAgent: String? = null,
    val selectedProjectDir: String? = null,
    // What the bridge reports is currently active (informational).
    val activeModel: ModelChoice? = null,
    // Interactive prompts awaiting an answer from the phone.
    val pendingPermission: PermissionPrompt? = null,
    val pendingQuestion: QuestionPrompt? = null,
    // Which composer picker sheet is open.
    val openPicker: PickerTarget? = null,
) {
    val isConnected get() = connection == BridgeClient.State.Ready
    val canSend get() = isConnected && attachedSession != null && !streaming
}

/**
 * Holds the bridge connection and everything the UI renders.
 *
 * BridgeClient calls back on OkHttp's dispatcher thread, so every mutation is
 * funnelled through [viewModelScope] to keep StateFlow writes main-safe.
 */
class BridgeViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: BridgeClient? = null

    fun setHost(host: String) = _state.update { it.copy(host = host.trim()) }

    fun setPort(port: Int) = _state.update { it.copy(port = port) }

    fun connect() {
        val current = _state.value
        if (current.host.isBlank()) {
            _state.update { it.copy(error = "Enter the desktop IP address") }
            return
        }
        disconnect()

        val bridge = BridgeClient(
            token = DEFAULT_AUTH_TOKEN,
            onMessage = { frame -> viewModelScope.launch { handle(frame) } },
            onStateChange = { state -> viewModelScope.launch { onConnectionChange(state) } },
        )
        client = bridge
        bridge.connect(current.host, current.port)
    }

    fun disconnect() {
        client?.shutdown()
        client = null
        _state.update {
            it.copy(
                connection = BridgeClient.State.Disconnected,
                attachedSession = null,
                streaming = false,
                pendingPermission = null,
                pendingQuestion = null,
            )
        }
    }

    fun loadSessions() = client?.listSessions()

    fun attach(session: Session) {
        // Carry the session's own model/agent over: send() re-pushes the
        // selection before every prompt, so a prompt never goes out model-less
        // (which wedges opencode into a permanent "busy"). Clearing streaming
        // stops a previously wedged session from blocking this one.
        val carried = session.model?.let { ModelChoice(it, session.provider.orEmpty(), it) }
        _state.update {
            it.copy(
                attachedSession = session,
                transcript = emptyList(),
                error = null,
                streaming = false,
                pendingPermission = null,
                pendingQuestion = null,
                selectedModel = carried ?: it.selectedModel,
                selectedAgent = session.agent ?: it.selectedAgent,
            )
        }
        client?.attach(session.id)
    }

    fun createSession() {
        // The project directory is remembered per-socket and consumed by the next create.
        _state.value.selectedProjectDir?.let { client?.pickProject(it) }
        _state.update {
            it.copy(
                attachedSession = null,
                transcript = emptyList(),
                pendingPermission = null,
                pendingQuestion = null,
            )
        }
        client?.createSession("From phone")
    }

    // --- Selection ---------------------------------------------------------

    fun chooseModel(modelID: String, providerID: String, label: String) {
        val choice = ModelChoice(modelID, providerID, label)
        _state.update { it.copy(selectedModel = choice) }
        // Send now so the bridge acks; send() re-sends before each prompt.
        _state.value.attachedSession?.id?.let { client?.selectModel(it, modelID, providerID) }
    }

    fun chooseAgent(name: String) {
        _state.update { it.copy(selectedAgent = name) }
        client?.selectAgent(name)
    }

    fun chooseProjectDir(directory: String) {
        _state.update { it.copy(selectedProjectDir = directory) }
        client?.pickProject(directory)
    }

    // --- Interactive prompt replies ----------------------------------------

    /** reply is one of "once", "always", "reject". */
    fun replyPermission(reply: String, message: String? = null) {
        val pending = _state.value.pendingPermission ?: return
        val requestID = pending.requestID ?: return
        client?.replyPermission(requestID, reply, message)
        _state.update { it.copy(pendingPermission = null) }
    }

    fun replyQuestion(answers: List<List<String>>) {
        val pending = _state.value.pendingQuestion ?: return
        val requestID = pending.requestID ?: return
        client?.replyQuestion(requestID, answers)
        _state.update { it.copy(pendingQuestion = null) }
    }

    // --- Sending ------------------------------------------------------------

    fun send(text: String) {
        val trimmed = text.trim()
        val session = _state.value.attachedSession ?: return
        if (trimmed.isEmpty()) return

        // The bridge pops the selection into the next prompt, so re-send the
        // active model/agent right before every message.
        _state.value.selectedModel?.let {
            client?.selectModel(session.id, it.modelID, it.providerID)
        }
        _state.value.selectedAgent?.let { client?.selectAgent(it) }

        _state.update {
            it.copy(
                transcript = it.transcript + TextItem("user-${System.nanoTime()}", "user", trimmed),
                streaming = true,
            )
        }
        client?.prompt(session.id, trimmed)
    }

    fun abort() {
        _state.value.attachedSession?.let { client?.abort(it.id) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Open (or close, with null) a composer picker sheet. */
    fun showPicker(target: PickerTarget?) = _state.update { it.copy(openPicker = target) }

    // --- Frame handling ------------------------------------------------------

    private fun onConnectionChange(state: BridgeClient.State) {
        _state.update { it.copy(connection = state) }
        if (state == BridgeClient.State.Ready) loadSessions()
    }

    private fun handle(frame: SessionMessage) {
        when (frame.type) {
            "welcome" -> _state.update {
                it.copy(connection = BridgeClient.State.Ready, error = null)
            }

            "error" -> {
                _state.update { it.copy(error = frame.message ?: "Unknown bridge error") }
                client?.shutdown()
                client = null
                _state.update { it.copy(connection = BridgeClient.State.Failed) }
            }

            "sessions" -> if (frame.kind == "list") {
                _state.update { it.copy(sessions = frame.sessions) }
            }

            "history" -> _state.update {
                it.copy(transcript = buildTranscript(frame.messages))
            }

            "session" -> {
                val created = frame.session
                if (frame.kind == "created" && created != null) {
                    _state.update {
                        it.copy(
                            attachedSession = created,
                            transcript = emptyList(),
                            sessions = it.sessions + created,
                            streaming = false,
                        )
                    }
                } else if (frame.kind == "attached") {
                    _state.update { it.copy(streaming = false) }
                }
            }

            "providers" -> _state.update { it.copy(providers = frame.providers) }
            "agents" -> _state.update { it.copy(agents = frame.agents) }
            "projects" -> _state.update { it.copy(projects = frame.projects) }

            "model" -> _state.update {
                it.copy(
                    activeModel = ModelChoice(
                        modelID = frame.modelID.orEmpty(),
                        providerID = frame.providerID.orEmpty(),
                        label = frame.modelID.orEmpty(),
                    ),
                )
            }

            "status" -> if (frame.status == "busy") {
                _state.update { it.copy(streaming = true) }
            } else if (frame.status == "idle" || frame.status == "done") {
                _state.update { it.copy(streaming = false) }
            }

            "output_delta" -> frame.delta?.takeIf { it.isNotEmpty() }?.let { delta ->
                _state.update { it.appendDelta(frame.messageID, delta) }
            }

            "part" -> frame.part?.let { part ->
                _state.update { it.appendPart(part) }
            }

            "message_done" -> _state.update { it.copy(streaming = false) }

            "permission_prompt" -> _state.update {
                it.copy(pendingPermission = frame.permissionPrompt ?: it.pendingPermission)
            }

            "question_prompt" -> _state.update {
                it.copy(pendingQuestion = frame.questionPrompt ?: it.pendingQuestion)
            }

            "pong" -> Unit
        }
    }

    /** Rebuild the transcript from a history frame's structured messages. */
    private fun buildTranscript(messages: List<HistoryMessage>): List<TranscriptItem> =
        messages.flatMap { message ->
            val role = message.role ?: "assistant"
            message.parts.map { part ->
                if (part.optStringOrNullSafe("type") == "text") {
                    TextItem(
                        id = part.optStringOrNullSafe("id") ?: "text-${System.identityHashCode(part)}",
                        role = role,
                        text = part.optStringOrNullSafe("text").orEmpty(),
                    )
                } else {
                    partFrom(part, role)
                }
            }
        }

    /**
     * opencode streams assistant text as deltas keyed to a message. They belong
     * on that message's text item, not as new entries, so append to the matching
     * item while a turn is in flight.
     */
    private fun UiState.appendDelta(messageID: String?, delta: String): UiState {
        val id = messageID ?: "streaming"
        val idx = transcript.indexOfFirst { it is TextItem && it.id == id }
        return if (idx >= 0) {
            val item = transcript[idx] as TextItem
            copy(
                transcript = transcript.toMutableList().also {
                    it[idx] = item.copy(text = item.text + delta)
                },
            )
        } else {
            copy(transcript = transcript + TextItem(id, "assistant", delta))
        }
    }

    /** Upsert a structured part by its stable id (guards against duplicates). */
    private fun UiState.appendPart(part: JSONObject): UiState {
        val item = partFrom(part, role = "assistant")
        val idx = transcript.indexOfFirst { it.id == item.id }
        return if (idx >= 0) {
            copy(transcript = transcript.toMutableList().also { it[idx] = item })
        } else {
            copy(transcript = transcript + item)
        }
    }

    override fun onCleared() {
        client?.shutdown()
        client = null
        super.onCleared()
    }
}

// --- Part normalization ------------------------------------------------------

private fun JSONObject.optStringOrNullSafe(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotEmpty() }
}

/**
 * Flatten one opencode part into renderable strings. Kept here so the UI stays
 * declarative and does no JSON digging at draw time.
 */
private fun partFrom(part: JSONObject, role: String): PartItem {
    val type = part.optStringOrNullSafe("type") ?: "part"
    val id = part.optStringOrNullSafe("id") ?: "part-${System.identityHashCode(part)}"
    val state = part.optStringOrNullSafe("state")
    val errorText = part.optStringOrNullSafe("error")
    val isError = errorText != null
    return when (type) {
        "tool" -> {
            val tool = part.optStringOrNullSafe("tool") ?: "tool"
            val input = part.optJSONObject("input")?.let { compact(it) }.orEmpty()
            val output = part.optStringOrNullSafe("output")
                ?: part.optStringOrNullSafe("result")
                ?: errorText
                ?: ""
            PartItem(id, type, tool, input.take(120), output.take(BODY_LIMIT), state, isError)
        }

        "file" -> {
            val filename = part.optStringOrNullSafe("filename")
                ?: part.optStringOrNullSafe("filePath")
                ?: "file"
            val subtitle = part.optStringOrNullSafe("mime")
                ?: part.optStringOrNullSafe("url")
                ?: ""
            PartItem(id, type, filename, subtitle.take(120), "", state, isError)
        }

        "snapshot", "patch" -> {
            val diff = part.optStringOrNullSafe("patch")
                ?: part.optStringOrNullSafe("diff")
                ?: part.optStringOrNullSafe("text")
                ?: ""
            PartItem(
                id, type, type.replaceFirstChar { it.uppercase() }, "",
                diff.take(BODY_LIMIT), state, isError,
            )
        }

        else -> {
            val text = part.optStringOrNullSafe("text")
                ?: part.optStringOrNullSafe("message")
                ?: ""
            PartItem(id, type, type, role, (if (isError) errorText else text).orEmpty().take(BODY_LIMIT), state, isError)
        }
    }
}

/** A short "k=v, k=v" preview of a tool input. */
private fun compact(obj: JSONObject, limit: Int = 3): String {
    val parts = mutableListOf<String>()
    for (key in obj.keys()) {
        if (parts.size >= limit) break
        parts += "$key=${obj.opt(key)?.toString()?.take(40)}"
    }
    return parts.joinToString(", ")
}

private const val BODY_LIMIT = 6000