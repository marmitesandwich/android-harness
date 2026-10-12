package dev.harness.tools.shell

import dev.harness.backend.ShellBackend
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** Active notifications, read from dumpsys so no NotificationListenerService is needed. */
class NotificationsTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "notifications"
    override val description = "List active notifications (package, title, text)."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String =
        sh("dumpsys notification --noredact | grep -E 'pkg=|android.title=|android.text=' | head -n 150")
}
