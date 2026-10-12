package dev.harness.tools

import dev.harness.backend.FileNotesStore
import dev.harness.backend.NotesStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class InMemoryNotes(var text: String = "") : NotesStore {
    override fun read() = text
    override fun write(text: String) { this.text = text }
}

class NotesToolTest {

    private fun args(action: String, text: String? = null) = buildJsonObject {
        put("action", action)
        if (text != null) put("text", text)
    }

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `empty notes say so`() = runTest {
        assertEquals("(notes are empty)", NotesTool(InMemoryNotes()).run(buildJsonObject {}))
    }

    @Test
    fun `write replaces and append adds a line`() = runTest {
        val store = InMemoryNotes()
        val tool = NotesTool(store)
        tool.run(args("write", "step 1 done"))
        tool.run(args("append", "step 2 done"))
        assertEquals("step 1 done\nstep 2 done", tool.run(args("read")))
        tool.run(args("write", "fresh"))
        assertEquals("fresh", store.text)
    }

    @Test
    fun `clear empties the notes`() = runTest {
        val store = InMemoryNotes("old")
        assertEquals("OK: notes cleared", NotesTool(store).run(args("clear")))
        assertEquals("", store.text)
    }

    @Test
    fun `oversized notes are refused and left unchanged`() = runTest {
        val store = InMemoryNotes("keep")
        val error = runCatching { NotesTool(store).run(args("write", "x".repeat(MAX_NOTES_CHARS + 1))) }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
        assertEquals("keep", store.text)
    }

    @Test
    fun `file store round-trips and survives a new instance`() {
        val file = tmp.newFolder("files").resolve("notes.txt")
        FileNotesStore { file }.write("persisted")
        assertEquals("persisted", FileNotesStore { file }.read())
        assertEquals("", FileNotesStore { tmp.root.resolve("missing.txt") }.read())
    }
}
