package dev.harness.tools

import dev.harness.tools.wait.WaitForUiTool
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

private const val EMPTY_SCREEN = "Frame [0,0-10,10]"

class WaitForUiToolTest {

    private fun args(q: String, state: String? = null, timeout: Int = 10) = buildJsonObject {
        put("query", q)
        if (state != null) put("state", state)
        put("timeout_s", timeout)
    }

    @Test
    fun `returns once the element appears`() = runTest {
        val ui = ScriptedUi(EMPTY_SCREEN, EMPTY_SCREEN, SAMPLE_TREE)
        assertEquals("OK: 'save' is on screen after 2s", WaitForUiTool(ui).run(args("save")))
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `waits for an element to go away`() = runTest {
        val ui = ScriptedUi(SAMPLE_TREE, EMPTY_SCREEN)
        assertEquals("OK: 'save' is gone after 1s", WaitForUiTool(ui).run(args("save", state = "gone")))
    }

    @Test
    fun `times out when nothing changes`() = runTest {
        val output = WaitForUiTool(ScriptedUi(EMPTY_SCREEN)).run(args("save", timeout = 3))
        assertEquals("TIMEOUT: 'save' did not become 'appears' within 3s", output)
        assertEquals(3_000L, currentTime)
    }
}

