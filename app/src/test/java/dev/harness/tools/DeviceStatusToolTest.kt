package dev.harness.tools

import dev.harness.tools.shell.DeviceStatusTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusToolTest {

    @Test
    fun `gathers every section in one shell call`() = runTest {
        val shell = FakeShell()
        DeviceStatusTool(shell).run(buildJsonObject {})
        val command = shell.commands.single()
        assertEquals(DeviceStatusTool.COMMAND, command)
        listOf("battery", "network", "storage", "screen").forEach { assertTrue(command.contains("## $it")) }
    }
}
