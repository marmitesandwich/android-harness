package dev.harness.tools

import dev.harness.tools.ui.ScrollUntilTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollUntilToolTest {

    private val screen = "Frame [0,0-1000,2000]"

    private fun args(q: String, max: Int = 5) = buildJsonObject {
        put("query", q)
        put("max_swipes", max)
    }

    @Test
    fun `swipes up the screen until the target shows`() = runTest {
        val ui = ScriptedUi(
            screen,
            "$screen\nText text=\"A\" [0,0-10,10]",
            "$screen\nText text=\"Target\" [0,100-500,200]",
        )
        val output = ScrollUntilTool(ui).run(args("target"))
        assertTrue(output, output.startsWith("FOUND after 2 swipes"))
        assertEquals(listOf(500, 1500, 500, 500), ui.swipes.first())
        assertEquals(2, ui.swipes.size)
    }

    @Test
    fun `scrolling up reverses the swipe`() = runTest {
        val ui = ScriptedUi(screen, "$screen\nText text=\"Target\" [0,100-500,200]")
        ScrollUntilTool(ui).run(buildJsonObject { put("query", "target"); put("direction", "up") })
        assertEquals(listOf(500, 500, 500, 1500), ui.swipes.single())
    }

    @Test
    fun `gives up when content stops changing`() = runTest {
        val output = ScrollUntilTool(ScriptedUi(screen)).run(args("target", max = 10))
        assertEquals("NOT FOUND: 'target' (content stopped changing after 1 swipes)", output)
    }
}
