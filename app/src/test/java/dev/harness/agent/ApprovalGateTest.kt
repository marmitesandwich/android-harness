package dev.harness.agent

import dev.harness.llm.ToolCall
import dev.harness.tools.Risk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalGateTest {

    @Test
    fun `read tools never prompt`() = runTest {
        var prompted = false
        val gate = ApprovalGate(ApprovalPrompt { _, _ -> prompted = true; ApprovalResponse.DENY })

        assertTrue(gate.approve(FakeTool("get_setting", Risk.READ), ToolCall("c", "get_setting", "{}")))
        assertFalse(prompted)
    }

    @Test
    fun `always allow is remembered after one prompt`() = runTest {
        var prompts = 0
        val gate = ApprovalGate(ApprovalPrompt { _, _ -> prompts++; ApprovalResponse.ALWAYS_ALLOW })
        val tool = FakeTool("put_setting", Risk.WRITE)

        assertTrue(gate.approve(tool, ToolCall("c1", "put_setting", "{}")))
        assertTrue(gate.approve(tool, ToolCall("c2", "put_setting", "{}")))

        assertEquals(1, prompts)
        assertTrue(gate.isAlwaysAllowed("put_setting"))
    }

    @Test
    fun `tools that opt out are never remembered`() = runTest {
        var prompts = 0
        val gate = ApprovalGate(ApprovalPrompt { _, _ -> prompts++; ApprovalResponse.ALWAYS_ALLOW })
        val tool = FakeTool("run_shell", Risk.WRITE, canAlwaysAllow = false)

        gate.approve(tool, ToolCall("c1", "run_shell", "{}"))
        gate.approve(tool, ToolCall("c2", "run_shell", "{}"))

        assertEquals(2, prompts)
        assertFalse(gate.isAlwaysAllowed("run_shell"))
    }

    @Test
    fun `deny returns false`() = runTest {
        val gate = ApprovalGate(ApprovalPrompt { _, _ -> ApprovalResponse.DENY })
        assertFalse(gate.approve(FakeTool("put_setting", Risk.WRITE), ToolCall("c", "put_setting", "{}")))
    }
}
