package dev.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Request wire model -------------------------------------------------

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<WireMessage>,
    val tools: List<WireTool>? = null,
    val stream: Boolean = true,
    val temperature: Double? = null,
)

@Serializable
internal data class WireMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
internal data class WireTool(
    val type: String = "function",
    val function: WireFunction,
)

@Serializable
internal data class WireFunction(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

@Serializable
internal data class WireToolCall(
    val id: String,
    val type: String = "function",
    val function: WireToolCallFunction,
)

@Serializable
internal data class WireToolCallFunction(
    val name: String,
    val arguments: String,
)

// ---- Streaming response wire model --------------------------------------

@Serializable
internal data class ChunkResponse(val choices: List<ChunkChoice> = emptyList())

@Serializable
internal data class ChunkChoice(
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class Delta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
)

@Serializable
internal data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionDelta? = null,
)

@Serializable
internal data class FunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)
