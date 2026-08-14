package ai.anya.companion.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.anya.companion.MainActivity
import ai.anya.companion.R
import ai.anya.companion.core.domain.download.DownloadNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts background file-download progress/outcome as system notifications.
 * Notification id is derived from `offerId` so progress updates overwrite the
 * same notification.
 */
@Singleton
public class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadNotifier {

    private fun manager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "文件下载", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "桌面文件下载进度" },
            )
        }
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    override fun showProgress(offerId: String, name: String, percent: Int) {
        if (!allowed()) return
        ensureChannel()
        val id = offerId.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("下载文件")
            .setContentText("$name · $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(id))
            .build()
        manager().notify(id, notification)
    }

    override fun showDone(offerId: String, name: String) {
        manager().cancel(offerId.hashCode())
    }

    override fun showFailed(offerId: String, name: String) {
        if (!allowed()) return
        ensureChannel()
        val id = offerId.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("下载失败")
            .setContentText(name)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(id))
            .build()
        manager().notify(id, notification)
    }

    private fun openAppIntent(id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "anya_file_download"
    }
}
