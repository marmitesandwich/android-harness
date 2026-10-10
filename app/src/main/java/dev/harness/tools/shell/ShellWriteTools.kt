package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** `settings put <namespace> <key> <value>` */
class PutSettingTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "put_setting"
    override val description = "Change an Android setting (system, secure or global). Example: put_setting(system, screen_timeout, 60000)."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "namespace" to Params.str("One of: system, secure, global"),
        "key" to Params.str("Setting key, e.g. screen_timeout"),
        "value" to Params.str("New value as text"),
        required = listOf("namespace", "key", "value"),
    )

    override suspend fun run(args: JsonObject): String {
        val namespace = Args.str(args, "namespace")
        require(namespace in SETTING_NAMESPACES) { "namespace must be one of $SETTING_NAMESPACES" }
        val key = Args.str(args, "key")
        val value = Args.str(args, "value")
        val result = sh("settings put $namespace ${Shell.quote(key)} ${Shell.quote(value)}")
        return if (result.startsWith("ERROR")) result else "OK: $namespace/$key = $value"
    }
}

/** `am start` for a package, using its launcher activity. */
class LaunchAppTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "launch_app"
    override val description = "Launch an app by package name (uses its launcher activity). Example: launch_app(com.android.settings)."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "package" to Params.str("Package name to launch"),
        "activity" to Params.str("Optional fully-qualified activity, e.g. .Settings"),
        required = listOf("package"),
    )

    override suspend fun run(args: JsonObject): String {
        val pkg = Args.str(args, "package")
        val activity = Args.optStr(args, "activity")?.takeIf { it.isNotBlank() }
        val command = if (activity != null) {
            val component = if (activity.startsWith(".")) "$pkg$activity" else activity
            "am start -n ${Shell.quote("$pkg/$component")}"
        } else {
            "monkey -p ${Shell.quote(pkg)} -c android.intent.category.LAUNCHER 1"
        }
        return sh(command)
    }
}

/** `am force-stop <pkg>` */
class ForceStopTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "force_stop"
    override val description = "Force-stop an app by package name. Example: force_stop(com.example.app)."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "package" to Params.str("Package name to stop"),
        required = listOf("package"),
    )

    override suspend fun run(args: JsonObject): String {
        val pkg = Args.str(args, "package")
        val result = sh("am force-stop ${Shell.quote(pkg)}")
        return if (result.startsWith("ERROR")) result else "OK: force-stopped $pkg"
    }
}

/** Arbitrary shell. Always WRITE and never eligible for "always allow". */
class RunShellTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "run_shell"
    override val description = "Run an arbitrary shell command as the shell user. Use only when no specific tool fits."
    override val risk = Risk.WRITE
    override val canAlwaysAllow = false
    override val jsonSchema = Params.obj(
        "command" to Params.str("Shell command to run"),
        required = listOf("command"),
    )

    override suspend fun run(args: JsonObject): String = sh(Args.str(args, "command"))
}
