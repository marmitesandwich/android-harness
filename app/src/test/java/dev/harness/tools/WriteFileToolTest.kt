package dev.harness.tools

import dev.harness.backend.ShellResult
import dev.harness.tools.shell.WriteFileTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteFileToolTest {

    private val silent = ShellResult(0, "", "")

    @Test
    fun `overwrites by default and quotes the content`() = runTest {
        val shell = FakeShell(result = silent)
        val output = WriteFileTool(shell).run(buildJsonObject {
            put("path", "/sdcard/notes.txt")
            put("content", "it's \$HOME")
        })
        assertEquals("printf '%s' 'it'\\''s \$HOME' > '/sdcard/notes.txt'", shell.commands.single())
        assertEquals("OK: wrote 10 chars to /sdcard/notes.txt", output)
    }

    @Test
    fun `append uses a double redirect`() = runTest {
        val shell = FakeShell(result = silent)
        WriteFileTool(shell).run(buildJsonObject {
            put("path", "/sdcard/log.txt")
            put("content", "x")
            put("append", true)
        })
        assertTrue(shell.commands.single().contains(" >> "))
    }

    @Test
    fun `is never remembered and respects the path guard`() = runTest {
        assertFalse(WriteFileTool(FakeShell()).canAlwaysAllow)
        val error = runCatching {
            WriteFileTool(FakeShell()).run(buildJsonObject { put("path", "/system/x"); put("content", "x") })
        }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }
}
