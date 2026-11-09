package dev.ntfy.hub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.ntfy.hub.MainActivity
import dev.ntfy.hub.R
import dev.ntfy.hub.data.NtfyMessage
import dev.ntfy.hub.util.serverDisplayName

object Notifications {

    // v2：旧渠道创建时未显式设置铃声，部分 ROM 默认静音且渠道设置创建后不可改，
    // 因此换新渠道 id 让所有用户拿到带声音的配置。
    const val CHANNEL_MESSAGES = "ntfy_messages_v2"
    const val CHANNEL_SERVICE = "ntfy_service"
    const val SERVICE_NOTIFICATION_ID = 1101

    const val EXTRA_SERVER_URL = "ntfy_server_url"
    const val EXTRA_TOPIC = "ntfy_topic"

    /** 当前正在查看的主题，避免在消息页里重复弹通知。 */
    @Volatile
    var visibleTopicKey: String? = null

    /** 主题备注查询，由 NtfyApp 注入；通知标题和副标题优先显示备注。 */
    @Volatile
    var topicDisplayNameProvider: ((serverUrl: String, topic: String) -> String?)? = null

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 显式指定系统默认通知铃声：部分 ROM 对未设置声音的渠道默认静音。
        val soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.channel_messages),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "来自已订阅主题的新消息"
            setSound(soundUri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setShowBadge(true)
        }
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持与 ntfy 服务器的实时连接"
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(messages, service))
    }

    fun serviceNotification(context: Context, topicCount: Int, serverCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, NtfyService::class.java).setAction(NtfyService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_ntfy)
            .setContentTitle("ntfy 正在监听")
            .setContentText("$topicCount 个主题 · $serverCount 个服务器 · 实时接收推送")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_stat_ntfy, "停止", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun postMessage(context: Context, message: NtfyMessage, unreadCount: Int) {
        if (visibleTopicKey == message.topicKey) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SERVER_URL, message.serverUrl)
            putExtra(EXTRA_TOPIC, message.topic)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(message.serverUrl, message.topic),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            notificationId(message.serverUrl, message.topic) + 7,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_READ
                putExtra(EXTRA_SERVER_URL, message.serverUrl)
                putExtra(EXTRA_TOPIC, message.topic)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val displayTopic =
            topicDisplayNameProvider?.invoke(message.serverUrl, message.topic) ?: message.topic
        val title = message.title?.takeIf { it.isNotBlank() } ?: "#$displayTopic"
        val text = message.body.ifBlank { "新消息" }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_ntfy)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("${serverDisplayName(message.serverUrl)} · $displayTopic")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 兜底：即使渠道声音被 ROM 忽略，也用默认铃声发出提示音。
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setGroup("ntfy")
            .setNumber(unreadCount)
            .setWhen(message.time)
            .setShowWhen(true)
            .addAction(0, "标记已读", markRead)
            .build()

        try {
            manager.notify(notificationId(message.serverUrl, message.topic), notification)
        } catch (_: SecurityException) {
            // 用户拒绝了通知权限
        }
    }

    fun cancelTopic(context: Context, serverUrl: String, topic: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(serverUrl, topic))
    }

    private fun notificationId(serverUrl: String, topic: String): Int =
        ("$serverUrl|$topic").hashCode() and 0x7fffffff
}
