package dev.harness.tools

import dev.harness.backend.ShellBackend
import dev.harness.backend.UiBackend
import dev.harness.tools.shell.AppInfoTool
import dev.harness.tools.shell.DumpsysTool
import dev.harness.tools.shell.ForceStopTool
import dev.harness.tools.shell.GetPropTool
import dev.harness.tools.shell.GetSettingTool
import dev.harness.tools.shell.LaunchAppTool
import dev.harness.tools.shell.ListPackagesTool
import dev.harness.tools.shell.LogcatTool
import dev.harness.tools.shell.PutSettingTool
import dev.harness.tools.shell.RunShellTool
import dev.harness.tools.ui.FindElementTool
import dev.harness.tools.ui.PressBackTool
import dev.harness.tools.ui.PressHomeTool
import dev.harness.tools.ui.ScreenshotTool
import dev.harness.tools.ui.SwipeTool
import dev.harness.tools.ui.TapElementTool
import dev.harness.tools.ui.TapTool
import dev.harness.tools.ui.TypeTextTool
import dev.harness.tools.ui.UiTreeTool
import dev.harness.tools.wait.WaitForLogTool
import dev.harness.tools.wait.WaitTool

/** Builds the v1 tool set over the given backends. */
object HarnessTools {
    fun registry(shell: ShellBackend, ui: UiBackend): ToolRegistry = ToolRegistry(
        listOf(
            // Shell READ
            GetSettingTool(shell),
            LogcatTool(shell),
            DumpsysTool(shell),
            ListPackagesTool(shell),
            AppInfoTool(shell),
            GetPropTool(shell),
            // Shell WRITE
            PutSettingTool(shell),
            LaunchAppTool(shell),
            ForceStopTool(shell),
            RunShellTool(shell),
            // Accessibility READ
            UiTreeTool(ui),
            FindElementTool(ui),
            ScreenshotTool(ui),
            // Accessibility WRITE
            TapTool(ui),
            TapElementTool(ui),
            SwipeTool(ui),
            TypeTextTool(ui),
            PressBackTool(ui),
            PressHomeTool(ui),
            // Waiting
            WaitTool(),
            WaitForLogTool(shell),
        ),
    )
}
