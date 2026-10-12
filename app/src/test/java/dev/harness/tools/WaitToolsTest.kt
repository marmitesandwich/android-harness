package dev.harness.tools

import dev.harness.backend.ShellBackend
import dev.harness.backend.ShellResult
import dev.harness.tools.wait.WaitForLogTool
import dev.harness.tools.wait.WaitTool
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** Returns each scripted stdout in turn, then repeats the last one. */
internal class ScriptedShell(private vararg val outputs: String) : ShellBackend {
    override val available = true
    val commands = mutableListOf<String>()
    override suspend fun exec(command: String): ShellResult {
        commands += command
        val index = minOf(commands.size - 1, outputs.size - 1)
        return ShellResult(0, outputs[index], "")
    }
}

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

class WaitForLogToolTest {

    private fun args(pattern: String, timeout: Int = 10) = buildJsonObject {
        put("pattern", pattern)
        put("timeout_s", timeout)
    }

    @Test
    fun `ignores lines that were already in the log`() = runTest {
        val shell = ScriptedShell("100.000 I A: boot done\n")
        val output = WaitForLogTool(shell).run(args("boot done", timeout = 3))
        assertEquals("TIMEOUT: no new log line matching 'boot done' within 3s", output)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `returns the first new matching line`() = runTest {
        val shell = ScriptedShell(
            "100.000 I A: old\n",
            "100.000 I A: old\n101.500 I B: noise\n",
            "100.000 I A: old\n101.500 I B: noise\n102.250 I App: Download COMPLETE\n",
        )
        val output = WaitForLogTool(shell).run(args("download complete"))
        assertEquals("MATCH: 102.250 I App: Download COMPLETE", output)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `timeout is capped at 60 seconds`() = runTest {
        val output = WaitForLogTool(ScriptedShell("")).run(args("x", timeout = 9999))
        assertEquals("TIMEOUT: no new log line matching 'x' within 60s", output)
        assertEquals(60_000L, currentTime)
    }

    @Test
    fun `passes the filter through quoted`() = runTest {
        val shell = ScriptedShell("")
        WaitForLogTool(shell).run(buildJsonObject {
            put("pattern", "x")
            put("timeout_s", 1)
            put("filter", "Tag:I *:S")
        })
        assertEquals("logcat -d -v epoch -t 2000 'Tag:I *:S'", shell.commands.first())
    }
}
