package dev.harness.tools

import dev.harness.backend.ClipboardBackend
import dev.harness.backend.NotesStore
import dev.harness.backend.ShellBackend
import dev.harness.backend.UiBackend
import dev.harness.tools.shell.AppInfoTool
import dev.harness.tools.shell.SetRadioTool
import dev.harness.tools.shell.UninstallAppTool
import dev.harness.tools.shell.InstallApkTool
import dev.harness.tools.shell.QuerySmsTool
import dev.harness.tools.shell.QueryCalendarTool
import dev.harness.tools.shell.QueryContactsTool
import dev.harness.tools.shell.WriteFileTool
import dev.harness.tools.shell.ListFilesTool
import dev.harness.tools.shell.ReadFileTool
import dev.harness.tools.shell.StartIntentTool
import dev.harness.tools.shell.NotificationsTool
import dev.harness.tools.shell.DeviceStatusTool
import dev.harness.tools.shell.CurrentAppTool
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
import dev.harness.tools.ui.ScrollUntilTool
import dev.harness.tools.ui.SwipeTool
import dev.harness.tools.ui.TapElementTool
import dev.harness.tools.ui.TapTool
import dev.harness.tools.ui.TypeTextTool
import dev.harness.tools.ui.UiTreeTool
import dev.harness.tools.wait.WaitForLogTool
import dev.harness.tools.wait.WaitForUiTool
import dev.harness.tools.wait.WaitTool

/** Builds the v1 tool set over the given backends. */
object HarnessTools {
    fun registry(shell: ShellBackend, ui: UiBackend, notes: NotesStore,
        clipboard: ClipboardBackend,
    ): ToolRegistry = ToolRegistry(
        listOf(
            // Shell READ
            GetSettingTool(shell),
            LogcatTool(shell),
            DumpsysTool(shell),
            ListPackagesTool(shell),
            AppInfoTool(shell),
            GetPropTool(shell),
            CurrentAppTool(shell),
            DeviceStatusTool(shell),
            NotificationsTool(shell),
            ReadFileTool(shell),
            ListFilesTool(shell),
            // Shell WRITE
            PutSettingTool(shell),
            LaunchAppTool(shell),
            ForceStopTool(shell),
            RunShellTool(shell),
            StartIntentTool(shell),
            WriteFileTool(shell),
            QueryContactsTool(shell),
            QueryCalendarTool(shell),
            QuerySmsTool(shell),
            InstallApkTool(shell),
            UninstallAppTool(shell),
            SetRadioTool(shell),
            // Accessibility READ
            UiTreeTool(ui),
            FindElementTool(ui),
            ScreenshotTool(ui),
            // Accessibility WRITE
            TapTool(ui),
            TapElementTool(ui),
            SwipeTool(ui),
            ScrollUntilTool(ui),
            TypeTextTool(ui),
            PressBackTool(ui),
            PressHomeTool(ui),
            // Agent-side
            NotesTool(notes),
            ClipboardGetTool(clipboard),
            ClipboardSetTool(clipboard),
            // Waiting
            WaitTool(),
            WaitForLogTool(shell),
            WaitForUiTool(ui),
        ),
    )
}
