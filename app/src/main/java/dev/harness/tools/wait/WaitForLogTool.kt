package dev.harness.tools.wait

import dev.harness.backend.SHIZUKU_UNAVAILABLE
import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import dev.harness.tools.shell.ShellTool
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

private const val POLL_SECONDS = 1

/** Epoch seconds from a `logcat -v epoch` line, or null for headers and blanks. */
internal fun logTimestamp(line: String): Double? = line.trimStart().substringBefore(' ').toDoubleOrNull()

/**
 * Blocks until a log line matching [pattern] appears, so the agent does not burn
 * a step per poll. Only lines logged after the call starts count.
 */
class WaitForLogTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "wait_for_log"
    override val description =
        "Wait until a new logcat line matching a regex appears (case-insensitive). Lines already in the log " +
            "when the call starts are ignored. Returns the first match, or a timeout message."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "pattern" to Params.str("Regex to look for in new log lines"),
        "timeout_s" to Params.int("Give up after this many seconds (1-$MAX_WAIT_SECONDS, default 30)"),
        "filter" to Params.str("Optional logcat filter spec, e.g. 'MyTag:I *:S'"),
        required = listOf("pattern"),
    )

    override suspend fun run(args: JsonObject): String {
        val pattern = Args.str(args, "pattern")
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val timeout = Args.optInt(args, "timeout_s", 30).coerceIn(1, MAX_WAIT_SECONDS)
        val filter = Args.optStr(args, "filter")?.takeIf { it.isNotBlank() }?.let { " ${Shell.quote(it)}" }.orEmpty()
        if (!shell.available) return SHIZUKU_UNAVAILABLE

        // ponytail: ms-resolution cursor; a line sharing the exact timestamp of the last one seen can be missed.
        var cursor: Double? = null
        var waited = 0
        while (true) {
            val lines = shell.exec("logcat -d -v epoch -t 2000$filter").stdout.lines()
            val stamped = lines.mapNotNull { line -> logTimestamp(line)?.let { it to line } }
            val seen = cursor
            if (seen == null) {
                cursor = stamped.maxOfOrNull { it.first } ?: 0.0
            } else {
                val fresh = stamped.filter { it.first > seen }
                fresh.firstOrNull { regex.containsMatchIn(it.second) }?.let { return "MATCH: ${it.second.trim()}" }
                cursor = maxOf(seen, fresh.maxOfOrNull { it.first } ?: seen)
            }
            if (waited >= timeout) return "TIMEOUT: no new log line matching '$pattern' within ${timeout}s"
            delay(POLL_SECONDS * 1000L)
            waited += POLL_SECONDS
        }
    }
}
