package dev.harness.agent

import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmEvent
import dev.harness.llm.Role
import dev.harness.llm.ToolCall
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import dev.harness.tools.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {

    @Test
    fun `plain reply finishes without tools`() = runTest {
        val llm = FakeLlmClient(listOf(listOf(LlmEvent.TextDelta("hi"), LlmEvent.Completed("stop"))))
        val loop = AgentLoop(llm, ToolRegistry(listOf(FakeTool("t", Risk.READ))), gateRespondingWith(ApprovalResponse.DENY))

        val conversation = mutableListOf(ChatMessage.user("hello"))
        val events = loop.run(conversation).toList()

        assertTrue(events.any { it is AgentEvent.AssistantDelta && it.text == "hi" })
        assertEquals(FinishReason.COMPLETED, (events.last() as AgentEvent.Finished).reason)
        assertEquals(Role.SYSTEM, conversation.first().role)
        assertEquals(1, llm.requests.size)
    }

    @Test
    fun `read tool runs and result is appended`() = runTest {
        val tool = FakeTool("get_setting", Risk.READ, result = "60000")
        val llm = FakeLlmClient(
            listOf(
                listOf(
                    LlmEvent.ToolCallsReady(listOf(ToolCall("c1", "get_setting", """{"namespace":"system"}"""))),
                    LlmEvent.Completed("tool_calls"),
                ),
                listOf(LlmEvent.TextDelta("done"), LlmEvent.Completed("stop")),
            ),
        )
        val loop = AgentLoop(llm, ToolRegistry(listOf(tool)), gateRespondingWith(ApprovalResponse.DENY))

        val conversation = mutableListOf(ChatMessage.user("timeout?"))
        val events = loop.run(conversation).toList()

        assertEquals(1, tool.invocations)
        assertEquals("system", tool.lastArgs?.get("namespace")?.let { it.toString().trim('"') })
        assertTrue(events.any { it is AgentEvent.ToolFinished && it.result == "60000" })
        assertEquals(2, llm.requests.size)
        assertEquals("60000", conversation.first { it.role == Role.TOOL }.content)
    }

    @Test
    fun `denied write tool is not run`() = runTest {
        val tool = FakeTool("put_setting", Risk.WRITE)
        val llm = FakeLlmClient(
            listOf(
                listOf(
                    LlmEvent.ToolCallsReady(listOf(ToolCall("c1", "put_setting", "{}"))),
                    LlmEvent.Completed("tool_calls"),
                ),
                listOf(LlmEvent.TextDelta("ok"), LlmEvent.Completed("stop")),
            ),
        )
        val loop = AgentLoop(llm, ToolRegistry(listOf(tool)), gateRespondingWith(ApprovalResponse.DENY))

        val events = loop.run(mutableListOf(ChatMessage.user("go"))).toList()

        assertEquals(0, tool.invocations)
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(finished.denied)
        assertEquals("ERROR: user denied", finished.result)
    }

    @Test
    fun `unknown tool becomes an error result`() = runTest {
        val llm = FakeLlmClient(
            listOf(
                listOf(
                    LlmEvent.ToolCallsReady(listOf(ToolCall("c1", "nope", "{}"))),
                    LlmEvent.Completed("tool_calls"),
                ),
                listOf(LlmEvent.TextDelta("done"), LlmEvent.Completed("stop")),
            ),
        )
        val loop = AgentLoop(llm, ToolRegistry(listOf(FakeTool("t", Risk.READ))), gateRespondingWith(ApprovalResponse.ALLOW_ONCE))

        val events = loop.run(mutableListOf(ChatMessage.user("x"))).toList()

        assertTrue(events.any { it is AgentEvent.ToolFinished && it.result.contains("unknown tool") })
    }

    @Test
    fun `throwing tool becomes an ERROR result and loop continues`() = runTest {
        val exploding = object : Tool {
            override val name = "boom"
            override val description = "d"
            override val risk = Risk.READ
            override val jsonSchema = dev.harness.tools.Params.obj()
            override suspend fun run(args: JsonObject): String = throw IllegalStateException("kaboom")
        }
        val llm = FakeLlmClient(
            listOf(
                listOf(LlmEvent.ToolCallsReady(listOf(ToolCall("c1", "boom", "{}"))), LlmEvent.Completed("tool_calls")),
                listOf(LlmEvent.TextDelta("recovered"), LlmEvent.Completed("stop")),
            ),
        )
        val loop = AgentLoop(llm, ToolRegistry(listOf(exploding)), gateRespondingWith(ApprovalResponse.ALLOW_ONCE))

        val events = loop.run(mutableListOf(ChatMessage.user("x"))).toList()

        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(finished.result.startsWith("ERROR:"))
        assertEquals(FinishReason.COMPLETED, (events.last() as AgentEvent.Finished).reason)
    }

    @Test
    fun `stops at max steps`() = runTest {
        val tool = FakeTool("t", Risk.READ)
        val turn = listOf(LlmEvent.ToolCallsReady(listOf(ToolCall("c", "t", "{}"))), LlmEvent.Completed("tool_calls"))
        val llm = FakeLlmClient(List(10) { turn })
        val loop = AgentLoop(llm, ToolRegistry(listOf(tool)), gateRespondingWith(ApprovalResponse.ALLOW_ONCE), maxSteps = 3)

        val events = loop.run(mutableListOf(ChatMessage.user("x"))).toList()

        assertEquals(FinishReason.MAX_STEPS, (events.last() as AgentEvent.Finished).reason)
        assertEquals(listOf(1, 2, 3), events.filterIsInstance<AgentEvent.Step>().map { it.n })
        assertEquals(3, tool.invocations)
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun `llm failure stops the loop`() = runTest {
        val llm = FakeLlmClient(listOf(listOf(LlmEvent.Failure("HTTP 401 Unauthorized"))))
        val loop = AgentLoop(llm, ToolRegistry(emptyList()), gateRespondingWith(ApprovalResponse.DENY))

        val events = loop.run(mutableListOf(ChatMessage.user("x"))).toList()

        assertTrue(events.any { it is AgentEvent.Failed && it.message.contains("401") })
        assertEquals(FinishReason.ERROR, (events.last() as AgentEvent.Finished).reason)
    }
}
