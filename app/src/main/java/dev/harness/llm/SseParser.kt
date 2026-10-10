package dev.harness.llm

/** Minimal Server-Sent Events parsing for OpenAI-compatible streaming responses. */
object SseParser {
    /** Sentinel payload that terminates an OpenAI SSE stream. */
    const val DONE = "[DONE]"

    /**
     * Returns the payload of a `data:` line, or null when the line carries no
     * data (blank line, comment, or a different SSE field).
     */
    fun dataPayload(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        return trimmed.substring(DATA_PREFIX.length).trim().ifEmpty { null }
    }

    private const val DATA_PREFIX = "data:"
}
