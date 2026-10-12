package dev.harness.tools

import dev.harness.tools.ui.TapElementTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapElementToolTest {

    private fun args(q: String, index: Int? = null) = buildJsonObject {
        put("query", q)
        if (index != null) put("index", index)
    }

    @Test
    fun `taps the centre of the match`() = runTest {
        val ui = ScriptedUi(SAMPLE_TREE)
        val output = TapElementTool(ui).run(args("save"))
        assertEquals(listOf(920 to 2260), ui.taps)
        assertTrue(output.startsWith("OK: tapped Button #save"))
    }

    @Test
    fun `rejects an index past the matches`() = runTest {
        val ui = ScriptedUi(SAMPLE_TREE)
        assertEquals("ERROR: index 3 out of range (1 matches)", TapElementTool(ui).run(args("save", index = 3)))
        assertTrue(ui.taps.isEmpty())
    }

    @Test
    fun `reports a missing element without tapping`() = runTest {
        val ui = ScriptedUi(SAMPLE_TREE)
        assertEquals("ERROR: no element matching 'nope'", TapElementTool(ui).run(args("nope")))
        assertTrue(ui.taps.isEmpty())
    }
}

