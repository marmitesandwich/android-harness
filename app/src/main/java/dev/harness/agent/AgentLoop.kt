package dev.harness.agent

import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmClient
import dev.harness.llm.LlmEvent
import dev.harness.llm.Role
import dev.harness.llm.ToolCall
import dev.harness.tools.HarnessJson
import dev.harness.tools.Tool
import dev.harness.tools.ToolRegistry
import dev.harness.tools.truncateOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.jsonObject

/**
 * The agent loop: send the conversation to the LLM, run any requested tools
 * (after gating), append results, and repeat until the model replies without
 * tool calls, the step budget is exhausted, or the flow is cancelled.
 *
 * The caller owns [conversation]; the loop mutates it in place so the UI keeps
 * a single source of truth.
 */
class AgentLoop(
    private val llm: LlmClient,
    private val registry: ToolRegistry,
    private val gate: ApprovalGate,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {

    fun run(conversation: MutableList<ChatMessage>): Flow<AgentEvent> = flow {
        if (conversation.none { it.role == Role.SYSTEM }) {
            conversation.add(0, ChatMessage.system(systemPrompt))
        }

        var step = 0
        while (step < maxSteps) {
            step++
            emit(AgentEvent.Step(step, maxSteps))

            val assistantText = StringBuilder()
            var calls: List<ToolCall> = emptyList()
            var failure: String? = null

            llm.stream(conversation.toList(), registry.schemas()).collect { event ->
                when (event) {
                    is LlmEvent.TextDelta -> {
                        assistantText.append(event.text)
                        emit(AgentEvent.AssistantDelta(event.text))
                    }
                    is LlmEvent.ToolCallsReady -> calls = event.calls
                    is LlmEvent.Completed -> Unit
                    is LlmEvent.Failure -> failure = event.message
                }
            }

            if (failure != null) {
                emit(AgentEvent.Failed(failure))
                emit(AgentEvent.Finished(FinishReason.ERROR))
                return@flow
            }

            conversation.add(ChatMessage.assistant(assistantText.toString(), calls))

            if (calls.isEmpty()) {
                emit(AgentEvent.Finished(FinishReason.COMPLETED))
                return@flow
            }

            for (call in calls) {
                val tool = registry.find(call.name)
                if (tool == null) {
                    appendResult(conversation, call, "ERROR: unknown tool '${call.name}'")
                    emit(AgentEvent.ToolFinished(call.id, call.name, "ERROR: unknown tool '${call.name}'"))
                    continue
                }

                if (!gate.approve(tool, call)) {
                    val denied = "ERROR: user denied"
                    emit(AgentEvent.ToolFinished(call.id, tool.name, denied, denied = true))
                    conversation.add(ChatMessage.toolResult(call.id, tool.name, denied))
                    continue
                }

                emit(AgentEvent.ToolStarted(call.id, tool.name, call.arguments, tool.risk))
                val result = truncateOutput(runTool(tool, call.arguments), maxOutputBytes)
                emit(AgentEvent.ToolFinished(call.id, tool.name, result))
                conversation.add(ChatMessage.toolResult(call.id, tool.name, result))
            }
        }

        emit(AgentEvent.Finished(FinishReason.MAX_STEPS))
    }

    private fun appendResult(conversation: MutableList<ChatMessage>, call: ToolCall, result: String) {
        conversation.add(ChatMessage.toolResult(call.id, call.name, result))
    }

    private suspend fun runTool(tool: Tool, argumentsJson: String): String = try {
        val args = HarnessJson.parseToJsonElement(argumentsJson.ifBlank { "{}" }).jsonObject
        tool.run(args)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        "ERROR: ${t.message ?: t::class.simpleName}"
    }
}
