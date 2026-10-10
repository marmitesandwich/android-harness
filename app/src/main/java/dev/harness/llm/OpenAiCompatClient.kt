package dev.harness.llm

import dev.harness.tools.HarnessJson
import dev.harness.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible `/chat/completions` client with SSE streaming.
 *
 * Works against any endpoint that speaks the OpenAI chat-completions protocol
 * (including OpenCode Go's `https://opencode.ai/zen/go/v1`).
 */
class OpenAiCompatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val temperature: Double? = null,
) : LlmClient {

    override fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent> = flow {
        val requestBody = buildChatRequest(model, history, tools, temperature)
        val payload = HarnessJson.encodeToString(ChatRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url(chatCompletionsUrl(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.body.string().take(500) }.getOrDefault("")
                    emit(LlmEvent.Failure("HTTP ${response.code} ${response.message}$detail"))
                    return@use
                }

                val source = response.body.source()
                val accumulator = StreamAccumulator()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val data = SseParser.dataPayload(line) ?: continue
                    if (data == SseParser.DONE) break
                    val chunk = runCatching {
                        HarnessJson.decodeFromString(ChunkResponse.serializer(), data)
                    }.getOrNull() ?: continue
                    accumulator.accept(chunk)?.let { emit(LlmEvent.TextDelta(it)) }
                }

                if (accumulator.hasToolCalls()) emit(LlmEvent.ToolCallsReady(accumulator.toolCalls()))
                emit(LlmEvent.Completed(accumulator.finishReason))
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            emit(LlmEvent.Failure(t.message ?: t.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** Long read timeout is required: an SSE stream stays open for the whole turn. */
fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

/** Resolves the chat-completions URL from a configurable base URL. */
fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
}

internal fun buildChatRequest(
    model: String,
    history: List<ChatMessage>,
    tools: List<ToolSchema>,
    temperature: Double?,
): ChatRequest = ChatRequest(
    model = model,
    messages = history.map { it.toWire() },
    tools = tools.takeIf { it.isNotEmpty() }?.map { schema ->
        WireTool(function = WireFunction(schema.name, schema.description, schema.parameters))
    },
    temperature = temperature,
)

private fun ChatMessage.toWire(): WireMessage = WireMessage(
    role = role.wire,
    content = content,
    toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map { call ->
        WireToolCall(id = call.id, function = WireToolCallFunction(call.name, call.arguments))
    },
    toolCallId = toolCallId,
    name = name,
)
