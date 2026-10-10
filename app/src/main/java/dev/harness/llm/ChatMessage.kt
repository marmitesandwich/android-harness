package dev.harness.llm

enum class Role(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
    ;

    companion object {
        fun fromWire(value: String): Role =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("unknown role '$value'")
    }
}

/** A tool invocation requested by the model. [arguments] is the raw JSON object text. */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** One entry of the conversation sent to the model. */
data class ChatMessage(
    val role: Role,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    /** Tool name, kept for display. Not serialised: some endpoints reject it. */
    val name: String? = null,
) {
    companion object {
        fun system(text: String) = ChatMessage(Role.SYSTEM, content = text)

        fun user(text: String) = ChatMessage(Role.USER, content = text)

        fun assistant(text: String?, toolCalls: List<ToolCall> = emptyList()) =
            ChatMessage(Role.ASSISTANT, content = text?.takeIf { it.isNotEmpty() }, toolCalls = toolCalls)

        fun toolResult(toolCallId: String, name: String, content: String) =
            ChatMessage(Role.TOOL, content = content, toolCallId = toolCallId, name = name)
    }
}
