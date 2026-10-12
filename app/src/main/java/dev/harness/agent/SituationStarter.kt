package dev.harness.agent

import dev.harness.backend.ShellBackend
import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmClient
import dev.harness.llm.LlmEvent
import dev.harness.tools.shell.DeviceStatusTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_STATUS_CHARS = 1_500
private const val MAX_SUGGESTION_CHARS = 80

private const val SYSTEM = """You write one suggested question for the owner of an Android phone to ask their assistant.
You are given a short status summary of the phone. Pick the single most useful question for the situation:
if something looks wrong (no network, low battery, nearly full storage, airplane mode, screen off), ask about that;
otherwise suggest a helpful routine check.
Rules: plain words a non-technical person would say, first person ("Why...", "Can you..."), under 9 words,
no quotes, no explanation. Reply with the question only."""

/** The two messages that ask the model for a suggestion about [status]. */
internal fun situationMessages(status: String): List<ChatMessage> = listOf(
    ChatMessage.system(SYSTEM),
    ChatMessage.user("Phone status:\n${status.take(MAX_STATUS_CHARS)}"),
)

/** Reduces a model reply to one short line fit for a tappable example, or null if it is unusable. */
internal fun cleanSuggestion(raw: String): String? {
    val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val text = line.trimStart('-', '*', '•', ' ').trim().trim('"', '\'', '`', '“', '”').trim()
    return text.takeIf { it.length in 6..MAX_SUGGESTION_CHARS }
}

/**
 * One example prompt tuned to the phone's current state, written by the model from a coarse status
 * summary (battery, network, storage, screen). Nothing personal is sent: no notifications, contacts or
 * screen text. Any failure returns null so the caller falls back to the fixed examples.
 */
class SituationStarter(
    private val shell: ShellBackend,
    /** A client for the current settings, or null when no API key is set. */
    private val llm: () -> LlmClient?,
    private val now: () -> Long = System::currentTimeMillis,
    private val successTtlMs: Long = 10 * 60_000,
    private val failureTtlMs: Long = 60_000,
    private val timeoutMs: Long = 10_000,
) {
    private var cachedAt = 0L
    private var cachedTtl = 0L
    private var cached: String? = null
    private var hasCache = false

    suspend fun suggest(): String? {
        if (hasCache && now() - cachedAt < cachedTtl) return cached
        val suggestion = try {
            withTimeoutOrNull(timeoutMs) { fetch() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        cached = suggestion
        cachedAt = now()
        cachedTtl = if (suggestion != null) successTtlMs else failureTtlMs
        hasCache = true
        return suggestion
    }

    private suspend fun fetch(): String? {
        if (!shell.available) return null
        val status = shell.exec(DeviceStatusTool.COMMAND).stdout.trim()
        if (status.isEmpty()) return null
        val client = llm() ?: return null

        val reply = StringBuilder()
        var failed = false
        client.stream(situationMessages(status), emptyList()).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> reply.append(event.text)
                is LlmEvent.Failure -> failed = true
                else -> Unit
            }
        }
        return if (failed) null else cleanSuggestion(reply.toString())
    }
}
