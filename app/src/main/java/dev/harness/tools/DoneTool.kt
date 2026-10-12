package dev.harness.tools

import kotlinx.serialization.json.JsonObject

/** Name the agent loop watches for to end a run. */
const val DONE_TOOL_NAME = "done"

/**
 * Explicit "I am finished" signal. A model that stops by returning an empty message after a tool looks
 * like a hang; calling this gives the run a clear, visible end. The loop shows the summary and stops.
 */
class DoneTool : Tool {
    override val name = DONE_TOOL_NAME
    override val description =
        "Call when the task is complete, or you cannot continue, with a short summary for the user. Ends the run."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "summary" to Params.str("What was done, or what is blocking you"),
        required = listOf("summary"),
    )

    override suspend fun run(args: JsonObject): String = Args.str(args, "summary")
}
