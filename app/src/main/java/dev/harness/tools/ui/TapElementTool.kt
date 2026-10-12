package dev.harness.tools.ui

import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.serialization.json.JsonObject

/** Tap an element found by text/description/id instead of hand-computed coordinates. */
class TapElementTool(private val ui: UiBackend) : Tool {
    override val name = "tap_element"
    override val description =
        "Tap the centre of an on-screen element whose text, content description or id contains the query. " +
            "Use index to pick among several matches (see find_element). Prefer this over tap."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "query" to Params.str("Text, description or id fragment of the element to tap"),
        "index" to Params.int("Which match to tap, 0-based (default 0)"),
        required = listOf("query"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val query = Args.str(args, "query")
        val index = Args.optInt(args, "index", 0)
        val hits = ui.find(query)
        if (hits.isEmpty()) return "ERROR: no element matching '$query'"
        val node = hits.getOrNull(index) ?: return "ERROR: index $index out of range (${hits.size} matches)"
        ui.tap(node.centerX, node.centerY)
        return "OK: tapped ${node.render()}"
    }
}
