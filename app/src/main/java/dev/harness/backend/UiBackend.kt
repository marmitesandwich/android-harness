package dev.harness.backend

/** Metadata for a captured screenshot; the model is text-only so we return this, not pixels. */
data class Screenshot(val path: String, val width: Int, val height: Int)

/**
 * Drives the UI through an AccessibilityService: read the view tree, and
 * perform taps, swipes, typing and global navigation.
 */
interface UiBackend {
    val available: Boolean
    suspend fun windowTree(): String
    suspend fun screenshot(): Screenshot
    suspend fun tap(x: Int, y: Int)
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long)
    suspend fun typeText(text: String)
    suspend fun pressBack()
    suspend fun pressHome()
}
