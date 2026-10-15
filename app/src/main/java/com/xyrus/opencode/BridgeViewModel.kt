package com.xyrus.opencode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrus.opencode.bridge.BridgeClient
import com.xyrus.opencode.bridge.Session
import com.xyrus.opencode.bridge.SessionMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEFAULT_AUTH_TOKEN = "opencode-2026-mobile-companion"
const val DEFAULT_PORT = 9876

/** One line in the conversation transcript. */
data class TranscriptLine(val text: String, val fromUser: Boolean)

data class UiState(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val connection: BridgeClient.State = BridgeClient.State.Disconnected,
    val sessions: List<Session> = emptyList(),
    val attachedSession: Session? = null,
    val transcript: List<TranscriptLine> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
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
            )
        }
    }

    fun loadSessions() = client?.listSessions()

    fun attach(session: Session) {
        _state.update {
            it.copy(
                attachedSession = session,
                transcript = emptyList(),
                error = null,
            )
        }
        client?.attach(session.id)
    }

    fun createSession() {
        _state.update { it.copy(attachedSession = null, transcript = emptyList()) }
        client?.createSession("From phone")
    }

    fun send(text: String) {
        val trimmed = text.trim()
        val session = _state.value.attachedSession ?: return
        if (trimmed.isEmpty()) return

        _state.update {
            it.copy(
                transcript = it.transcript + TranscriptLine(trimmed, fromUser = true),
                streaming = true,
            )
        }
        client?.prompt(session.id, trimmed)
    }

    fun abort() {
        _state.value.attachedSession?.let { client?.abort(it.id) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

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

            "sessions" -> when (frame.kind) {
                "list" -> _state.update { it.copy(sessions = frame.sessions) }
                "history" -> _state.update {
                    it.copy(
                        transcript = frame.lines.map { line ->
                            TranscriptLine(line, fromUser = line.startsWith("you:"))
                        },
                    )
                }
            }

            "session" -> {
                val created = frame.session
                if (frame.kind == "created" && created != null) {
                    _state.update {
                        it.copy(
                            attachedSession = created,
                            transcript = emptyList(),
                            sessions = it.sessions + created,
                        )
                    }
                }
            }

            "status" -> if (frame.status == "busy") {
                _state.update { it.copy(streaming = true) }
            }

            "output_delta" -> frame.delta?.takeIf { it.isNotEmpty() }?.let { delta ->
                _state.update { current -> current.appendDelta(delta) }
            }

            "message_done" -> _state.update { it.copy(streaming = false) }

            "pong" -> Unit
        }
    }

    /**
     * opencode streams deltas for the message currently being written. Those
     * belong on the last assistant line, not as new lines, so append to the tail
     * while a turn is in flight.
     */
    private fun UiState.appendDelta(delta: String): UiState {
        val tail = transcript.lastOrNull()
        return if (tail != null && !tail.fromUser) {
            copy(transcript = transcript.dropLast(1) + tail.copy(text = tail.text + delta))
        } else {
            copy(transcript = transcript + TranscriptLine(delta, fromUser = false))
        }
    }

    override fun onCleared() {
        client?.shutdown()
        client = null
        super.onCleared()
    }
}
