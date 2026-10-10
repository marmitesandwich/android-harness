package dev.harness.llm

/** Events emitted by [LlmClient.stream] for a single assistant turn. */
sealed interface LlmEvent {
    /** Incremental assistant text to append to the UI. */
    data class TextDelta(val text: String) : LlmEvent

    /** Complete set of tool calls requested this turn (empty when none). */
    data class ToolCallsReady(val calls: List<ToolCall>) : LlmEvent

    /** Stream finished normally. */
    data class Completed(val finishReason: String?) : LlmEvent

    /** Transport or protocol failure. Emitted last; the flow then ends. */
    data class Failure(val message: String) : LlmEvent
}
