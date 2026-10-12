package dev.harness.backend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

interface ClipboardBackend {
    /** Current clip as text; null when empty or when Android withholds it from a background app. */
    fun get(): String?
    fun set(text: String)
}

class AndroidClipboardBackend(private val context: Context) : ClipboardBackend {
    private val manager: ClipboardManager get() = context.getSystemService(ClipboardManager::class.java)

    override fun get(): String? =
        manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

    override fun set(text: String) = manager.setPrimaryClip(ClipData.newPlainText("harness", text))
}
