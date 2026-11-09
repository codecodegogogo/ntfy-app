package dev.ntfy.hub.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.ntfy.hub.NtfyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务只负责让进程保持存活并展示常驻通知；
 * 真正的连接由 [dev.ntfy.hub.data.NtfyRepository] 持有。
 */
class NtfyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        startInForeground(0)
        scope.launch {
            (application as NtfyApp).repository.subscriptions.collectLatest { subscriptions ->
                val topics = subscriptions.sumOf { it.topics.size }
                if (topics == 0) {
                    stopSelf()
                    return@collectLatest
                }
                startInForeground(topics)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as NtfyApp).repository.stopStreams()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startInForeground(topicCount: Int) {
        val subscriptions = (application as NtfyApp).repository.subscriptions.value
        val notification = Notifications.serviceNotification(
            this,
            topicCount,
            subscriptions.map { it.serverUrl }.distinct().size,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.SERVICE_NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "dev.ntfy.hub.STOP_SERVICE"

        fun start(context: Context) {
            val app = context.applicationContext as? NtfyApp ?: return
            if (app.repository.subscriptions.value.isEmpty()) return
            val intent = Intent(context, NtfyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyService::class.java))
        }
    }
}
