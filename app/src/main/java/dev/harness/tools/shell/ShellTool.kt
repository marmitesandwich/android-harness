package dev.harness.tools.shell

import dev.harness.backend.ShellBackend
import dev.harness.tools.Tool

internal const val SHIZUKU_UNAVAILABLE =
    "ERROR: Shizuku is not running or permission not granted. Open Harness to pair Shizuku."

internal val SETTING_NAMESPACES = setOf("system", "secure", "global")

/** Base for tools that run a command through the Shizuku shell backend. */
abstract class ShellTool(protected val shell: ShellBackend) : Tool {

    protected suspend fun sh(command: String): String =
        if (!shell.available) SHIZUKU_UNAVAILABLE else shell.exec(command).render()
}
