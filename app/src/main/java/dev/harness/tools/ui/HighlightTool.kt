package dev.harness.tools.ui

import dev.harness.backend.OverlayBackend
import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.serialization.json.JsonObject

/**
 * Point at something on screen: an orange outline plus a short label, drawn over whatever app is showing.
 * READ-tier because it only draws; it cannot tap, type or change anything.
 */
class HighlightTool(private val ui: UiBackend, private val overlay: OverlayBackend) : Tool {
    override val name = "highlight"
    override val description =
        "Show the user where to tap: outline an on-screen element (found by text, description or id) with a " +
            "short label such as 'Tap here'. It stays for a while and clears when the screen changes. " +
            "Use it when guiding someone through a task; it does not tap anything."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "query" to Params.str("Text, description or id fragment of the element to point at"),
        "label" to Params.str("Short instruction shown next to it, e.g. 'Tap Wi-Fi' (default 'Tap here')"),
        "index" to Params.int("Which match to highlight, 0-based (default 0)"),
        "seconds" to Params.int("How long to keep it (3-60, default 20)"),
        required = listOf("query"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val query = Args.str(args, "query")
        val index = Args.optInt(args, "index", 0)
        val hits = ui.find(query)
        if (hits.isEmpty()) return "ERROR: no element matching '$query'"
        val node = hits.getOrNull(index) ?: return "ERROR: index $index out of range (${hits.size} matches)"
        val label = Args.optStr(args, "label")?.takeIf { it.isNotBlank() } ?: "Tap here"
        val seconds = Args.optInt(args, "seconds", 20).coerceIn(3, 60)
        overlay.highlight(node.left, node.top, node.right, node.bottom, label, seconds)
        return "OK: highlighting ${node.render()} with '$label' for ${seconds}s"
    }
}
