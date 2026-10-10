package dev.harness.tools

import kotlinx.serialization.json.Json

/**
 * Lenient JSON used for tool arguments and LLM payloads. Models frequently emit
 * fields we do not model, so unknown keys are ignored rather than failing.
 */
val HarnessJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = false
}
