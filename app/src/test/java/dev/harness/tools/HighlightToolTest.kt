package dev.harness.tools

import dev.harness.agent.ApprovalResponse
import dev.harness.backend.ApprovalCard
import dev.harness.backend.OverlayBackend
import dev.harness.tools.ui.HighlightTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeOverlay(override val available: Boolean = true) : OverlayBackend {
    data class Highlight(val rect: List<Int>, val label: String?, val seconds: Int)

    val highlights = mutableListOf<Highlight>()
    var shown: ApprovalCard? = null
    var respond: ((ApprovalResponse) -> Unit)? = null

    override suspend fun highlight(left: Int, top: Int, right: Int, bottom: Int, label: String?, seconds: Int) {
        highlights += Highlight(listOf(left, top, right, bottom), label, seconds)
    }

    override fun showApproval(card: ApprovalCard, onResponse: (ApprovalResponse) -> Unit) {
        shown = card
        respond = onResponse
    }

    override fun dismissApproval() { shown = null }
}

class HighlightToolTest {

    private fun args(q: String, label: String? = null, seconds: Int? = null, index: Int? = null) = buildJsonObject {
        put("query", q)
        if (label != null) put("label", label)
        if (seconds != null) put("seconds", seconds)
        if (index != null) put("index", index)
    }

    @Test
    fun `outlines the element with the label`() = runTest {
        val overlay = FakeOverlay()
        val output = HighlightTool(ScriptedUi(SAMPLE_TREE), overlay).run(args("save", label = "Tap Save", seconds = 30))
        assertEquals(listOf(FakeOverlay.Highlight(listOf(800, 2200, 1040, 2320), "Tap Save", 30)), overlay.highlights)
        assertTrue(output, output.startsWith("OK: highlighting Button #save"))
    }

    @Test
    fun `defaults the label and caps the duration`() = runTest {
        val overlay = FakeOverlay()
        HighlightTool(ScriptedUi(SAMPLE_TREE), overlay).run(args("save", seconds = 9999))
        assertEquals("Tap here", overlay.highlights.single().label)
        assertEquals(60, overlay.highlights.single().seconds)
    }

    @Test
    fun `a missing element draws nothing`() = runTest {
        val overlay = FakeOverlay()
        assertEquals("ERROR: no element matching 'nope'", HighlightTool(ScriptedUi(SAMPLE_TREE), overlay).run(args("nope")))
        assertEquals("ERROR: index 4 out of range (1 matches)", HighlightTool(ScriptedUi(SAMPLE_TREE), overlay).run(args("save", index = 4)))
        assertTrue(overlay.highlights.isEmpty())
    }

    @Test
    fun `it only draws, so it never needs approval`() {
        assertEquals(Risk.READ, HighlightTool(ScriptedUi(), FakeOverlay()).risk)
    }
}
