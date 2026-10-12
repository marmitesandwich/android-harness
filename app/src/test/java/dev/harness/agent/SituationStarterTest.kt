package dev.harness.agent

import dev.harness.backend.ShellBackend
import dev.harness.backend.ShellResult
import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmClient
import dev.harness.llm.LlmEvent
import dev.harness.llm.Role
import dev.harness.tools.ToolSchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class StatusShell(
    override val available: Boolean = true,
    private val stdout: String = "## network\nActive default network: none\n## battery\nlevel: 80",
) : ShellBackend {
    var calls = 0
    override suspend fun exec(command: String): ShellResult { calls++; return ShellResult(0, stdout, "") }
}

private class ReplyLlm(private val events: List<LlmEvent>, private val delayMs: Long = 0) : LlmClient {
    var requests = 0
    var lastHistory: List<ChatMessage> = emptyList()
    override fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent> = flow {
        requests++
        lastHistory = history
        if (delayMs > 0) delay(delayMs)
        events.forEach { emit(it) }
    }
}

private fun reply(text: String) = ReplyLlm(listOf(LlmEvent.TextDelta(text), LlmEvent.Completed("stop")))

class CleanSuggestionTest {

    @Test
    fun `keeps a short question and drops quotes and bullets`() {
        assertEquals("Why isn't my Wi-Fi working?", cleanSuggestion("\"Why isn't my Wi-Fi working?\""))
        assertEquals("Is my storage nearly full?", cleanSuggestion("- Is my storage nearly full?"))
    }

    @Test
    fun `takes only the first line`() {
        assertEquals("Why is my battery low?", cleanSuggestion("\nWhy is my battery low?\nBecause the screen is on."))
    }

    @Test
    fun `rejects empty, tiny and rambling replies`() {
        assertNull(cleanSuggestion("   "))
        assertNull(cleanSuggestion("Hi"))
        assertNull(cleanSuggestion("x".repeat(200)))
    }
}

class SituationStarterTest {

    @Test
    fun `asks the model about the status and returns a clean suggestion`() = runTest {
        val llm = reply("Why isn't my Wi-Fi working?")
        val starter = SituationStarter(StatusShell(), { llm })
        assertEquals("Why isn't my Wi-Fi working?", starter.suggest())
        assertEquals(Role.SYSTEM, llm.lastHistory.first().role)
        assertTrue(llm.lastHistory.last().content.orEmpty().contains("Active default network: none"))
    }

    @Test
    fun `a success is reused until it expires`() = runTest {
        val llm = reply("Is my storage nearly full?")
        var clock = 0L
        val starter = SituationStarter(StatusShell(), { llm }, now = { clock }, successTtlMs = 1_000)
        starter.suggest()
        clock = 500
        starter.suggest()
        assertEquals(1, llm.requests)
        clock = 1_500
        starter.suggest()
        assertEquals(2, llm.requests)
    }

    @Test
    fun `a failure is retried soon but not on every call`() = runTest {
        val llm = ReplyLlm(listOf(LlmEvent.Failure("boom")))
        var clock = 0L
        val starter = SituationStarter(StatusShell(), { llm }, now = { clock }, failureTtlMs = 60_000)
        assertNull(starter.suggest())
        clock = 10_000
        assertNull(starter.suggest())
        assertEquals(1, llm.requests)
        clock = 61_000
        starter.suggest()
        assertEquals(2, llm.requests)
    }

    @Test
    fun `without shell access or an api key nothing is sent`() = runTest {
        val llm = reply("Why is my battery low?")
        assertNull(SituationStarter(StatusShell(available = false), { llm }).suggest())
        assertNull(SituationStarter(StatusShell(), { null }).suggest())
        assertEquals(0, llm.requests)
    }

    @Test
    fun `an empty status is not worth a model call`() = runTest {
        val llm = reply("Why is my battery low?")
        assertNull(SituationStarter(StatusShell(stdout = "  "), { llm }).suggest())
        assertEquals(0, llm.requests)
    }

    @Test
    fun `a slow model times out instead of hanging`() = runTest {
        val llm = ReplyLlm(listOf(LlmEvent.TextDelta("Is my phone slow?")), delayMs = 60_000)
        val starter = SituationStarter(StatusShell(), { llm }, timeoutMs = 5_000)
        assertNull(starter.suggest())
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun `an exception from the client becomes null`() = runTest {
        val broken = object : LlmClient {
            override fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent> =
                flow { error("network down") }
        }
        assertNull(SituationStarter(StatusShell(), { broken }).suggest())
    }
}
