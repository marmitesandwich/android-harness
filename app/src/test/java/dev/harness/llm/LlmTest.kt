package dev.harness.llm

import dev.harness.tools.HarnessJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun `extracts payload after data prefix`() {
        assertEquals("{\"a\":1}", SseParser.dataPayload("data: {\"a\":1}"))
    }

    @Test
    fun `handles carriage returns and no space`() {
        assertEquals("hi", SseParser.dataPayload("data:hi\r"))
    }

    @Test
    fun `ignores non-data lines`() {
        assertNull(SseParser.dataPayload(": keep-alive"))
        assertNull(SseParser.dataPayload("event: message"))
        assertNull(SseParser.dataPayload(""))
    }
}

class StreamAccumulatorTest {

    private fun chunk(json: String) = HarnessJson.decodeFromString(ChunkResponse.serializer(), json)

    @Test
    fun `collects text deltas and finish reason`() {
        val accumulator = StreamAccumulator()
        assertEquals("Hel", accumulator.accept(chunk("""{"choices":[{"delta":{"content":"Hel"}}]}""")))
        assertEquals("lo", accumulator.accept(chunk("""{"choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}]}""")))
        assertEquals("stop", accumulator.finishReason)
    }

    @Test
    fun `reassembles tool calls split across chunks`() {
        val accumulator = StreamAccumulator()
        accumulator.accept(
            chunk("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"get_setting","arguments":"{\"na"}}]}}]}"""),
        )
        accumulator.accept(
            chunk("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"me\":\"system\"}"}}]},"finish_reason":"tool_calls"}]}"""),
        )
        val calls = accumulator.toolCalls()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("get_setting", calls[0].name)
        assertEquals("""{"name":"system"}""", calls[0].arguments)
    }

    @Test
    fun `tool call with no arguments defaults to empty object`() {
        val accumulator = StreamAccumulator()
        accumulator.accept(chunk("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"ui_tree"}}]}}]}"""))
        assertEquals("{}", accumulator.toolCalls()[0].arguments)
    }
}

class ChatRequestMappingTest {

    @Test
    fun `maps roles, tool calls and tool results`() {
        val history = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("hi"),
            ChatMessage.assistant(null, listOf(ToolCall("c1", "ui_tree", "{}"))),
            ChatMessage.toolResult("c1", "ui_tree", "tree"),
        )
        val request = buildChatRequest("m", history, emptyList(), null)
        assertEquals("m", request.model)
        assertEquals(listOf("system", "user", "assistant", "tool"), request.messages.map { it.role })
        assertEquals("ui_tree", request.messages[2].toolCalls?.first()?.function?.name)
        assertEquals("c1", request.messages[2].toolCalls?.first()?.id)
        assertEquals("c1", request.messages[3].toolCallId)
        assertNull(request.tools)
    }

    @Test
    fun `resolves chat completions url`() {
        assertEquals("https://x/v1/chat/completions", chatCompletionsUrl("https://x/v1"))
        assertEquals("https://x/v1/chat/completions", chatCompletionsUrl("https://x/v1/"))
        assertEquals("https://x/v1/chat/completions", chatCompletionsUrl("https://x/v1/chat/completions"))
    }
}

class OpenAiCompatClientTest {

    @Test
    fun `api key whitespace is stripped`() {
        assertEquals("sk-abc", sanitizeApiKey(" sk-abc\n"))
        assertEquals("sk-abc", sanitizeApiKey("sk-\nabc"))
        assertEquals("", sanitizeApiKey("  \n\t "))
    }

    @Test
    fun `an unusable base url yields a Failure event instead of throwing`() = runTest {
        // Regression: a bad header or URL used to escape the flow and crash the app.
        val client = OpenAiCompatClient(baseUrl = "not a url", apiKey = "key\n", model = "model")
        val events = client.stream(listOf(ChatMessage.user("hi")), emptyList()).toList()

        assertEquals(1, events.size)
        assertTrue("expected Failure, got ${events.single()}", events.single() is LlmEvent.Failure)
    }

    @Test
    fun `a key containing a newline does not crash header construction`() = runTest {
        val client = OpenAiCompatClient(
            baseUrl = "http://127.0.0.1:1/v1",
            apiKey = "sk-test\n",
            model = "model",
        )
        // Connection is refused, but the point is that it is reported, not thrown.
        val events = client.stream(listOf(ChatMessage.user("hi")), emptyList()).toList()

        assertTrue(events.single() is LlmEvent.Failure)
    }
}
