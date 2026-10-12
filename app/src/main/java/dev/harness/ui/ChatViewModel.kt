package dev.harness.ui

import android.util.Log
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
import dev.harness.agent.DEFAULT_MAX_STEPS
import dev.harness.agent.FinishReason
import dev.harness.agent.SituationStarter
import dev.harness.agent.ApprovalPrompt
import dev.harness.agent.ApprovalResponse
import dev.harness.backend.ClipboardBackend
import dev.harness.backend.HarnessAccessibilityService
import dev.harness.backend.HttpFetcher
import dev.harness.backend.ApprovalCard
import dev.harness.backend.NotesStore
import dev.harness.backend.OverlayBackend
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
    private val overlay: OverlayBackend,
    private val notes: NotesStore,
    private val clipboard: ClipboardBackend,
    private val http: HttpFetcher,
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

    private val registry by lazy { HarnessTools.registry(shell, uiBackend, overlay, notes, clipboard, http) }

    /** Past prompts to mix into the empty-state examples. */
    val promptHistory: List<PastPrompt> get() = settings.promptHistory

    private val situation = SituationStarter(shell, llm = {
        if (settings.apiKey.isBlank()) null
        else OpenAiCompatClient(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            sessionId = newSessionId(),
        )
    })

    /** One example prompt written for the phone's current state; null until the model answers, or if it cannot. */
    var tunedStarter by mutableStateOf<String?>(null)
        private set

    fun refreshTunedStarter() {
        viewModelScope.launch { tunedStarter = situation.suggest() }
    }

    /** Shown in the top bar; re-read whenever [refreshStatus] runs. */
    val modelName: String get() = settings.model

    private var runJob: Job? = null
    private var approvalDeferred: CompletableDeferred<ApprovalResponse>? = null

    /** Stable per-conversation id sent as `x-opencode-session`; reset on New. */
    private var sessionId: String = newSessionId()

    private val prompt = ApprovalPrompt { tool, argumentsJson ->
        val deferred = CompletableDeferred<ApprovalResponse>()
        approvalDeferred = deferred
        val pending = PendingApproval(tool.name, argumentsJson, tool.canAlwaysAllow)
        pendingApproval = pending
        // If the agent has taken the user to another app, Harness's own sheet is out of sight.
        if (!appVisible) showApprovalOverlay(pending)
        try {
            deferred.await()
        } finally {
            overlay.dismissApproval()
            pendingApproval = null
            approvalDeferred = null
        }
    }

    /** Set by the activity. While Harness is out of sight, approvals are drawn over the current app instead. */
    private var appVisible = true

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) overlay.dismissApproval() else pendingApproval?.let(::showApprovalOverlay)
    }

    private fun showApprovalOverlay(pending: PendingApproval) {
        if (!overlay.available) return
        val card = ApprovalCard(actionTitle(pending.toolName), approvalSummary(pending.toolName, pending.argumentsJson), pending.canAlwaysAllow)
        overlay.showApproval(card) { respondToApproval(it) }
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
        settings.promptHistory = addToHistory(settings.promptHistory, text)
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

            is AgentEvent.Step -> {
                Log.i(TAG, "step ${event.n}/${event.max}")
                // Warn once, a fifth of the budget before the cap.
                if (event.n == event.max - event.max / 5) {
                    _messages += UiMessage(newId(), UiMessage.Kind.ERROR, "${event.max - event.n} steps left before this run stops.")
                }
            }

            is AgentEvent.Finished -> {
                Log.i(TAG, "finished ${event.reason}")
                finishStreaming()
                // A silent stop looks like a hang; say why the loop ended.
                val note = when {
                    event.reason == FinishReason.MAX_STEPS -> "Stopped after $DEFAULT_MAX_STEPS steps. Send a message to continue."
                    event.reason == FinishReason.COMPLETED && _messages.lastOrNull()?.kind == UiMessage.Kind.TOOL ->
                        "The model replied with nothing after the last tool. Send a message to continue."
                    else -> null
                }
                note?.let { _messages += UiMessage(newId(), UiMessage.Kind.ERROR, it) }
            }
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
        private const val TAG = "Harness"

        fun factory(app: HarnessApp) = viewModelFactory {
            initializer { ChatViewModel(app.settings, app.shell, app.uiBackend, app.overlay, app.notes, app.clipboard, app.http) }
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
