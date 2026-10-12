package dev.harness.backend

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Hold the running AccessibilityService so [AccessibilityUiBackend] can use it.
 * Nothing is captured passively; the tree is pulled on demand by tools.
 */
class HarnessAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) windowChangeListener?.invoke()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: HarnessAccessibilityService? = null
            private set

        val connected: Boolean get() = instance != null

        /** Called on the main thread when a different window comes to the front. */
        @Volatile
        var windowChangeListener: (() -> Unit)? = null
    }
}
