package dev.harness.tools

import dev.harness.backend.ClipboardBackend
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

internal class InMemoryClipboard(var text: String? = null) : ClipboardBackend {
    override fun get() = text
    override fun set(text: String) { this.text = text }
}

class ClipboardToolsTest {

    @Test
    fun `set then get round-trips`() = runTest {
        val clipboard = InMemoryClipboard()
        assertEquals("OK: clipboard set (5 chars)", ClipboardSetTool(clipboard).run(buildJsonObject { put("text", "hello") }))
        assertEquals("hello", ClipboardGetTool(clipboard).run(buildJsonObject {}))
    }

    @Test
    fun `an empty or hidden clipboard is reported`() = runTest {
        val message = "(clipboard is empty or unavailable)"
        assertEquals(message, ClipboardGetTool(InMemoryClipboard(null)).run(buildJsonObject {}))
        assertEquals(message, ClipboardGetTool(InMemoryClipboard("")).run(buildJsonObject {}))
    }

    @Test
    fun `both directions need approval`() {
        val clipboard = InMemoryClipboard()
        assertEquals(Risk.WRITE, ClipboardGetTool(clipboard).risk)
        assertEquals(Risk.WRITE, ClipboardSetTool(clipboard).risk)
    }
}
