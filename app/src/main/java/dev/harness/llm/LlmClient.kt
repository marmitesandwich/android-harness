package dev.harness.llm

import dev.harness.tools.ToolSchema
import kotlinx.coroutines.flow.Flow

/** Streams assistant turns. Implementations must not throw; report failures as [LlmEvent.Failure]. */
interface LlmClient {
    fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent>
}
