package dev.harness.tools.shell

import dev.harness.backend.ShellBackend
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** Which window has focus right now. */
class CurrentAppTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "current_app"
    override val description = "Report the foreground app and activity (the window that has focus)."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String = sh("dumpsys window | grep -m1 mCurrentFocus")
}
