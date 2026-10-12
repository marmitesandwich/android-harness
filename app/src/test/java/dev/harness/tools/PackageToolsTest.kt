package dev.harness.tools

import dev.harness.backend.ShellResult
import dev.harness.tools.shell.InstallApkTool
import dev.harness.tools.shell.SetRadioTool
import dev.harness.tools.shell.UninstallAppTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageToolsTest {

    @Test
    fun `install_apk replaces in place and only takes apks from shared storage`() = runTest {
        val shell = FakeShell()
        InstallApkTool(shell).run(buildJsonObject { put("path", "/sdcard/Download/app.apk") })
        assertEquals("pm install -r '/sdcard/Download/app.apk'", shell.commands.single())

        val notApk = runCatching { InstallApkTool(shell).run(buildJsonObject { put("path", "/sdcard/x.txt") }) }
        assertTrue(notApk.exceptionOrNull() is IllegalArgumentException)
        assertFalse(InstallApkTool(shell).canAlwaysAllow)
    }

    @Test
    fun `uninstall keeps data unless told otherwise`() = runTest {
        val shell = FakeShell()
        UninstallAppTool(shell).run(buildJsonObject { put("package", "com.example.app") })
        UninstallAppTool(shell).run(buildJsonObject { put("package", "com.example.app"); put("keep_data", false) })
        assertEquals(listOf("pm uninstall -k com.example.app", "pm uninstall com.example.app"), shell.commands)
    }

    @Test
    fun `uninstall rejects anything that is not a package name`() = runTest {
        val shell = FakeShell()
        listOf("com.example; rm -rf /sdcard", "nodots", "a b.c").forEach { pkg ->
            val error = runCatching { UninstallAppTool(shell).run(buildJsonObject { put("package", pkg) }) }
            assertTrue(pkg, error.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(shell.commands.isEmpty())
        assertFalse(UninstallAppTool(shell).canAlwaysAllow)
    }

    @Test
    fun `set_radio maps to svc`() = runTest {
        val shell = FakeShell(result = ShellResult(0, "", ""))
        val output = SetRadioTool(shell).run(buildJsonObject { put("radio", "wifi"); put("enabled", false) })
        assertEquals("svc wifi disable", shell.commands.single())
        assertEquals("OK: wifi off", output)
    }

    @Test
    fun `set_radio rejects unknown radios`() = runTest {
        val error = runCatching {
            SetRadioTool(FakeShell()).run(buildJsonObject { put("radio", "nfc"); put("enabled", true) })
        }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }
}
