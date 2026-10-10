package dev.harness.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.harness.agent.AgentEvent
import dev.harness.agent.AgentLoop
import dev.harness.agent.ApprovalGate
import dev.harness.agent.ApprovalPrompt
import dev.harness.agent.ApprovalResponse
import dev.harness.backend.HarnessAccessibilityService
import dev.harness.backend.ShellBackend
import dev.harness.backend.UiBackend
import dev.harness.llm.ChatMessage
import dev.harness.llm.OpenAiCompatClient
import dev.harness.llm.newSessionId
import dev.harness.tools.HarnessTools
import dev.harness.tools.Risk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns the conversation, streams the agent loop, and mediates approval. */
class ChatViewModel(
    private val settings: SettingsStore,
    private val shell: ShellBackend,
    private val uiBackend: UiBackend,
) : ViewModel() {

    private val conversation = mutableListOf<ChatMessage>()
    private val _messages = mutableStateListOf<UiMessage>()
    val messages: List<UiMessage> get() = _messages

    var input by mutableStateOf("")
    var isRunning by mutableStateOf(false)
        private set
    var pendingApproval by mutableStateOf<PendingApproval?>(null)
        private set
    var status by mutableStateOf(BackendStatus())
        private set

    private val registry by lazy { HarnessTools.registry(shell, uiBackend) }

    /** Shown in the top bar; re-read whenever [refreshStatus] runs. */
    val modelName: String get() = settings.model

    private var runJob: Job? = null
    private var approvalDeferred: CompletableDeferred<ApprovalResponse>? = null

    /** Stable per-conversation id sent as `x-opencode-session`; reset on New. */
    private var sessionId: String = newSessionId()

    private val prompt = ApprovalPrompt { tool, argumentsJson ->
        val deferred = CompletableDeferred<ApprovalResponse>()
        approvalDeferred = deferred
        pendingApproval = PendingApproval(tool.name, argumentsJson, tool.canAlwaysAllow)
        try {
            deferred.await()
        } finally {
            pendingApproval = null
            approvalDeferred = null
        }
    }

    fun respondToApproval(response: ApprovalResponse) {
        approvalDeferred?.complete(response)
    }

    fun refreshStatus() {
        status = BackendStatus(
            shizukuAvailable = shell.available,
            accessibilityEnabled = HarnessAccessibilityService.connected,
            apiKeySet = settings.apiKey.isNotBlank(),
        )
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || isRunning) return

        if (settings.apiKey.isBlank()) {
            _messages += UiMessage(newId(), UiMessage.Kind.ERROR, "Set your API key in Settings first.")
            return
        }

        input = ""
        _messages += UiMessage(newId(), UiMessage.Kind.USER, text)
        conversation += ChatMessage.user(text)

        val client = OpenAiCompatClient(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            sessionId = sessionId,
        )
        val loop = AgentLoop(
            llm = client,
            registry = registry,
            gate = ApprovalGate(prompt, settings.alwaysAllowedTools) { tool ->
                settings.alwaysAllowedTools += tool
            },
        )

        isRunning = true
        runJob = viewModelScope.launch {
            try {
                loop.run(conversation).collect { render(it) }
            } finally {
                isRunning = false
                pendingApproval?.let { respondToApproval(ApprovalResponse.DENY) }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        isRunning = false
    }

    fun newChat() {
        cancel()
        conversation.clear()
        _messages.clear()
        sessionId = newSessionId()
    }

    private fun render(event: AgentEvent) {
        when (event) {
            is AgentEvent.AssistantDelta -> appendAssistantDelta(event.text)

            is AgentEvent.ToolStarted -> {
                finishStreaming()
                _messages += UiMessage(
                    id = event.id,
                    kind = UiMessage.Kind.TOOL,
                    text = "",
                    toolName = event.name,
                    toolArgs = event.argumentsJson,
                    write = registry.find(event.name)?.risk == Risk.WRITE,
                    running = true,
                )
            }

            is AgentEvent.ToolFinished -> {
                val index = _messages.indexOfFirst { it.id == event.id && it.kind == UiMessage.Kind.TOOL }
                if (index >= 0) {
                    _messages[index] = _messages[index].copy(
                        text = event.result,
                        running = false,
                        denied = event.denied,
                    )
                }
            }

            is AgentEvent.Failed -> {
                finishStreaming()
                _messages += UiMessage(newId(), UiMessage.Kind.ERROR, event.message)
            }

            is AgentEvent.Finished -> finishStreaming()
        }
    }

    private fun appendAssistantDelta(delta: String) {
        val last = _messages.lastOrNull()
        if (last != null && last.kind == UiMessage.Kind.ASSISTANT && last.streaming) {
            _messages[_messages.lastIndex] = last.copy(text = last.text + delta)
        } else {
            _messages += UiMessage(newId(), UiMessage.Kind.ASSISTANT, delta, streaming = true)
        }
    }

    private fun finishStreaming() {
        val index = _messages.indexOfLast { it.kind == UiMessage.Kind.ASSISTANT && it.streaming }
        if (index >= 0) _messages[index] = _messages[index].copy(streaming = false)
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        fun factory(app: HarnessApp) = viewModelFactory {
            initializer { ChatViewModel(app.settings, app.shell, app.uiBackend) }
        }
    }
}

/** Snapshot of the permission/configuration prerequisites. */
data class BackendStatus(
    val shizukuAvailable: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val apiKeySet: Boolean = false,
) {
    val allGood: Boolean get() = shizukuAvailable && accessibilityEnabled && apiKeySet
}
