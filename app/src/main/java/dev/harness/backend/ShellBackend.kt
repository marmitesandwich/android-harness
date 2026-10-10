package dev.harness.backend

/** Result of running a command through [ShellBackend]. */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    /** Model-readable rendering; the exit code is shown only when non-zero. */
    fun render(): String = buildString {
        if (stdout.isNotBlank()) append(stdout.trimEnd())
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append("stderr:\n").append(stderr.trimEnd())
        }
        if (exitCode != 0) {
            if (isNotEmpty()) append('\n')
            append("exit code: ").append(exitCode)
        }
        if (isEmpty()) append("(no output)")
    }
}

/**
 * Runs shell commands as the shell user. Implemented over Shizuku so the app
 * does not need root; `available` is true only when Shizuku is running and the
 * user granted this app permission.
 */
interface ShellBackend {
    val available: Boolean
    suspend fun exec(command: String): ShellResult
}

/** POSIX single-quote escaping for `sh -c` argument strings. */
object Shell {
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

/** Used when Shizuku is missing so tools return a clear, actionable message. */
class UnavailableShellBackend(private val reason: String) : ShellBackend {
    override val available: Boolean = false
    override suspend fun exec(command: String): ShellResult = ShellResult(-1, "", reason)
}
