package ai.anya.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keep the process alive while the app is in background.
 *
 * Without a foreground service, Android may suspend/kill the WebSocket,
 * causing missed desktop-triggered ask/tool-approval interactions.
 */
class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTI_ID,
            buildOngoingNotification(),
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Anya 常驻连接",
            NotificationManager.IMPORTANCE_LOW,
        )
        ch.description = "用于保持远程连接常驻运行"
        nm.createNotificationChannel(ch)
    }

    private fun buildOngoingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Anya 远程连接中")
            .setContentText("请勿关闭后台限制以确保随时提醒")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "anya_keep_alive"
        const val NOTI_ID = 2001
    }
}

