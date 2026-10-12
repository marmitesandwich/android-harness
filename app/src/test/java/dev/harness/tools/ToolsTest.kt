package dev.harness.tools

import dev.harness.backend.SHIZUKU_UNAVAILABLE
import dev.harness.backend.Screenshot
import dev.harness.backend.ShellBackend
import dev.harness.backend.ShellResult
import dev.harness.backend.UiBackend
import dev.harness.tools.shell.GetSettingTool
import dev.harness.tools.shell.ListPackagesTool
import dev.harness.tools.shell.PutSettingTool
import dev.harness.tools.shell.RunShellTool
import dev.harness.tools.ui.ACCESSIBILITY_UNAVAILABLE
import dev.harness.tools.ui.TapTool
import dev.harness.tools.ui.UiTreeTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeShell(
    override val available: Boolean = true,
    var result: ShellResult = ShellResult(0, "ok", ""),
) : ShellBackend {
    val commands = mutableListOf<String>()
    override suspend fun exec(command: String): ShellResult {
        commands += command
        return result
    }
}

internal class FakeUi(override val available: Boolean = true) : UiBackend {
    val taps = mutableListOf<Pair<Int, Int>>()
    override suspend fun windowTree(): String = "root"
    override suspend fun screenshot(): Screenshot = Screenshot("/tmp/x.png", 1080, 1920)
    override suspend fun tap(x: Int, y: Int) { taps += x to y }
    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long) = Unit
    override suspend fun typeText(text: String) = Unit
    override suspend fun pressBack() = Unit
    override suspend fun pressHome() = Unit
}

class ShellToolsTest {

    @Test
    fun `get_setting quotes the key`() = runTest {
        val shell = FakeShell(result = ShellResult(0, "60000", ""))
        val output = GetSettingTool(shell).run(buildJsonObject {
            put("namespace", "system")
            put("key", "screen_timeout")
        })
        assertEquals("settings get system 'screen_timeout'", shell.commands.single())
        assertEquals("60000", output)
    }

    @Test
    fun `get_setting rejects an unknown namespace`() = runTest {
        val error = runCatching {
            GetSettingTool(FakeShell()).run(buildJsonObject {
                put("namespace", "bogus")
                put("key", "k")
            })
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `shell tools report unavailability instead of running`() = runTest {
        val output = GetSettingTool(FakeShell(available = false)).run(buildJsonObject {
            put("namespace", "system")
            put("key", "k")
        })
        assertEquals(SHIZUKU_UNAVAILABLE, output)
    }

    @Test
    fun `put_setting confirms with a summary`() = runTest {
        val shell = FakeShell(result = ShellResult(0, "", ""))
        val output = PutSettingTool(shell).run(buildJsonObject {
            put("namespace", "system")
            put("key", "screen_timeout")
            put("value", "60000")
        })
        assertEquals("OK: system/screen_timeout = 60000", output)
        assertEquals("settings put system 'screen_timeout' '60000'", shell.commands.single())
    }

    @Test
    fun `list_packages defaults to user apps`() = runTest {
        val shell = FakeShell()
        ListPackagesTool(shell).run(buildJsonObject {})
        assertEquals("pm list packages -3", shell.commands.single())
    }

    @Test
    fun `list_packages includes system apps when asked`() = runTest {
        val shell = FakeShell()
        ListPackagesTool(shell).run(buildJsonObject { put("include_system", true) })
        assertEquals("pm list packages", shell.commands.single())
    }

    @Test
    fun `run_shell is never eligible for always allow`() {
        assertFalse(RunShellTool(FakeShell()).canAlwaysAllow)
    }
}

class UiToolsTest {

    @Test
    fun `tap is forwarded to the backend`() = runTest {
        val ui = FakeUi()
        val output = TapTool(ui).run(buildJsonObject { put("x", 10); put("y", 20) })
        assertEquals("OK: tapped (10, 20)", output)
        assertEquals(listOf(10 to 20), ui.taps)
    }

    @Test
    fun `ui tools report unavailable accessibility`() = runTest {
        val output = UiTreeTool(FakeUi(available = false)).run(buildJsonObject {})
        assertEquals(ACCESSIBILITY_UNAVAILABLE, output)
    }
}

class HarnessToolsTest {

    @Test
    fun `registry exposes the full v1 tool set without duplicates`() {
        val registry = HarnessTools.registry(FakeShell(), FakeUi())
        val names = registry.all().map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.containsAll(listOf("get_setting", "run_shell", "ui_tree", "tap", "press_home")))
        assertEquals(25, names.size)
    }

    @Test
    fun `run_shell is the only write tool that cannot be remembered`() {
        val registry = HarnessTools.registry(FakeShell(), FakeUi())
        val blocked = registry.all().filter { it.risk == Risk.WRITE && !it.canAlwaysAllow }.map { it.name }
        assertEquals(listOf("run_shell"), blocked)
    }
}
