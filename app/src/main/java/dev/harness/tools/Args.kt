package dev.harness.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Small helpers for reading model-supplied arguments defensively. */
object Args {
    fun str(args: JsonObject, name: String): String =
        (args[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            ?: throw IllegalArgumentException("missing required string '$name'")

    fun optStr(args: JsonObject, name: String): String? =
        (args[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    fun int(args: JsonObject, name: String): Int =
        optStr(args, name)?.trim()?.toIntOrNull()
            ?: throw IllegalArgumentException("missing or non-integer '$name'")

    fun optInt(args: JsonObject, name: String, default: Int): Int =
        optStr(args, name)?.trim()?.toIntOrNull() ?: default

    fun optLong(args: JsonObject, name: String, default: Long): Long =
        optStr(args, name)?.trim()?.toLongOrNull() ?: default
}

/** Builders that keep tool schemas terse and consistent. */
object Params {
    fun obj(vararg props: Pair<String, JsonElement>, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
            put("additionalProperties", false)
            if (required.isNotEmpty()) {
                put(
                    "required",
                    buildJsonArray { required.forEach { add(JsonPrimitive(it)) } },
                )
            }
        }

    private fun typed(type: String, description: String): JsonObject =
        buildJsonObject {
            put("type", type)
            put("description", description)
        }

    fun str(description: String): JsonObject = typed("string", description)
    fun int(description: String): JsonObject = typed("integer", description)
    fun num(description: String): JsonObject = typed("number", description)
    fun bool(description: String): JsonObject = typed("boolean", description)
}
