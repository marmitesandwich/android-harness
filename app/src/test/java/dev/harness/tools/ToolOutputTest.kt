package dev.harness.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputTest {

    @Test
    fun `short output is unchanged`() {
        assertEquals("hello", truncateOutput("hello", 1024))
    }

    @Test
    fun `long output is truncated and reports dropped bytes`() {
        val text = "a".repeat(100)
        val result = truncateOutput(text, 10)
        assertTrue(result.startsWith("a".repeat(10)))
        assertTrue(result.contains("truncated 90 bytes"))
    }

    @Test
    fun `truncation does not split a multibyte character`() {
        // Each emoji is 4 UTF-8 bytes; a 6-byte budget must keep only the first.
        val result = truncateOutput("😀😀", 6)
        assertTrue(result.startsWith("😀"))
        assertTrue(result.contains("truncated"))
    }

    @Test
    fun `non-positive budget yields empty`() {
        assertEquals("", truncateOutput("data", 0))
    }
}

class ToolRegistryTest {

    private fun tool(name: String) = object : Tool {
        override val name = name
        override val description = "d"
        override val jsonSchema = buildJsonObject { put("type", "object") }
        override val risk = Risk.READ
        override suspend fun run(args: kotlinx.serialization.json.JsonObject) = "ok"
    }

    @Test
    fun `find returns registered tool`() {
        val registry = ToolRegistry(listOf(tool("a"), tool("b")))
        assertEquals("a", registry.find("a")?.name)
        assertNull(registry.find("missing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate names are rejected`() {
        ToolRegistry(listOf(tool("a"), tool("a")))
    }

    @Test
    fun `schemas preserve declaration order`() {
        val registry = ToolRegistry(listOf(tool("a"), tool("b")))
        assertEquals(listOf("a", "b"), registry.schemas().map { it.name })
    }
}
