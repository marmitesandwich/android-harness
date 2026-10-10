package dev.harness.backend

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Executes commands through Shizuku's `newProcess`, which runs as the shell
 * (uid 2000) user. Requires the Shizuku service to be running and this app to
 * hold Shizuku permission.
 */
class ShizukuShellBackend : ShellBackend {

    override val available: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!available) {
            return@withContext ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku is not running or permission was not granted. Open the app and pair Shizuku.",
            )
        }
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            // Drain both pipes concurrently so a full buffer cannot deadlock.
            val stderrDeferred = async { process.errorStream.bufferedReader().readText() }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = stderrDeferred.await()
            val exit = process.waitFor()
            ShellResult(exit, stdout, stderr)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            ShellResult(-1, "", "Shizuku exec failed: ${t.message ?: t::class.simpleName}")
        }
    }
}
