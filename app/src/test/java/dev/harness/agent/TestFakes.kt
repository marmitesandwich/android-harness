package dev.harness.agent

import dev.harness.llm.ChatMessage
import dev.harness.llm.LlmClient
import dev.harness.llm.LlmEvent
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import dev.harness.tools.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.JsonObject

/** Scripted LLM: returns [turns] in order and records the history of each request. */
internal class FakeLlmClient(private val turns: List<List<LlmEvent>>) : LlmClient {
    val requests = mutableListOf<List<ChatMessage>>()
    private var index = 0

    override fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent> {
        requests += history.toList()
        val turn = turns.getOrElse(index) { listOf(LlmEvent.Completed("stop")) }
        index++
        return turn.asFlow()
    }
}

internal open class FakeTool(
    override val name: String,
    override val risk: Risk,
    override val canAlwaysAllow: Boolean = true,
    private val result: String = "ok",
) : Tool {
    var invocations = 0
    var lastArgs: JsonObject? = null

    override val description = "fake tool"
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String {
        invocations++
        lastArgs = args
        return result
    }
}

internal fun gateRespondingWith(response: ApprovalResponse) =
    ApprovalGate(ApprovalPrompt { _, _ -> response })
