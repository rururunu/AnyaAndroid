package ai.anya.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ai.anya.companion.core.domain.repository.AppUpdateMonitor
import ai.anya.companion.core.model.update.UpdateDownloadStatus
import ai.anya.companion.notify.CompanionNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateDownloadService : Service() {
    @Inject
    lateinit var updateMonitor: AppUpdateMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTI_ID,
            progressNotification(0, 0),
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        if (downloadJob?.isActive == true) return START_NOT_STICKY
        downloadJob = scope.launch {
            val progressJob = launch {
                updateMonitor.download.collect { state ->
                    if (state.status == UpdateDownloadStatus.Downloading) {
                        notifyNow(progressNotification(state.downloadedBytes, state.totalBytes))
                    }
                }
            }
            updateMonitor.executeDownload()
            progressJob.cancel()
            when (updateMonitor.download.value.status) {
                UpdateDownloadStatus.Ready -> {
                    if (!updateMonitor.launchInstaller()) {
                        notifyNow(readyNotification())
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
                UpdateDownloadStatus.Failed -> {
                    notifyNow(failedNotification())
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                else -> stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notifyNow(notification: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTI_ID, notification)
    }

    private fun progressNotification(downloaded: Long, total: Long): Notification {
        val percent = if (total > 0) {
            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val text = if (total > 0) {
            getString(R.string.update_download_progress, percent)
        } else {
            getString(R.string.update_download_ongoing)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.update_download_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAboutIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (total > 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun readyNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.update_download_ready_title))
            .setContentText(getString(R.string.update_download_ready_body))
            .setAutoCancel(true)
            .setContentIntent(openAboutIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun failedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.update_download_failed_title))
            .setContentText(getString(R.string.update_download_failed_body))
            .setAutoCancel(true)
            .setContentIntent(openAboutIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun openAboutIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CompanionNotifier.EXTRA_OPEN_ABOUT, true)
        }
        return PendingIntent.getActivity(
            this,
            NOTI_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.update_download_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.update_download_channel_desc)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "anya_update_download"
        const val NOTI_ID = 4102
    }
}
