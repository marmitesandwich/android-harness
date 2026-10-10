package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** `settings get <namespace> <key>` */
class GetSettingTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "get_setting"
    override val description = "Read an Android setting (system, secure or global). Example: get_setting(system, screen_off_timeout)"
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "namespace" to Params.str("One of: system, secure, global"),
        "key" to Params.str("Setting key, e.g. screen_off_timeout"),
        required = listOf("namespace", "key"),
    )

    override suspend fun run(args: JsonObject): String {
        val namespace = Args.str(args, "namespace")
        require(namespace in SETTING_NAMESPACES) { "namespace must be one of $SETTING_NAMESPACES" }
        val key = Args.str(args, "key")
        return sh("settings get $namespace ${Shell.quote(key)}")
    }
}

/** `logcat -d -t <lines> [filter]` */
class LogcatTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "logcat"
    override val description = "Dump recent logcat lines. Optional filter is a logcat filter spec such as 'ActivityManager:I *:S'."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "lines" to Params.int("How many recent lines to dump (1-2000, default 200)"),
        "filter" to Params.str("Optional logcat filter specification"),
        required = emptyList(),
    )

    override suspend fun run(args: JsonObject): String {
        val lines = Args.optInt(args, "lines", 200).coerceIn(1, 2000)
        val filter = Args.optStr(args, "filter")?.takeIf { it.isNotBlank() }?.let { " ${Shell.quote(it)}" }.orEmpty()
        return sh("logcat -d -t $lines$filter")
    }
}

/** `dumpsys <service>` */
class DumpsysTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "dumpsys"
    override val description = "Run dumpsys for a system service, e.g. dumpsys(activity), dumpsys(battery)."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "service" to Params.str("Service name, e.g. activity, battery, wifi"),
        "extra" to Params.str("Optional extra argument for the service"),
        required = listOf("service"),
    )

    override suspend fun run(args: JsonObject): String {
        val service = Args.str(args, "service")
        val extra = Args.optStr(args, "extra")?.takeIf { it.isNotBlank() }?.let { " ${Shell.quote(it)}" }.orEmpty()
        return sh("dumpsys ${Shell.quote(service)}$extra")
    }
}

/** `pm list packages [-3] [query]` */
class ListPackagesTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "list_packages"
    override val description = "List installed packages. Set include_system=true to include system apps, and query to filter by substring."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "include_system" to Params.bool("Include system packages (default false, user apps only)"),
        "query" to Params.str("Optional package name substring filter"),
        required = emptyList(),
    )

    override suspend fun run(args: JsonObject): String {
        val includeSystem = Args.optStr(args, "include_system")?.toBooleanStrictOrNull() ?: false
        val query = Args.optStr(args, "query")?.takeIf { it.isNotBlank() }?.let { " ${Shell.quote(it)}" }.orEmpty()
        val flags = if (includeSystem) "" else " -3"
        return sh("pm list packages$flags$query")
    }
}

/** `dumpsys package <pkg>` */
class AppInfoTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "app_info"
    override val description = "Show installed-package details (version, permissions, activities) for one package."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "package" to Params.str("Package name, e.g. com.android.settings"),
        required = listOf("package"),
    )

    override suspend fun run(args: JsonObject): String {
        val pkg = Args.str(args, "package")
        return sh("dumpsys package ${Shell.quote(pkg)}")
    }
}

/** `getprop [name]` */
class GetPropTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "getprop"
    override val description = "Read system properties. Omit name to list all (large). Example: getprop(ro.build.version.release)."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "name" to Params.str("Optional property name; omit to list all properties"),
        required = emptyList(),
    )

    override suspend fun run(args: JsonObject): String {
        val name = Args.optStr(args, "name")?.takeIf { it.isNotBlank() }
        return if (name == null) sh("getprop") else sh("getprop ${Shell.quote(name)}")
    }
}
