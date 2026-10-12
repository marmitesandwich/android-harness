package dev.harness.backend

import java.io.File

/** One scratchpad the agent can read back in a later run or chat. */
interface NotesStore {
    fun read(): String
    fun write(text: String)
}

/** Notes kept in a single file in the app's private storage; the file is resolved lazily per call. */
class FileNotesStore(private val file: () -> File) : NotesStore {
    override fun read(): String = file().takeIf { it.exists() }?.readText().orEmpty()

    override fun write(text: String) {
        val target = file()
        target.parentFile?.mkdirs()
        target.writeText(text)
    }
}
