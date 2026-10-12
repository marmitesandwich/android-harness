package dev.harness.tools.wait

import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Upper bound for any single wait, so a tool call can never hang a run. */
internal const val MAX_WAIT_SECONDS = 60

/** Plain delay. READ: it touches nothing, so it never needs approval. */
class WaitTool : Tool {
    override val name = "wait"
    override val description =
        "Pause for a number of seconds (1-30) so something can finish, instead of polling. " +
            "Prefer wait_for_log or wait_for_ui when you know what you are waiting for."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "seconds" to Params.int("Seconds to wait (1-30)"),
        required = listOf("seconds"),
    )

    override suspend fun run(args: JsonObject): String {
        val seconds = Args.int(args, "seconds").coerceIn(1, 30)
        delay(seconds * 1000L)
        return "OK: waited ${seconds}s"
    }
}
