package ai.anya.companion.notify

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.anya.companion.MainActivity
import ai.anya.companion.R
import ai.anya.companion.core.common.di.ApplicationScope
import ai.anya.companion.core.model.protocol.ServerMessage
import ai.anya.companion.core.network.gateway.RemoteGatewayClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Surfaces desktop-side interactions as system notifications with vibration:
 * task finished, ask-user questions, and tool approvals. When the app is in
 * the foreground we only vibrate (the in-app panel is already visible).
 */
@Singleton
class CompanionNotifier @Inject constructor(
    private val gateway: RemoteGatewayClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private var foregroundCount = 0
    private var notificationId = 1000

    fun start(app: Application) {
        createChannel(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                foregroundCount++
            }

            override fun onActivityStopped(activity: Activity) {
                foregroundCount = (foregroundCount - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        appScope.launch {
            gateway.incoming.collect { message ->
                if (message !is ServerMessage.Event) return@collect
                when (message.name) {
                    "chat.finished", "ChatFinished" -> {
                        onAlert(
                            app,
                            title = "回答完成",
                            body = message.data.string("content")
                                ?.replace('\n', ' ')
                                ?.trim()
                                ?.take(80)
                                .orEmpty()
                                .ifBlank { "Anya 已完成本次回答" },
                            sessionId = message.data.string("sessionId"),
                        )
                    }
                    "ask-user", "ask.user", "AskUser" -> {
                        onAlert(
                            app,
                            title = "需要你的回答",
                            body = message.data.string("title").orEmpty().ifBlank { "桌面端发来一个问题" },
                            sessionId = message.data.string("sessionId"),
                        )
                    }
                    "tool-approval", "tool.approval" -> {
                        onAlert(
                            app,
                            title = "工具审批",
                            body = listOfNotNull(
                                message.data.string("title"),
                                message.data.string("toolName"),
                            ).firstOrNull { it.isNotBlank() } ?: "有工具操作等待你的批准",
                            sessionId = message.data.string("sessionId"),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun onAlert(context: Context, title: String, body: String, sessionId: String?) {
        if (foregroundCount > 0) {
            vibrate(context)
        }
        postNotification(context, title, body, sessionId)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
    }

    private fun postNotification(context: Context, title: String, body: String, sessionId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!sessionId.isNullOrBlank()) putExtra("sessionId", sessionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            sessionId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId++, notification)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anya 交互提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "任务完成、询问与工具审批提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 40, 60, 40)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val CHANNEL_ID = "anya_alerts"
    }
}
