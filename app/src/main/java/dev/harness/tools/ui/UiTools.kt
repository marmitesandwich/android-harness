package dev.harness.tools.ui

import dev.harness.backend.UiBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.Tool
import kotlinx.serialization.json.JsonObject

internal const val ACCESSIBILITY_UNAVAILABLE =
    "ERROR: accessibility service is not enabled (Settings > Accessibility > Harness UI control)."

internal suspend fun UiBackend.guard(): String? = if (available) null else ACCESSIBILITY_UNAVAILABLE

/** Dump the current window's view tree as indented text. */
class UiTreeTool(private val ui: UiBackend) : Tool {
    override val name = "ui_tree"
    override val description = "Read the current screen's view hierarchy (class, ids, text, bounds). Call this before tapping to find coordinates."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String = ui.guard() ?: ui.windowTree()
}

/** Capture a screenshot to the app cache and report its path and size. */
class ScreenshotTool(private val ui: UiBackend) : Tool {
    override val name = "screenshot"
    override val description = "Capture the current screen to a PNG file and report the path and dimensions."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val shot = ui.screenshot()
        return "screenshot saved: ${shot.path} (${shot.width}x${shot.height})"
    }
}

/** Tap at screen coordinates. */
class TapTool(private val ui: UiBackend) : Tool {
    override val name = "tap"
    override val description = "Tap at screen coordinates (x, y) in pixels."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "x" to Params.int("X coordinate in pixels"),
        "y" to Params.int("Y coordinate in pixels"),
        required = listOf("x", "y"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val x = Args.int(args, "x")
        val y = Args.int(args, "y")
        ui.tap(x, y)
        return "OK: tapped ($x, $y)"
    }
}

/** Swipe between two points. */
class SwipeTool(private val ui: UiBackend) : Tool {
    override val name = "swipe"
    override val description = "Swipe from (from_x, from_y) to (to_x, to_y) over duration_ms milliseconds."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "from_x" to Params.int("Start X"),
        "from_y" to Params.int("Start Y"),
        "to_x" to Params.int("End X"),
        "to_y" to Params.int("End Y"),
        "duration_ms" to Params.int("Swipe duration in ms (default 300)"),
        required = listOf("from_x", "from_y", "to_x", "to_y"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        val fromX = Args.int(args, "from_x")
        val fromY = Args.int(args, "from_y")
        val toX = Args.int(args, "to_x")
        val toY = Args.int(args, "to_y")
        val duration = Args.optInt(args, "duration_ms", 300).coerceIn(1, 5000).toLong()
        ui.swipe(fromX, fromY, toX, toY, duration)
        return "OK: swiped ($fromX,$fromY) -> ($toX,$toY) in ${duration}ms"
    }
}

/** Type into the focused editable field. */
class TypeTextTool(private val ui: UiBackend) : Tool {
    override val name = "type_text"
    override val description = "Set the text of the currently focused editable field."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "text" to Params.str("Text to enter"),
        required = listOf("text"),
    )

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        ui.typeText(Args.str(args, "text"))
        return "OK: typed text"
    }
}

/** Global back. */
class PressBackTool(private val ui: UiBackend) : Tool {
    override val name = "press_back"
    override val description = "Press the system Back button."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        ui.pressBack()
        return "OK: back"
    }
}

/** Global home. */
class PressHomeTool(private val ui: UiBackend) : Tool {
    override val name = "press_home"
    override val description = "Go to the system Home screen."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String {
        ui.guard()?.let { return it }
        ui.pressHome()
        return "OK: home"
    }
}
