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
    fun `stored grants skip the prompt and new grants are reported once`() = runTest {
        var prompts = 0
        val granted = mutableListOf<String>()
        val gate = ApprovalGate(
            ApprovalPrompt { _, _ -> prompts++; ApprovalResponse.ALWAYS_ALLOW },
            initialAlwaysAllowed = setOf("put_setting"),
            onAlwaysAllow = { granted += it },
        )

        assertTrue(gate.approve(FakeTool("put_setting", Risk.WRITE), ToolCall("c1", "put_setting", "{}")))
        assertEquals(0, prompts)

        val launch = FakeTool("launch_app", Risk.WRITE)
        gate.approve(launch, ToolCall("c2", "launch_app", "{}"))
        gate.approve(launch, ToolCall("c3", "launch_app", "{}"))
        assertEquals(1, prompts)
        assertEquals(listOf("launch_app"), granted)
    }

    @Test
    fun `a stored grant does not apply to a tool that opts out`() = runTest {
        var prompts = 0
        val gate = ApprovalGate(
            ApprovalPrompt { _, _ -> prompts++; ApprovalResponse.DENY },
            initialAlwaysAllowed = setOf("run_shell"),
        )

        assertFalse(gate.approve(FakeTool("run_shell", Risk.WRITE, canAlwaysAllow = false), ToolCall("c", "run_shell", "{}")))
        assertEquals(1, prompts)
    }

    @Test
    fun `deny returns false`() = runTest {
        val gate = ApprovalGate(ApprovalPrompt { _, _ -> ApprovalResponse.DENY })
        assertFalse(gate.approve(FakeTool("put_setting", Risk.WRITE), ToolCall("c", "put_setting", "{}")))
    }
}
