package dev.harness.backend

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Executes commands as the shell user by binding a Shizuku [ShellUserService].
 *
 * The service is bound lazily on the first command and cached. Binding requires
 * the Shizuku service to be running and this app to hold Shizuku permission.
 */
class ShizukuShellBackend(private val context: Context) : ShellBackend {

    private val mutex = Mutex()

    @Volatile
    private var service: IShellService? = null

    private var pendingConnection: CompletableDeferred<IShellService?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val resolved = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
            service = resolved
            pendingConnection?.complete(resolved)
            pendingConnection = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .version(SERVICE_VERSION)

    override val available: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!available) return@withContext ShellResult(-1, "", SHIZUKU_UNAVAILABLE)
        val bound = bind() ?: return@withContext ShellResult(-1, "", COULD_NOT_CONNECT)
        try {
            val bundle = bound.exec(command)
            ShellResult(
                exitCode = bundle.getInt(ShellUserService.KEY_EXIT_CODE, -1),
                stdout = bundle.getString(ShellUserService.KEY_STDOUT).orEmpty(),
                stderr = bundle.getString(ShellUserService.KEY_STDERR).orEmpty(),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            service = null
            ShellResult(-1, "", "Shizuku exec failed: ${t.message ?: t::class.simpleName}")
        }
    }

    private suspend fun bind(): IShellService? {
        service?.let { return it }
        return mutex.withLock {
            service?.let { return@withLock it }
            val deferred = CompletableDeferred<IShellService?>()
            pendingConnection = deferred
            val requested = try {
                Shizuku.bindUserService(userServiceArgs, connection)
                true
            } catch (_: Throwable) {
                pendingConnection = null
                false
            }
            if (!requested) return@withLock null
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deferred.await() }
        }
    }

    private companion object {
        const val SERVICE_VERSION = 1
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val COULD_NOT_CONNECT = "Could not connect to the Shizuku shell service."
    }
}
