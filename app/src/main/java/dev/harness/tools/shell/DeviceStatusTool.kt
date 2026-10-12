package dev.harness.tools.shell

import dev.harness.backend.ShellBackend
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** Battery, connectivity, storage and screen state in one call instead of four dumpsys round-trips. */
class DeviceStatusTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "device_status"
    override val description = "Summarise battery, network, free storage, screen power state and rotation."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj()

    override suspend fun run(args: JsonObject): String = sh(COMMAND)

    internal companion object {
        // Each section is best-effort: a missing grep match just leaves the section empty.
        const val COMMAND =
            "echo '## battery'; dumpsys battery | grep -E 'level|status|powered|temperature'; " +
                "echo '## network'; dumpsys connectivity | grep -m1 'Active default network'; " +
                "dumpsys wifi | grep -m1 'Wi-Fi is'; " +
                "echo '## storage'; df -h /data | tail -n 1; " +
                "echo '## screen'; dumpsys power | grep -m1 'mWakefulness='; " +
                "dumpsys window displays | grep -m1 -oE 'rotation=[0-9]|mRotation=[0-9]'"
    }
}
