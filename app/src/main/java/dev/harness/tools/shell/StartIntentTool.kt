package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** `am start`: deep links, URLs, settings pages and share targets without navigating by hand. */
class StartIntentTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "start_intent"
    override val description =
        "Start an activity with an intent. Give a data URI to open a link (https://..., tel:..., sms:..., " +
            "geo:...), an action such as android.settings.WIFI_SETTINGS, and/or an explicit component."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "data" to Params.str("Data URI, e.g. https://example.com"),
        "action" to Params.str("Intent action, e.g. android.intent.action.VIEW"),
        "package" to Params.str("Restrict to this package"),
        "component" to Params.str("Explicit component 'pkg/.Activity'"),
        "mime_type" to Params.str("MIME type of the data"),
    )

    override suspend fun run(args: JsonObject): String {
        val parts = listOf(
            "-a" to Args.optStr(args, "action"),
            "-d" to Args.optStr(args, "data"),
            "-p" to Args.optStr(args, "package"),
            "-n" to Args.optStr(args, "component"),
            "-t" to Args.optStr(args, "mime_type"),
        ).filter { !it.second.isNullOrBlank() }
        require(parts.any { it.first == "-a" || it.first == "-d" || it.first == "-n" }) {
            "give at least one of data, action or component"
        }
        val flags = parts.joinToString(" ") { (flag, value) -> "$flag ${Shell.quote(value!!)}" }
        return sh("am start $flags")
    }
}
