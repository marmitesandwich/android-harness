package dev.harness.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.harness.R
import dev.harness.ui.MainActivity

/** Keeps Harness's process alive while a run is going; some phones kill backgrounded apps within minutes. */
interface RunKeeper {
    fun hold()
    fun release()
}

class AndroidRunKeeper(private val context: Context) : RunKeeper {
    override fun hold() {
        try {
            context.startForegroundService(Intent(context, RunService::class.java))
        } catch (_: Exception) {
            // Not allowed to start from here (app long in background): the run still goes on, just killable.
        }
    }

    override fun release() {
        context.stopService(Intent(context, RunService::class.java))
    }
}

class RunService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent running", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Harness is working")
            .setContentText("Tap to return to the chat")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL = "run"
        const val ID = 1
    }
}
