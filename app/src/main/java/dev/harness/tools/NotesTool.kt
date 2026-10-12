package dev.harness.tools

import dev.harness.backend.NotesStore
import kotlinx.serialization.json.JsonObject

internal const val MAX_NOTES_CHARS = 8_000

/**
 * Persistent scratchpad for progress and findings, so a long task survives the step cap or a new chat.
 * READ-tier: it only touches the app's own private note file.
 */
class NotesTool(private val store: NotesStore) : Tool {
    override val name = "notes"
    override val description =
        "Your persistent scratchpad (survives new chats and restarts). Actions: read, write (replace), " +
            "append, clear. Save progress and key findings on long tasks; read it first when resuming. " +
            "Max $MAX_NOTES_CHARS characters."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "action" to Params.str("'read' (default), 'write', 'append' or 'clear'"),
        "text" to Params.str("Text for write or append"),
    )

    override suspend fun run(args: JsonObject): String {
        return when (val action = Args.optStr(args, "action")?.lowercase() ?: "read") {
            "read" -> store.read().ifBlank { "(notes are empty)" }
            "clear" -> { store.write(""); "OK: notes cleared" }
            "write", "append" -> {
                val text = Args.str(args, "text")
                val next = if (action == "append" && store.read().isNotEmpty()) store.read() + "\n" + text else text
                require(next.length <= MAX_NOTES_CHARS) { "notes would be ${next.length} chars; the limit is $MAX_NOTES_CHARS" }
                store.write(next)
                "OK: notes saved (${next.length} chars)"
            }
            else -> throw IllegalArgumentException("action must be read, write, append or clear")
        }
    }
}
