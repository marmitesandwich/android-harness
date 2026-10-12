package dev.harness.tools.ui

import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

private const val SWIPE_MS = 400L
private const val SETTLE_MS = 500L

/** Swipe until an element is on screen, in one tool call instead of a swipe + ui_tree per step. */
class ScrollUntilTool(private val ui: UiBackend) : Tool {
    override val name = "scroll_until"
    override val description =
        "Scroll the screen (direction down or up) until an element whose text, description or id contains " +
            "the query is visible, or the content stops changing. Does not tap it; follow with tap_element."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "query" to Params.str("Text, description or id fragment to scroll to"),
        "direction" to Params.str("'down' (default, reveals content below) or 'up'"),
        "max_swipes" to Params.int("Maximum swipes (1-20, default 8)"),
        required = listOf("query"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val query = Args.str(args, "query")
        val direction = Args.optStr(args, "direction")?.lowercase() ?: "down"
        require(direction == "down" || direction == "up") { "direction must be 'down' or 'up'" }
        val maxSwipes = Args.optInt(args, "max_swipes", 8).coerceIn(1, 20)

        var previous: String? = null
        var swipes = 0
        while (true) {
            val tree = ui.windowTree()
            val nodes = parseUiTree(tree)
            nodes.firstOrNull { it.visible && it.matches(query) }
                ?.let { return "FOUND after $swipes swipes: ${it.render()}" }
            if (tree == previous) return "NOT FOUND: '$query' (content stopped changing after $swipes swipes)"
            if (swipes >= maxSwipes) return "NOT FOUND: '$query' within $maxSwipes swipes"
            val screen = nodes.firstOrNull() ?: return "ERROR: could not read the screen"

            val height = screen.bottom - screen.top
            val near = screen.top + height * 3 / 4
            val far = screen.top + height / 4
            val (from, to) = if (direction == "down") near to far else far to near
            ui.swipe(screen.centerX, from, screen.centerX, to, SWIPE_MS)
            swipes++
            previous = tree
            delay(SETTLE_MS)
        }
    }
}
