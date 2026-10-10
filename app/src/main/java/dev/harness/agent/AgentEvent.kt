package dev.harness.agent

import dev.harness.tools.Risk

/** Why the loop stopped. */
enum class FinishReason { COMPLETED, MAX_STEPS, CANCELLED, ERROR }

/** Progress events surfaced to the UI while the loop runs. */
sealed interface AgentEvent {
    /** Incremental assistant text. */
    data class AssistantDelta(val text: String) : AgentEvent

    /** A tool is about to run; approval has already been granted. */
    data class ToolStarted(
        val id: String,
        val name: String,
        val argumentsJson: String,
        val risk: Risk,
    ) : AgentEvent

    /** A tool finished (or was denied/failed). [denied] marks a user refusal. */
    data class ToolFinished(
        val id: String,
        val name: String,
        val result: String,
        val denied: Boolean = false,
    ) : AgentEvent

    /** The loop ended normally. */
    data class Finished(val reason: FinishReason) : AgentEvent

    /** The LLM call failed; the loop stops. */
    data class Failed(val message: String) : AgentEvent
}
