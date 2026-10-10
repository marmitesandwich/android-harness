package dev.harness.tools

import kotlinx.serialization.json.JsonObject

/** Tool declaration sent to the LLM (name + description + parameter schema). */
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)
