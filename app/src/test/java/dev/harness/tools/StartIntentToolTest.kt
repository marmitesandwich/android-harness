package dev.harness.tools

import dev.harness.tools.shell.StartIntentTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartIntentToolTest {

    @Test
    fun `opens a url with every value quoted`() = runTest {
        val shell = FakeShell()
        StartIntentTool(shell).run(buildJsonObject { put("data", "https://example.com/?a=1&b=it's") })
        assertEquals("am start -d 'https://example.com/?a=1&b=it'\\''s'", shell.commands.single())
    }

    @Test
    fun `combines action and package`() = runTest {
        val shell = FakeShell()
        StartIntentTool(shell).run(buildJsonObject {
            put("action", "android.settings.WIFI_SETTINGS")
            put("package", "com.android.settings")
        })
        assertEquals("am start -a 'android.settings.WIFI_SETTINGS' -p 'com.android.settings'", shell.commands.single())
    }

    @Test
    fun `needs something to start`() = runTest {
        val error = runCatching { StartIntentTool(FakeShell()).run(buildJsonObject { put("package", "x.y") }) }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }
}
