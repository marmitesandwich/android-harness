package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

/** Install an APK from shared storage over the existing install. Always asks. */
class InstallApkTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "install_apk"
    override val description =
        "Install (or update, keeping data) an APK file located under /sdcard, /storage/emulated/0 or /data/local/tmp."
    override val risk = Risk.WRITE
    override val canAlwaysAllow = false
    override val jsonSchema = Params.obj(
        "path" to Params.str("Absolute path of the .apk file"),
        required = listOf("path"),
    )

    override suspend fun run(args: JsonObject): String {
        val path = FilePaths.check(Args.str(args, "path"))
        require(path.endsWith(".apk", ignoreCase = true)) { "path must be an .apk file" }
        return sh("pm install -r ${Shell.quote(path)}")
    }
}

/** Uninstall an app. Keeps its data and cache by default so the action can be undone by reinstalling. */
class UninstallAppTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "uninstall_app"
    override val description =
        "Uninstall an app by package name. Keeps the app's data by default (keep_data=true); " +
            "set keep_data=false only if the user asked to erase it."
    override val risk = Risk.WRITE
    override val canAlwaysAllow = false
    override val jsonSchema = Params.obj(
        "package" to Params.str("Package name, e.g. com.example.app"),
        "keep_data" to Params.bool("Keep app data and cache (default true)"),
        required = listOf("package"),
    )

    override suspend fun run(args: JsonObject): String {
        val pkg = Args.str(args, "package").trim()
        require(PACKAGE_NAME.matches(pkg)) { "not a valid package name: $pkg" }
        val keep = Args.optStr(args, "keep_data")?.toBooleanStrictOrNull() ?: true
        return sh("pm uninstall${if (keep) " -k" else ""} $pkg")
    }
}

/** Wi-Fi, Bluetooth or mobile data on/off through `svc`. */
class SetRadioTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "set_radio"
    override val description = "Turn Wi-Fi, Bluetooth or mobile data on or off."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "radio" to Params.str("'wifi', 'bluetooth' or 'data'"),
        "enabled" to Params.bool("true to turn on, false to turn off"),
        required = listOf("radio", "enabled"),
    )

    override suspend fun run(args: JsonObject): String {
        val radio = Args.str(args, "radio").lowercase()
        require(radio in RADIOS) { "radio must be one of ${RADIOS.joinToString()}" }
        val enabled = Args.str(args, "enabled").toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("'enabled' must be true or false")
        val result = sh("svc $radio ${if (enabled) "enable" else "disable"}")
        return if (result == "(no output)") "OK: $radio ${if (enabled) "on" else "off"}" else result
    }

    private companion object {
        val RADIOS = listOf("wifi", "bluetooth", "data")
    }
}
