package dev.harness.tools

import dev.harness.tools.shell.NotificationsTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationsToolTest {

    @Test
    fun `reads unredacted titles and text and caps the output`() = runTest {
        val shell = FakeShell()
        NotificationsTool(shell).run(buildJsonObject {})
        assertEquals(
            "dumpsys notification --noredact | grep -E 'pkg=|android.title=|android.text=' | head -n 150",
            shell.commands.single(),
        )
    }
}
