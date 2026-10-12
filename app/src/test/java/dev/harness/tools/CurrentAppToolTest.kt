package dev.harness.tools

import dev.harness.backend.ShellResult
import dev.harness.tools.shell.CurrentAppTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentAppToolTest {

    @Test
    fun `asks dumpsys for the focused window`() = runTest {
        val focus = "mCurrentFocus=Window{1 u0 dev.harness/dev.harness.ui.MainActivity}"
        val shell = FakeShell(result = ShellResult(0, focus, ""))
        assertEquals(focus, CurrentAppTool(shell).run(buildJsonObject {}))
        assertEquals("dumpsys window | grep -m1 mCurrentFocus", shell.commands.single())
    }
}
