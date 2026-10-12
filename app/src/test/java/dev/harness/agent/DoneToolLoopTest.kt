package dev.harness.agent

import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmEvent
import dev.harness.llm.ToolCall
import dev.harness.tools.DoneTool
import dev.harness.tools.Risk
import dev.harness.tools.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoneToolLoopTest {

    private fun doneCall(summary: String) = listOf(
        LlmEvent.ToolCallsReady(listOf(ToolCall("d1", "done", """{"summary":"$summary"}"""))),
        LlmEvent.Completed("tool_calls"),
    )

    @Test
    fun `done ends the run and shows the summary as the reply`() = runTest {
        val llm = FakeLlmClient(listOf(doneCall("All set"), listOf(LlmEvent.TextDelta("never reached"))))
        val loop = AgentLoop(llm, ToolRegistry(listOf(DoneTool())), gateRespondingWith(ApprovalResponse.DENY))

        val events = loop.run(mutableListOf(ChatMessage.user("go"))).toList()

        assertEquals(1, llm.requests.size)
        assertTrue(events.any { it is AgentEvent.AssistantDelta && it.text == "All set" })
        assertEquals(FinishReason.COMPLETED, (events.last() as AgentEvent.Finished).reason)
    }

    @Test
    fun `other tools in the same turn still run before the loop stops`() = runTest {
        val other = FakeTool("get_setting", Risk.READ)
        val llm = FakeLlmClient(
            listOf(
                listOf(
                    LlmEvent.ToolCallsReady(
                        listOf(ToolCall("a", "get_setting", "{}"), ToolCall("d", "done", """{"summary":"ok"}""")),
                    ),
                    LlmEvent.Completed("tool_calls"),
                ),
            ),
        )
        val loop = AgentLoop(llm, ToolRegistry(listOf(other, DoneTool())), gateRespondingWith(ApprovalResponse.DENY))

        loop.run(mutableListOf(ChatMessage.user("go"))).toList()

        assertEquals(1, other.invocations)
        assertEquals(1, llm.requests.size)
    }

    @Test
    fun `done is a read tool so it never prompts`() {
        assertEquals(Risk.READ, DoneTool().risk)
    }
}
