package dev.harness.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApprovalTextTest {

    @Test
    fun `new write tools get a plain-language title`() {
        listOf(
            "start_intent", "set_radio", "tap_element", "scroll_until", "clipboard_set", "clipboard_get",
            "write_file", "install_apk", "uninstall_app", "query_contacts", "query_calendar", "query_sms", "http_fetch",
        ).forEach { tool ->
            val title = actionTitle(tool)
            assertFalse("$tool has no title", title == tool)
            assertFalse("$tool title looks technical: $title", title.contains('_'))
        }
    }

    @Test
    fun `shell commands are shown as the bare command`() {
        assertEquals("ls -la /sdcard", approvalSummary("run_shell", """{"command":"ls -la /sdcard"}"""))
    }

    @Test
    fun `other arguments are one name and value per line`() {
        assertEquals(
            "radio: wifi\nenabled: false",
            approvalSummary("set_radio", """{"radio":"wifi","enabled":false}"""),
        )
    }

    @Test
    fun `unparseable arguments are shown as they came`() {
        assertEquals("not json", approvalSummary("tap", "not json"))
    }
}
