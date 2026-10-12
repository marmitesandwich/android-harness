package dev.harness.ui

import dev.harness.llm.ChatMessage
import dev.harness.llm.Role
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Everything needed to bring a chat back after the system kills the process. */
@Serializable
data class Transcript(
    val sessionId: String,
    val conversation: List<ChatMessage>,
    val messages: List<UiMessage>,
)

interface TranscriptStore {
    fun load(): Transcript?
    fun save(transcript: Transcript)
    fun clear()
}

/** One JSON file in private storage. A missing or unreadable file means "no saved chat". */
class FileTranscriptStore(private val file: () -> File) : TranscriptStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): Transcript? = try {
        file().takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Transcript>(it) }
    } catch (_: Exception) {
        null
    }

    override fun save(transcript: Transcript) {
        val target = file()
        target.parentFile?.mkdirs()
        // Write beside, then rename, so a kill mid-write cannot leave half a file.
        val tmp = File(target.path + ".tmp")
        tmp.writeText(json.encodeToString(transcript))
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    override fun clear() {
        file().delete()
    }
}

/**
 * A chat cut off mid-turn ends in an assistant tool call or tool results with no final reply, which the
 * API rejects. Drop that unfinished turn so the history ends on a user message or a plain assistant reply.
 */
fun dropUnfinishedTurn(conversation: List<ChatMessage>): List<ChatMessage> =
    conversation.dropLastWhile { it.role == Role.TOOL || (it.role == Role.ASSISTANT && it.toolCalls.isNotEmpty()) }

/** Entries that were live when the process died are shown as stopped, never as still running. */
fun settled(messages: List<UiMessage>): List<UiMessage> = messages.map {
    if (it.running || it.streaming) it.copy(running = false, streaming = false, text = it.text.ifEmpty { "Interrupted." }) else it
}
