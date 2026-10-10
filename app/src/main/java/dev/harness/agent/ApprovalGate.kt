package dev.harness.agent

import dev.harness.llm.ToolCall
import dev.harness.tools.Risk
import dev.harness.tools.Tool

/** The user's answer to an approval request. */
enum class ApprovalResponse { ALLOW_ONCE, ALWAYS_ALLOW, DENY }

/** User-facing prompt. Suspends until the user answers. */
fun interface ApprovalPrompt {
    suspend fun ask(tool: Tool, argumentsJson: String): ApprovalResponse
}

/**
 * Tiered gate: READ tools always run; WRITE tools require an explicit answer.
 * "Always allow" is remembered per tool, but only for tools that opt in
 * ([Tool.canAlwaysAllow]); `run_shell` opts out and always prompts.
 */
class ApprovalGate(
    private val prompt: ApprovalPrompt,
    /** Grants loaded from storage, so "always" survives new chats and restarts. */
    initialAlwaysAllowed: Set<String> = emptySet(),
    /** Called once when the user grants a new permanent allow, so the caller can persist it. */
    private val onAlwaysAllow: (String) -> Unit = {},
) {

    private val alwaysAllowed = initialAlwaysAllowed.toMutableSet()

    fun isAlwaysAllowed(toolName: String): Boolean = toolName in alwaysAllowed

    /** True when the tool may run; false when the user denied it. */
    suspend fun approve(tool: Tool, call: ToolCall): Boolean {
        if (tool.risk == Risk.READ) return true
        // A stored grant for a tool that has since opted out (e.g. run_shell) must not apply.
        if (tool.canAlwaysAllow && tool.name in alwaysAllowed) return true

        return when (prompt.ask(tool, call.arguments)) {
            ApprovalResponse.ALLOW_ONCE -> true
            ApprovalResponse.ALWAYS_ALLOW -> {
                if (tool.canAlwaysAllow && alwaysAllowed.add(tool.name)) onAlwaysAllow(tool.name)
                true
            }
            ApprovalResponse.DENY -> false
        }
    }
}
