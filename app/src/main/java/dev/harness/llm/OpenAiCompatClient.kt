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
 *
 * This class never throws out of [stream]: every failure, including a malformed
 * URL or header, is reported as [LlmEvent.Failure] so the app cannot crash.
 */
class OpenAiCompatClient(
    baseUrl: String,
    apiKey: String,
    model: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val temperature: Double? = null,
    sessionId: String = newSessionId(),
) : LlmClient {

    private val baseUrl = baseUrl.trim()
    private val model = model.trim()
    private val apiKey = sanitizeApiKey(apiKey)
    private val sessionId = sessionId.trim().ifEmpty { newSessionId() }

    override fun stream(history: List<ChatMessage>, tools: List<ToolSchema>): Flow<LlmEvent> = flow {
        try {
            val requestBody = buildChatRequest(model, history, tools, temperature)
            val payload = HarnessJson.encodeToString(ChatRequest.serializer(), requestBody)

            val builder = Request.Builder()
                .url(chatCompletionsUrl(baseUrl))
                .post(payload.toRequestBody(JSON_MEDIA))
            harnessHeaders(apiKey, sessionId).forEach { (name, value) -> builder.header(name, value) }

            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = runCatching { response.body.string() }.getOrDefault("")
                    emit(LlmEvent.Failure(describeHttpFailure(response.code, response.message, body)))
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

/**
 * Removes every whitespace character. Pasted API keys routinely carry a
 * trailing newline or wrapped spaces, and an HTTP header value must not
 * contain them.
 */
internal fun sanitizeApiKey(raw: String): String = raw.filterNot { it.isWhitespace() }

/** Identifies this client to OpenCode Go, which rejects generic HTTP-library user agents. */
const val HARNESS_USER_AGENT = "harness-android/0.1.0"

internal fun newSessionId(): String = java.util.UUID.randomUUID().toString()

/**
 * Request headers. `x-opencode-session` lets the gateway keep a conversation on
 * one route and reuse prompt cache; a stable per-conversation value is expected.
 */
internal fun harnessHeaders(apiKey: String, sessionId: String): List<Pair<String, String>> = listOf(
    "Authorization" to "Bearer $apiKey",
    "Accept" to "text/event-stream",
    "User-Agent" to HARNESS_USER_AGENT,
    "x-opencode-session" to sessionId,
)

/**
 * Turns an HTTP failure into one readable line, preferring the message inside
 * an `{"error":{"message":...}}` envelope and falling back to the raw body.
 */
internal fun describeHttpFailure(code: Int, reason: String, body: String): String {
    val parsed = runCatching {
        HarnessJson.decodeFromString(ApiErrorEnvelope.serializer(), body).error?.message
    }.getOrNull()
    val detail = parsed?.takeIf { it.isNotBlank() } ?: body.take(300).ifBlank { "(no body)" }
    return "HTTP $code $reason: $detail"
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
)
