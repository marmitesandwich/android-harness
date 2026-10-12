package dev.harness.backend

import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.harness.agent.ApprovalResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [OverlayBackend] drawing accessibility-overlay windows, which need no extra permission and sit above
 * other apps. The highlight window is full-screen and untouchable; the approval card is a small window
 * at the bottom that takes touches, while touches outside it still reach the app underneath.
 */
class AccessibilityOverlayBackend(
    private val serviceProvider: () -> HarnessAccessibilityService? = { HarnessAccessibilityService.instance },
) : OverlayBackend {

    private val main = Handler(Looper.getMainLooper())
    private var highlightView: View? = null
    private var highlightShownAt = 0L
    private var removeHighlight: Runnable? = null
    private var cardView: View? = null

    init {
        // A highlight points at a screen that no longer exists once the user navigates, so drop it then.
        HarnessAccessibilityService.windowChangeListener = {
            if (SystemClock.uptimeMillis() - highlightShownAt > GRACE_MS) clearHighlight()
        }
    }

    override val available: Boolean get() = serviceProvider() != null

    override suspend fun highlight(left: Int, top: Int, right: Int, bottom: Int, label: String?, seconds: Int) =
        withContext(Dispatchers.Main) {
            val service = serviceProvider() ?: error("accessibility service is not enabled")
            clearHighlight()
            val view = HighlightView(service, RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()), label)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
            windowManager(service).addView(view, params)
            highlightView = view
            highlightShownAt = SystemClock.uptimeMillis()
            removeHighlight = Runnable { clearHighlight() }.also { main.postDelayed(it, seconds * 1000L) }
        }

    override fun showApproval(card: ApprovalCard, onResponse: (ApprovalResponse) -> Unit) {
        val service = serviceProvider() ?: return
        dismissApproval()
        val view = buildApprovalCard(service, card) { response ->
            dismissApproval()
            onResponse(response)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            val margin = (16 * service.resources.displayMetrics.density).toInt()
            x = 0
            y = margin
            horizontalMargin = 0.03f
        }
        windowManager(service).addView(view, params)
        cardView = view
    }

    override fun dismissApproval() {
        val view = cardView ?: return
        cardView = null
        runCatching { windowManagerOrNull()?.removeViewImmediate(view) }
    }

    private fun clearHighlight() {
        removeHighlight?.let(main::removeCallbacks)
        removeHighlight = null
        val view = highlightView ?: return
        highlightView = null
        runCatching { windowManagerOrNull()?.removeViewImmediate(view) }
    }

    private fun windowManager(service: HarnessAccessibilityService) =
        service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager

    private fun windowManagerOrNull() = serviceProvider()?.let(::windowManager)

    private companion object {
        /** Showing the overlay itself fires window events; ignore those. */
        const val GRACE_MS = 1_000L
    }
}
