package dev.harness.tools

import dev.harness.backend.Screenshot
import dev.harness.backend.UiBackend
import dev.harness.tools.ui.FindElementTool
import dev.harness.tools.ui.parseUiTree
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serves each scripted tree in turn (then repeats the last) and records gestures. */
internal class ScriptedUi(private vararg val trees: String) : UiBackend {
    override val available = true
    var reads = 0
    val taps = mutableListOf<Pair<Int, Int>>()
    val swipes = mutableListOf<List<Int>>()

    override suspend fun windowTree(): String = trees[minOf(reads++, trees.size - 1)]
    override suspend fun screenshot(): Screenshot = Screenshot("/tmp/x.png", 1080, 1920)
    override suspend fun tap(x: Int, y: Int) { taps += x to y }
    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long) {
        swipes += listOf(fromX, fromY, toX, toY)
    }
    override suspend fun typeText(text: String) = Unit
    override suspend fun pressBack() = Unit
    override suspend fun pressHome() = Unit
}

internal val SAMPLE_TREE = """
FrameLayout [0,0-1080,2400]
  LinearLayout [0,100-1080,2400]
    TextView #title text="Wi-Fi" [40,120-400,200]
    Button #save text="Save" desc="Save settings" [800,2200-1040,2320] clickable
    EditText #name text="" [40,300-1040,400] editable focused
    ImageButton desc="Send" [900,2000-1000,2100] clickable
    TextView text="Hidden" [0,0-0,0]
""".trimIndent()

class UiNodesTest {

    @Test
    fun `parses ids text descriptions bounds and flags`() {
        val nodes = parseUiTree(SAMPLE_TREE)
        assertEquals(7, nodes.size)
        val save = nodes.first { it.id == "save" }
        assertEquals("Button", save.cls)
        assertEquals("Save", save.text)
        assertEquals("Save settings", save.desc)
        assertEquals(920 to 2260, save.centerX to save.centerY)
        assertTrue(save.clickable)
        assertTrue(nodes.first { it.id == "name" }.editable)
    }

    @Test
    fun `ignores lines that are not nodes`() {
        assertTrue(parseUiTree("(no active window)").isEmpty())
        assertTrue(parseUiTree("").isEmpty())
    }

    @Test
    fun `matches is case-insensitive over text desc and id`() {
        val nodes = parseUiTree(SAMPLE_TREE)
        assertTrue(nodes.first { it.id == "title" }.matches("wi-fi"))
        assertTrue(nodes.first { it.id == "save" }.matches("SETTINGS"))
        assertFalse(nodes.first { it.id == "title" }.matches("save"))
    }
}

class FindElementToolTest {

    private fun query(q: String) = buildJsonObject { put("query", q) }

    @Test
    fun `lists matches with centres`() = runTest {
        val output = FindElementTool(ScriptedUi(SAMPLE_TREE)).run(query("save"))
        assertEquals(
            "[0] Button #save text=\"Save\" desc=\"Save settings\" center=(920, 2260) clickable",
            output,
        )
    }

    @Test
    fun `skips zero-size nodes`() = runTest {
        assertEquals("no element matching 'hidden'", FindElementTool(ScriptedUi(SAMPLE_TREE)).run(query("hidden")))
    }

    @Test
    fun `reports unavailable accessibility`() = runTest {
        val ui = object : UiBackend by ScriptedUi() { override val available = false }
        assertEquals(
            dev.harness.tools.ui.ACCESSIBILITY_UNAVAILABLE,
            FindElementTool(ui).run(query("x")),
        )
    }
}
