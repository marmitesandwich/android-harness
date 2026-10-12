package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

/** SQL string literal for a LIKE pattern: wrap in % and double any quote. */
internal fun sqlLike(value: String): String = "'%" + value.replace("'", "''") + "%'"

private fun limit(args: JsonObject, default: Int, max: Int) = Args.optInt(args, "limit", default).coerceIn(1, max)

// These read private data that then goes to the LLM provider, so they are WRITE-gated:
// the user sees and approves each query, even though nothing is modified.

/** Contacts by name, through the `content` shell command (no READ_CONTACTS grant needed). */
class QueryContactsTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "query_contacts"
    override val description = "Look up contacts' names and phone numbers, optionally filtered by name."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "name" to Params.str("Name fragment to filter by"),
        "limit" to Params.int("Max rows (1-100, default 20)"),
    )

    override suspend fun run(args: JsonObject): String {
        val where = Args.optStr(args, "name")?.takeIf { it.isNotBlank() }
            ?.let { " --where ${Shell.quote("display_name LIKE ${sqlLike(it)}")}" }.orEmpty()
        return sh(
            "content query --uri content://com.android.contacts/data/phones " +
                "--projection display_name:data1$where | head -n ${limit(args, 20, 100)}",
        )
    }
}

/** Upcoming calendar instances. */
class QueryCalendarTool(
    shell: ShellBackend,
    private val now: () -> Long = System::currentTimeMillis,
) : ShellTool(shell) {
    override val name = "query_calendar"
    override val description = "List calendar events starting from now for the next N days."
    override val risk = Risk.WRITE
    override val jsonSchema = Params.obj(
        "days" to Params.int("How many days ahead (1-90, default 7)"),
        "limit" to Params.int("Max rows (1-100, default 50)"),
    )

    override suspend fun run(args: JsonObject): String {
        val begin = now()
        val end = begin + Args.optInt(args, "days", 7).coerceIn(1, 90) * 86_400_000L
        return sh(
            "content query --uri content://com.android.calendar/instances/when/$begin/$end " +
                "--projection title:begin:end:eventLocation | head -n ${limit(args, 50, 100)}",
        )
    }
}

/** Inbox text messages, newest first. Never remembered: message bodies are the most sensitive data here. */
class QuerySmsTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "query_sms"
    override val description = "Read recent inbox SMS (sender, body, date), newest first, optionally from one sender."
    override val risk = Risk.WRITE
    override val canAlwaysAllow = false
    override val jsonSchema = Params.obj(
        "from" to Params.str("Sender number or name fragment"),
        "limit" to Params.int("Max messages (1-50, default 10)"),
    )

    override suspend fun run(args: JsonObject): String {
        val where = Args.optStr(args, "from")?.takeIf { it.isNotBlank() }
            ?.let { " --where ${Shell.quote("address LIKE ${sqlLike(it)}")}" }.orEmpty()
        return sh(
            "content query --uri content://sms/inbox --projection address:body:date$where " +
                "--sort ${Shell.quote("date DESC")} | head -n ${limit(args, 10, 50)}",
        )
    }
}
