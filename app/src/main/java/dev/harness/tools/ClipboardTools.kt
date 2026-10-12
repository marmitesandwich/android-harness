package dev.harness.tools

import dev.harness.backend.ClipboardBackend
import kotlinx.serialization.json.JsonObject

/** Reads the clipboard. Approval-gated: it often holds passwords or one-time codes that would go to the LLM. */
class ClipboardGetTool(private val clipboard: ClipboardBackend) : Tool {
    override val name = "clipboard_get"
    override val description =
        "Read the clipboard text. Android hides the clipboard from background apps, so this can come back " +
            "empty unless Harness is in the foreground."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String = clipboard.get()?.ifEmpty { null } ?: "(clipboard is empty or unavailable)"
}

/** Puts text on the clipboard; a reliable way to get long text into a field, then paste it. */
class ClipboardSetTool(private val clipboard: ClipboardBackend) : Tool {
    override val name = "clipboard_set"
    override val description = "Replace the clipboard text."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "text" to Params.str("Text to put on the clipboard"),
        required = listOf("text"),
    )

    override suspend fun run(args: JsonObject): String {
        val text = Args.str(args, "text")
        clipboard.set(text)
        return "OK: clipboard set (${text.length} chars)"
    }
}
