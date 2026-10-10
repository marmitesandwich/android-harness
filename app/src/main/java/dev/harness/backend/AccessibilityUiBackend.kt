package dev.harness.backend

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * [UiBackend] backed by the enabled [HarnessAccessibilityService].
 *
 * The service is looked up lazily on each call so the backend stays valid when
 * the user toggles the accessibility service on or off.
 */
class AccessibilityUiBackend(
    private val serviceProvider: () -> HarnessAccessibilityService? = { HarnessAccessibilityService.instance },
    private val screenshotDir: () -> File,
) : UiBackend {

    override val available: Boolean get() = serviceProvider() != null

    override suspend fun windowTree(): String = withContext(Dispatchers.Main) {
        val service = serviceProvider() ?: return@withContext NOT_ENABLED
        val root = service.rootInActiveWindow ?: return@withContext "(no active window)"
        val builder = StringBuilder()
        dump(root, 0, builder, intArrayOf(0))
        builder.toString().ifEmpty { "(empty view tree)" }
    }

    override suspend fun screenshot(): Screenshot {
        val service = serviceProvider() ?: error(NOT_ENABLED)
        val bitmap = withContext(Dispatchers.Main) { capture(service) }
        val file = File(screenshotDir(), "shot-${System.currentTimeMillis()}.png")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return Screenshot(file.absolutePath, bitmap.width, bitmap.height)
    }

    override suspend fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatch(path, startMs = 0, durationMs = TAP_MS)
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long) {
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }
        dispatch(path, startMs = 0, durationMs = durationMs)
    }

    override suspend fun typeText(text: String) = withContext(Dispatchers.Main) {
        val service = serviceProvider() ?: error(NOT_ENABLED)
        val root = service.rootInActiveWindow ?: error("no active window")
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            error("no editable field is focused")
        }
    }

    override suspend fun pressBack() = globalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    override suspend fun pressHome() = globalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    private suspend fun globalAction(action: Int) = withContext(Dispatchers.Main) {
        val service = serviceProvider() ?: error(NOT_ENABLED)
        if (!service.performGlobalAction(action)) error("global action $action rejected")
    }

    private suspend fun dispatch(path: Path, startMs: Long, durationMs: Long) {
        val service = serviceProvider() ?: error(NOT_ENABLED)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, startMs, durationMs))
            .build()
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                if (!service.dispatchGesture(gesture, callback, null) && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private suspend fun capture(service: HarnessAccessibilityService): Bitmap =
        suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (continuation.isActive) {
                            if (software != null) continuation.resume(software)
                            else continuation.cancel(IllegalStateException("could not decode screenshot"))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.cancel(IllegalStateException("screenshot failed (code $errorCode)"))
                        }
                    }
                },
            )
        }

    private fun dump(node: AccessibilityNodeInfo?, depth: Int, builder: StringBuilder, count: IntArray) {
        if (node == null || depth > MAX_DEPTH || count[0] >= MAX_NODES) return
        count[0]++

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        builder.append("  ".repeat(depth))
        builder.append(simpleClass(node.className))
        node.viewIdResourceName?.let { builder.append(" #").append(it.substringAfterLast('/')) }
        node.text?.takeIf { it.isNotBlank() }?.let { builder.append(" text=\"").append(it.take(MAX_TEXT)).append('"') }
        node.contentDescription?.takeIf { it.isNotBlank() }
            ?.let { builder.append(" desc=\"").append(it.take(MAX_TEXT)).append('"') }
        builder.append(" [")
            .append(bounds.left).append(',').append(bounds.top)
            .append('-')
            .append(bounds.right).append(',').append(bounds.bottom).append(']')
        if (node.isClickable) builder.append(" clickable")
        if (node.isEditable) builder.append(" editable")
        if (node.isFocused) builder.append(" focused")
        if (node.isScrollable) builder.append(" scrollable")
        builder.append('\n')

        for (index in 0 until node.childCount) {
            dump(node.getChild(index), depth + 1, builder, count)
        }
    }

    private fun simpleClass(className: CharSequence?): String =
        className?.toString()?.substringAfterLast('.') ?: "View"

    private companion object {
        const val NOT_ENABLED = "ERROR: accessibility service is not enabled (Settings > Accessibility > Harness UI control)"
        const val TAP_MS = 50L
        const val MAX_DEPTH = 14
        const val MAX_NODES = 500
        const val MAX_TEXT = 120
    }
}

/** Used when Accessibility is off so UI tools return a clear, actionable message. */
class UnavailableUiBackend(private val reason: String) : UiBackend {
    override val available: Boolean = false
    override suspend fun windowTree(): String = reason
    override suspend fun screenshot(): Screenshot = error(reason)
    override suspend fun tap(x: Int, y: Int): Unit = error(reason)
    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Unit = error(reason)
    override suspend fun typeText(text: String): Unit = error(reason)
    override suspend fun pressBack(): Unit = error(reason)
    override suspend fun pressHome(): Unit = error(reason)
}
