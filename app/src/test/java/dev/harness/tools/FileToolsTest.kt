package dev.harness.tools

import dev.harness.tools.shell.ListFilesTool
import dev.harness.tools.shell.ReadFileTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileToolsTest {

    @Test
    fun `read_file caps bytes and quotes the path`() = runTest {
        val shell = FakeShell()
        ReadFileTool(shell).run(buildJsonObject {
            put("path", "/sdcard/Download/a b.txt")
            put("max_bytes", 999_999)
        })
        assertEquals("head -c 16000 '/sdcard/Download/a b.txt'", shell.commands.single())
    }

    @Test
    fun `list_files defaults to the sdcard`() = runTest {
        val shell = FakeShell()
        ListFilesTool(shell).run(buildJsonObject {})
        assertEquals("ls -la '/sdcard/' | head -n 200", shell.commands.single())
    }

    @Test
    fun `paths outside shared storage are rejected`() = runTest {
        listOf("/data/data/com.x/files/db", "/system/build.prop", "relative.txt").forEach { path ->
            val error = runCatching { ReadFileTool(FakeShell()).run(buildJsonObject { put("path", path) }) }
            assertTrue(path, error.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `traversal out of an allowed root is rejected`() = runTest {
        val shell = FakeShell()
        val error = runCatching {
            ReadFileTool(shell).run(buildJsonObject { put("path", "/sdcard/../data/data/com.x/db") })
        }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
        assertTrue(shell.commands.isEmpty())
    }
}
