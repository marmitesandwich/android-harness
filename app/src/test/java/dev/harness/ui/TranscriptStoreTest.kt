package dev.harness.ui

import dev.harness.llm.ChatMessage
import dev.harness.llm.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TranscriptStoreTest {

    @get:Rule
    val dir = TemporaryFolder()

    private fun store() = FileTranscriptStore { File(dir.root, "t.json") }

    private val saved = Transcript(
        sessionId = "s1",
        conversation = listOf(
            ChatMessage.user("check wifi"),
            ChatMessage.assistant(null, listOf(ToolCall("c1", "device_status", "{}"))),
            ChatMessage.toolResult("c1", "device_status", "ok"),
            ChatMessage.assistant("Wi-Fi is fine."),
        ),
        messages = listOf(
            UiMessage("u", UiMessage.Kind.USER, "check wifi"),
            UiMessage("t", UiMessage.Kind.TOOL, "ok", toolName = "device_status", toolArgs = "{}"),
        ),
    )

    @Test
    fun `a saved chat loads back identical`() {
        store().save(saved)
        assertEquals(saved, store().load())
    }

    @Test
    fun `no file or a corrupt file means no saved chat`() {
        assertNull(store().load())
        File(dir.root, "t.json").writeText("{not json")
        assertNull(store().load())
    }

    @Test
    fun `clear removes the saved chat`() {
        store().save(saved)
        store().clear()
        assertNull(store().load())
    }

    @Test
    fun `an unfinished tool turn is dropped but finished turns stay`() {
        val cut = saved.conversation.dropLast(1) // ends on the tool result, no final reply
        assertEquals(listOf(ChatMessage.user("check wifi")), dropUnfinishedTurn(cut))
        assertEquals(saved.conversation, dropUnfinishedTurn(saved.conversation))
    }

    @Test
    fun `entries live at the time of the kill are shown as stopped`() {
        val live = listOf(
            UiMessage("t", UiMessage.Kind.TOOL, "", toolName = "x", running = true),
            UiMessage("a", UiMessage.Kind.ASSISTANT, "part", streaming = true),
        )
        val out = settled(live)
        assertEquals(listOf(false, false), out.map { it.running || it.streaming })
        assertEquals(listOf("Interrupted.", "part"), out.map { it.text })
    }
}
