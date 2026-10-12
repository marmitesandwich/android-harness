package dev.harness.tools.wait

import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import dev.harness.tools.ui.find
import dev.harness.tools.ui.guard
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

private const val POLL_SECONDS = 1

/** Blocks until an element appears on (or disappears from) the screen. */
class WaitForUiTool(private val ui: UiBackend) : Tool {
    override val name = "wait_for_ui"
    override val description =
        "Wait until an element whose text, description or id contains the query appears (default) or " +
            "disappears (state=gone) from the screen. Returns when the condition holds, or on timeout."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "query" to Params.str("Text, description or id fragment to watch for"),
        "state" to Params.str("'appears' (default) or 'gone'"),
        "timeout_s" to Params.int("Give up after this many seconds (1-$MAX_WAIT_SECONDS, default 30)"),
        required = listOf("query"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val query = Args.str(args, "query")
        val state = Args.optStr(args, "state")?.lowercase() ?: "appears"
        require(state == "appears" || state == "gone") { "state must be 'appears' or 'gone'" }
        val timeout = Args.optInt(args, "timeout_s", 30).coerceIn(1, MAX_WAIT_SECONDS)

        var waited = 0
        while (true) {
            val present = ui.find(query).isNotEmpty()
            if (present == (state == "appears")) {
                return "OK: '$query' ${if (present) "is on screen" else "is gone"} after ${waited}s"
            }
            if (waited >= timeout) return "TIMEOUT: '$query' did not become '$state' within ${timeout}s"
            delay(POLL_SECONDS * 1000L)
            waited += POLL_SECONDS
        }
    }
}
