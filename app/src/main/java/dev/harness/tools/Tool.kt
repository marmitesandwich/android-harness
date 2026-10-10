package dev.harness.tools

import kotlinx.serialization.json.JsonObject

/** Read tools run without prompting; write tools require user approval. */
enum class Risk { READ, WRITE }

/**
 * A single capability the agent can invoke.
 *
 * Implementations may throw: the agent loop wraps every call and converts a
 * failure into an `ERROR: ...` tool result so the conversation never dies.
 */
interface Tool {
    /** Stable identifier the model uses in `tool_calls`. */
    val name: String

    /** One-line description shown to the model. */
    val description: String

    /** JSON Schema (type: object) describing the arguments object. */
    val jsonSchema: JsonObject

    val risk: Risk

    /**
     * Whether the user may permanently allow this tool after one approval.
     * Destructive tools (`run_shell`) opt out and always prompt.
     */
    val canAlwaysAllow: Boolean get() = true

    /** Execute and return a result string. */
    suspend fun run(args: JsonObject): String
}
