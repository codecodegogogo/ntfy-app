package dev.ntfy.hub

import android.app.Application
import dev.ntfy.hub.data.NtfyRepository
import dev.ntfy.hub.service.Notifications
import dev.ntfy.hub.service.NtfyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NtfyApp : Application() {

    lateinit var repository: NtfyRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        repository = NtfyRepository(this)
        repository.start()
        Notifications.topicDisplayNameProvider = repository::aliasOf

        // 订阅首次出现时拉起前台服务，避免「添加订阅后要等下次启动才有后台通知」。
        appScope.launch {
            repository.subscriptions.collect { subscriptions ->
                if (subscriptions.any { it.topics.isNotEmpty() }) {
                    try {
                        NtfyService.start(this@NtfyApp)
                    } catch (_: Exception) {
                        // 后台启动前台服务受限时忽略，回到前台会再次尝试。
                    }
                }
            }
        }

        appScope.launch {
            repository.newMessages.collect { message ->
                // 只对实时消息弹通知；连接建立时补发的历史消息不打扰用户。
                val age = System.currentTimeMillis() - message.time
                if (age in -60_000L..120_000L) {
                    val unread = repository.messages.value.count {
                        it.topicKey == message.topicKey && it.unread
                    }
                    Notifications.postMessage(this@NtfyApp, message, unread)
                }
            }
        }
    }
}
