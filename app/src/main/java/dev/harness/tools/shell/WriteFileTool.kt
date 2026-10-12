package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

private const val MAX_WRITE_CHARS = 8_000

/** Write or append text to a file on shared storage. Always asks: it can overwrite user files. */
class WriteFileTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "write_file"
    override val description =
        "Write text to a file under /sdcard, /storage/emulated/0 or /data/local/tmp. " +
            "Overwrites unless append is true. At most $MAX_WRITE_CHARS characters."
    override val risk = Risk.WRITE
    override val canAlwaysAllow = false
    override val jsonSchema = Params.obj(
        "path" to Params.str("Absolute file path"),
        "content" to Params.str("Text to write"),
        "append" to Params.bool("Append instead of overwrite (default false)"),
        required = listOf("path", "content"),
    )

    override suspend fun run(args: JsonObject): String {
        val path = FilePaths.check(Args.str(args, "path"))
        val content = Args.str(args, "content")
        require(content.length <= MAX_WRITE_CHARS) { "content is over $MAX_WRITE_CHARS characters" }
        val redirect = if (Args.optStr(args, "append")?.toBooleanStrictOrNull() == true) ">>" else ">"
        val result = sh("printf '%s' ${Shell.quote(content)} $redirect ${Shell.quote(path)}")
        return if (result == "(no output)") "OK: wrote ${content.length} chars to $path" else result
    }
}
