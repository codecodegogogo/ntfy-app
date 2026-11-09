package dev.ntfy.hub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ntfy.hub.NtfyApp

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_READ) return
        val serverUrl = intent.getStringExtra(Notifications.EXTRA_SERVER_URL) ?: return
        val topic = intent.getStringExtra(Notifications.EXTRA_TOPIC) ?: return
        val app = context.applicationContext as? NtfyApp ?: return
        app.repository.markAllRead(serverUrl, topic)
        Notifications.cancelTopic(context, serverUrl, topic)
    }

    companion object {
        const val ACTION_MARK_READ = "dev.ntfy.hub.MARK_READ"
    }
}
