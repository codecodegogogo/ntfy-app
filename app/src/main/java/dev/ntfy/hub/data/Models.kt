package dev.ntfy.hub.data

import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val serverUrl: String,
    val topics: List<String>,
    /** 主题备注：key 是真实主题名，value 是界面显示名。 */
    val topicAliases: Map<String, String> = emptyMap(),
    /** 服务器备注：界面显示名；为空时显示原始地址。 */
    val serverAlias: String? = null,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * 同一台服务器 + 同一组凭据的订阅共用一条长连接。
     * 密码只参与哈希，避免明文留在 key 里。
     */
    val streamKey: String
        get() = serverUrl + "|" + username.orEmpty() + "|" + token.orEmpty() + "|" + (password?.hashCode() ?: 0)
}

data class NtfyMessage(
    val id: String,
    val subscriptionId: String,
    val serverUrl: String,
    val topic: String,
    val title: String?,
    val body: String,
    val time: Long,
    val unread: Boolean = true,
    val priority: Int = 3,
    val tags: List<String> = emptyList(),
    val clickUrl: String? = null,
    /** 附件：名称与下载地址；只有服务器消息携带 attachment 时才有值。 */
    val attachmentName: String? = null,
    val attachmentUrl: String? = null,
    val attachmentSize: Long? = null,
) {
    val key: String get() = "$serverUrl|$topic|$id"
    val topicKey: String get() = "$serverUrl|$topic"
    val hasAttachment: Boolean get() = !attachmentUrl.isNullOrBlank()
}

enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

data class ServerStatus(
    val state: ConnectionState = ConnectionState.CONNECTING,
    val latencyMs: Long? = null,
    val error: String? = null,
)

data class PersistedState(
    val subscriptions: List<Subscription> = emptyList(),
    val messages: List<NtfyMessage> = emptyList(),
)
