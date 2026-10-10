package dev.harness.backend

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep

/**
 * Runs inside the Shizuku user-service process, i.e. as the shell user.
 *
 * Shizuku instantiates this class by reflection, so the constructors must be
 * kept and the class must extend the generated [IShellService.Stub].
 */
class ShellUserService : IShellService.Stub() {

    @Keep
    @Suppress("unused")
    constructor()

    @Keep
    @Suppress("unused")
    constructor(context: Context) {
        // Context is supplied by Shizuku; nothing needed here.
        context.packageName
    }

    override fun destroy() {
        System.exit(0)
    }

    override fun exec(command: String): Bundle {
        val result = Bundle()
        try {
            val process = ProcessBuilder("sh", "-c", command).start()

            // Drain stderr on a separate thread so a full pipe cannot deadlock.
            var stderr = ""
            val stderrThread = Thread { stderr = process.errorStream.bufferedReader().readText() }
            stderrThread.start()

            val stdout = process.inputStream.bufferedReader().readText()
            stderrThread.join()

            result.putInt(KEY_EXIT_CODE, process.waitFor())
            result.putString(KEY_STDOUT, stdout)
            result.putString(KEY_STDERR, stderr)
        } catch (t: Throwable) {
            result.putInt(KEY_EXIT_CODE, -1)
            result.putString(KEY_STDOUT, "")
            result.putString(KEY_STDERR, t.message ?: t::class.simpleName)
        }
        return result
    }

    companion object {
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_STDOUT = "stdout"
        const val KEY_STDERR = "stderr"
    }
}
