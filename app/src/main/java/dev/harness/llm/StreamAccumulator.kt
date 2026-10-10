package dev.harness.llm

/**
 * Reassembles OpenAI's incremental `delta` chunks into a full assistant turn.
 * Tool calls arrive split across chunks and are keyed by [ToolCallDelta.index].
 */
internal class StreamAccumulator {

    private val calls = LinkedHashMap<Int, PartialCall>()

    var finishReason: String? = null
        private set

    /** Feeds one chunk; returns new assistant text if the chunk carried any. */
    fun accept(chunk: ChunkResponse): String? {
        val choice = chunk.choices.firstOrNull() ?: return null
        choice.finishReason?.let { finishReason = it }

        choice.delta?.toolCalls?.forEach { delta ->
            val call = calls.getOrPut(delta.index) { PartialCall() }
            delta.id?.let { call.id = it }
            delta.function?.name?.let { call.name = it }
            delta.function?.arguments?.let { call.arguments.append(it) }
        }

        return choice.delta?.content?.takeIf { it.isNotEmpty() }
    }

    fun hasToolCalls(): Boolean = calls.isNotEmpty()

    fun toolCalls(): List<ToolCall> = calls.entries.sortedBy { it.key }.map { (index, call) ->
        ToolCall(
            id = call.id ?: "call_$index",
            name = call.name ?: "",
            arguments = call.arguments.toString().ifEmpty { "{}" },
        )
    }

    private class PartialCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )
}
