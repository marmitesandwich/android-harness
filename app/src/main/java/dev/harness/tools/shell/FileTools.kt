package dev.harness.tools.shell

import dev.harness.backend.Shell
import dev.harness.backend.ShellBackend
import dev.harness.tools.Args
import dev.harness.tools.Params
import dev.harness.tools.Risk
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_READ_BYTES = 8_000
private const val MAX_READ_BYTES = 16_000

/** Read the start of a file on shared storage. */
class ReadFileTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "read_file"
    override val description =
        "Read the first bytes of a text file under /sdcard, /storage/emulated/0 or /data/local/tmp."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj(
        "path" to Params.str("Absolute file path"),
        "max_bytes" to Params.int("Bytes to read (1-$MAX_READ_BYTES, default $DEFAULT_READ_BYTES)"),
        required = listOf("path"),
    )

    override suspend fun run(args: JsonObject): String {
        val path = FilePaths.check(Args.str(args, "path"))
        val bytes = Args.optInt(args, "max_bytes", DEFAULT_READ_BYTES).coerceIn(1, MAX_READ_BYTES)
        return sh("head -c $bytes ${Shell.quote(path)}")
    }
}

/** List a directory on shared storage. */
class ListFilesTool(shell: ShellBackend) : ShellTool(shell) {
    override val name = "list_files"
    override val description =
        "List a directory under /sdcard, /storage/emulated/0 or /data/local/tmp (default /sdcard/)."
    override val risk = Risk.READ
    override val jsonSchema = Params.obj("path" to Params.str("Absolute directory path"))

    override suspend fun run(args: JsonObject): String {
        val path = FilePaths.check(Args.optStr(args, "path") ?: "/sdcard/")
        return sh("ls -la ${Shell.quote(path)} | head -n 200")
    }
}
