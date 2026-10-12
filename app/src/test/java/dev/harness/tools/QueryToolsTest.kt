package dev.harness.tools

import dev.harness.tools.shell.QueryCalendarTool
import dev.harness.tools.shell.QueryContactsTool
import dev.harness.tools.shell.QuerySmsTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryToolsTest {

    @Test
    fun `contacts filter escapes quotes at both the SQL and shell level`() = runTest {
        val shell = FakeShell()
        QueryContactsTool(shell).run(buildJsonObject { put("name", "O'Brien") })
        assertEquals(
            "content query --uri content://com.android.contacts/data/phones --projection display_name:data1 " +
                "--where 'display_name LIKE '\\''%O'\\'''\\''Brien%'\\''' | head -n 20",
            shell.commands.single(),
        )
    }

    @Test
    fun `calendar asks for a window starting now`() = runTest {
        val shell = FakeShell()
        QueryCalendarTool(shell, now = { 1_000L }).run(buildJsonObject { put("days", 2) })
        assertEquals(
            "content query --uri content://com.android.calendar/instances/when/1000/172801000 " +
                "--projection title:begin:end:eventLocation | head -n 50",
            shell.commands.single(),
        )
    }

    @Test
    fun `sms is newest first, capped, and never remembered`() = runTest {
        val shell = FakeShell()
        val tool = QuerySmsTool(shell)
        tool.run(buildJsonObject { put("limit", 500) })
        assertEquals(
            "content query --uri content://sms/inbox --projection address:body:date " +
                "--sort 'date DESC' | head -n 50",
            shell.commands.single(),
        )
        assertFalse(tool.canAlwaysAllow)
    }

    @Test
    fun `private data queries are approval gated`() {
        val shell = FakeShell()
        listOf(QueryContactsTool(shell), QueryCalendarTool(shell), QuerySmsTool(shell)).forEach {
            assertTrue(it.name, it.risk == Risk.WRITE)
        }
    }
}
