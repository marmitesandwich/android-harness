package dev.harness.tools.ui

import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.serialization.json.JsonObject

private const val MAX_RESULTS = 15

/** Search the screen for matching nodes without dumping the whole tree. */
class FindElementTool(private val ui: UiBackend) : Tool {
    override val name = "find_element"
    override val description =
        "Find on-screen elements whose text, content description or id contains the query " +
            "(case-insensitive). Returns up to $MAX_RESULTS matches with tap coordinates. Cheaper than ui_tree."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "query" to Params.str("Text, description or id fragment to look for"),
        required = listOf("query"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val query = Args.str(args, "query")
        val hits = ui.find(query)
        if (hits.isEmpty()) return "no element matching '$query'"
        val shown = hits.take(MAX_RESULTS)
        return shown.mapIndexed { i, n -> "[$i] ${n.render()}" }.joinToString("\n") +
            if (hits.size > shown.size) "\n(${hits.size - shown.size} more)" else ""
    }
}
