package dev.harness.tools

import dev.harness.tools.wait.WaitTool
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitToolTest {

    @Test
    fun `wait sleeps for the requested seconds`() = runTest {
        val output = WaitTool().run(buildJsonObject { put("seconds", 5) })
        assertEquals("OK: waited 5s", output)
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun `wait is capped at 30 seconds`() = runTest {
        val output = WaitTool().run(buildJsonObject { put("seconds", 999) })
        assertEquals("OK: waited 30s", output)
        assertEquals(30_000L, currentTime)
    }
}
