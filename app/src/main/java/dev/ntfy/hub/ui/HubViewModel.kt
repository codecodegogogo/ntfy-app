package dev.ntfy.hub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ntfy.hub.NtfyApp
import dev.ntfy.hub.data.NtfyMessage
import dev.ntfy.hub.data.NtfyRepository
import dev.ntfy.hub.data.Subscription
import dev.ntfy.hub.service.NtfyService
import dev.ntfy.hub.util.isValidTopic
import dev.ntfy.hub.util.normalizeServerUrl
import dev.ntfy.hub.util.normalizeTopic
import dev.ntfy.hub.util.serverDisplayName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HubViewModel(private val app: NtfyApp) : ViewModel() {

    private val repository: NtfyRepository = app.repository

    val subscriptions = repository.subscriptions
    val messages = repository.messages
    val serverStates = repository.serverStates
    val disconnectedServers = repository.disconnectedServers
    val dismissedKeys = repository.dismissedKeys

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /**
     * 校验并添加订阅。返回 null 表示成功，否则返回给用户看的错误文案。
     */
    fun addSubscription(
        serverUrlInput: String,
        topicInputs: List<String>,
        usernameInput: String,
        passwordInput: String,
        tokenInput: String,
        topicAliases: Map<String, String> = emptyMap(),
    ): String? {
        val serverUrl = normalizeServerUrl(serverUrlInput)
            ?: return "请输入有效的服务器地址（http 或 https）"

        val topics = topicInputs.map(::normalizeTopic).filter { it.isNotEmpty() }.distinct()
        if (topics.isEmpty()) return "请至少添加一个主题"
        topics.firstOrNull { !isValidTopic(it) }?.let {
            return "主题 “$it” 不合法，仅支持字母、数字、- 和 _"
        }
        val aliases = topicAliases
            .filterKeys { it in topics }
            .filterValues { it.isNotBlank() }

        val token = tokenInput.trim().takeIf { it.isNotBlank() }
        val username = if (token == null) usernameInput.trim().takeIf { it.isNotBlank() } else null
        val password = if (token == null) passwordInput.takeIf { it.isNotBlank() } else null
        if (token == null && (username == null) != (password == null)) {
            return "用户名和密码需要同时填写"
        }

        val duplicate = repository.subscriptions.value.any { subscription ->
            subscription.serverUrl == serverUrl && subscription.topics.any { it in topics }
        }
        if (duplicate) return "该服务器上的主题已经订阅过了"

        val added = repository.addSubscription(
            Subscription(
                serverUrl = serverUrl,
                topics = topics,
                topicAliases = aliases,
                username = username,
                password = password,
                token = token,
            )
        )
        if (!added) return "这些主题已经订阅过了"

        _events.tryEmit("已添加 ${topics.size} 个主题 · 正在连接 ${serverDisplayName(serverUrl)}")
        return null
    }

    /** 把已保存的主题迁移到修改后的凭据下。返回 null 表示成功。 */
    fun moveTopics(
        serverUrlInput: String,
        topicInputs: List<String>,
        usernameInput: String,
        passwordInput: String,
        tokenInput: String,
        topicAliases: Map<String, String> = emptyMap(),
    ): String? {
        val serverUrl = normalizeServerUrl(serverUrlInput)
            ?: return "请输入有效的服务器地址（http 或 https）"
        val topics = topicInputs.map(::normalizeTopic).filter { it.isNotEmpty() }.distinct()
        if (topics.isEmpty()) return null

        val token = tokenInput.trim().takeIf { it.isNotBlank() }
        val username = if (token == null) usernameInput.trim().takeIf { it.isNotBlank() } else null
        val password = if (token == null) passwordInput.takeIf { it.isNotBlank() } else null
        if (token == null && (username == null) != (password == null)) {
            return "用户名和密码需要同时填写"
        }

        repository.moveTopics(
            serverUrl = serverUrl,
            topics = topics,
            username = username,
            password = password,
            token = token,
            topicAliases = topicAliases.filterKeys { it in topics },
        )
        return null
    }

    fun removeTopic(serverUrl: String, topic: String) {
        val displayName = repository.aliasOf(serverUrl, topic) ?: topic
        repository.removeTopic(serverUrl, topic)
        _events.tryEmit("已取消订阅 #$displayName")
    }

    /** 删除同一服务器地址上的全部订阅（长按服务器操作）。 */
    fun removeServer(serverUrl: String) {
        val displayName = serverDisplayName(serverUrl)
        repository.removeServer(serverUrl)
        _events.tryEmit("已删除 $displayName 的全部订阅")
    }

    /** 断开服务器：不再接收它的所有消息（长按服务器操作）。 */
    fun disconnectServer(serverUrl: String) {
        repository.disconnectServer(serverUrl)
        _events.tryEmit("已断开 ${serverDisplayName(serverUrl)}，不再接收消息")
    }

    /** 重新连接已断开的服务器。 */
    fun reconnectServer(serverUrl: String) {
        repository.reconnectServer(serverUrl)
        _events.tryEmit("正在重新连接 ${serverDisplayName(serverUrl)}")
    }

    fun refresh() {
        repository.refresh()
        // 用户可能通过通知「停止」结束过监听，重试时一并恢复前台服务。
        NtfyService.start(app)
    }

    /** 单主题页的下拉刷新：只重拉该主题，不动其它订阅。 */
    fun refreshTopic(serverUrl: String, topic: String) {
        repository.refreshTopic(serverUrl, topic)
        NtfyService.start(app)
    }

    fun markAllRead(serverUrl: String? = null, topic: String? = null) =
        repository.markAllRead(serverUrl, topic)

    fun markReadSince(serverUrl: String?, topic: String?, since: Long) =
        repository.markReadSince(serverUrl, topic, since)

    fun markMessageRead(key: String) = repository.markMessageRead(key)

    fun deleteMessage(key: String): NtfyMessage? = repository.deleteMessage(key)

    fun restoreMessage(message: NtfyMessage) = repository.restoreMessage(message)

    fun clearMessages(serverUrl: String? = null, topic: String? = null) =
        repository.clearMessages(serverUrl, topic)

    /** 用备份内容覆盖本地数据。 */
    fun restoreBackup(plus: dev.ntfy.hub.data.PersistedStatePlus) {
        repository.restoreFromBackup(plus)
        _events.tryEmit(app.getString(dev.ntfy.hub.R.string.restore_done))
    }

    companion object {
        fun factory(app: NtfyApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { HubViewModel(app) }
        }
    }
}
