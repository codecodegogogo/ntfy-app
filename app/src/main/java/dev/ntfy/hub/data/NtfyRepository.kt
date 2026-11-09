package dev.ntfy.hub.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * ntfy 订阅与消息的唯一数据源。
 *
 * 每个主题维持一条 JSON 流式长连接（/topic/json）。ntfy.sh 不允许匿名用户
 * 在一条连接里订阅多个主题（会返回 403），所以按主题拆分最稳妥。
 * 断线后指数退避重连；消息按 id 去重并持久化到本地。
 */
class NtfyRepository(context: Context) {

    private val store = HubStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _messages = MutableStateFlow<List<NtfyMessage>>(emptyList())
    val messages: StateFlow<List<NtfyMessage>> = _messages.asStateFlow()

    private val _streamStates = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())

    /** 同一台服务器可能有多个主题连接，这里聚合成一个服务器状态。 */
    val serverStates: StateFlow<Map<String, ServerStatus>> =
        combine(_streamStates, _subscriptions) { states, subs ->
            subs.map { it.serverUrl }.distinct().associateWith { serverUrl ->
                val keys = subs
                    .filter { it.serverUrl == serverUrl }
                    .flatMap { sub -> sub.topics.map { sub.streamKey + "|" + it } }
                val statuses = keys.mapNotNull { states[it] }
                val online = statuses.firstOrNull { it.state == ConnectionState.ONLINE }
                when {
                    online != null -> online
                    statuses.isEmpty() -> ServerStatus(ConnectionState.CONNECTING)
                    statuses.any { it.state == ConnectionState.CONNECTING } ->
                        ServerStatus(ConnectionState.CONNECTING)
                    else -> statuses.first()
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _newMessages = MutableSharedFlow<NtfyMessage>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<NtfyMessage> = _newMessages.asSharedFlow()

    private val streamJobs = ConcurrentHashMap<String, StreamHandle>()
    private var saveJob: Job? = null

    /**
     * 已删除消息的 key 黑名单：服务器重拉时会重推窗口内的旧消息，
     * 这里记录被用户删掉/清空的 key，防止它们「复活」。
     */
    private val _dismissedKeys = MutableStateFlow(store.loadDismissedKeys())
    val dismissedKeys: StateFlow<Set<String>> = _dismissedKeys.asStateFlow()

    /**
     * 用户主动断开的服务器地址集合（持久化）。
     * 断开后不再为该服务器建立任何连接，重连后恢复接收。
     */
    private val _disconnectedServers = MutableStateFlow(store.loadDisconnectedServers())
    val disconnectedServers: StateFlow<Set<String>> = _disconnectedServers.asStateFlow()

    /** 断开某个服务器的所有连接，不再接收它的消息。 */
    fun disconnectServer(serverUrl: String) {
        _disconnectedServers.update { it + serverUrl }
        persistServerState()
        streamJobs.entries
            .filter { it.key.startsWith("$serverUrl|") }
            .forEach { (key, handle) ->
                stopStream(handle)
                streamJobs.remove(key)
                setStatus(key, ServerStatus(ConnectionState.OFFLINE, null, "已断开"))
            }
    }

    /** 重新连接被断开的服务器。 */
    fun reconnectServer(serverUrl: String) {
        _disconnectedServers.update { it - serverUrl }
        persistServerState()
        reconcileStreams()
    }

    /** 服务器级状态（断开集合）单独落盘，跟订阅/消息分开。 */
    private fun persistServerState() {
        scope.launch { store.saveDisconnectedServers(_disconnectedServers.value) }
    }

    private fun dismissKey(key: String) {
        _dismissedKeys.update { keys ->
            // 限制规模，防止无限增长；重推窗口只有 12 小时，足够用。
            val next = keys + key
            if (next.size > MAX_DISMISSED_KEYS) next.drop(next.size - MAX_DISMISSED_KEYS).toSet()
            else next
        }
    }

    init {
        val persisted = store.load()
        _subscriptions.value = persisted.subscriptions
        _messages.value = persisted.messages
    }

    /** 应用启动、回到前台或服务重启时调用；重复调用是安全的。 */
    fun start() {
        reconcileStreams()
    }

    /**
     * 用备份内容整体覆盖本地数据（订阅、消息、黑名单）。
     * 断开的服务器状态保留不动。
     */
    fun restoreFromBackup(plus: PersistedStatePlus) {
        _subscriptions.value = plus.state.subscriptions
        _messages.value = plus.state.messages
        _dismissedKeys.value = plus.dismissedKeys
        persist()
        reconcileStreams(force = true)
    }

    fun addSubscription(subscription: Subscription): Boolean {
        var changed = false
        _subscriptions.update { current ->
            val existing = current.firstOrNull {
                it.serverUrl == subscription.serverUrl &&
                    it.username == subscription.username &&
                    it.password == subscription.password &&
                    it.token == subscription.token
            }
            if (existing != null) {
                val merged = (existing.topics + subscription.topics).distinct()
                val mergedAliases = (existing.topicAliases + subscription.topicAliases)
                    .filterKeys { it in merged }
                if (merged.size == existing.topics.size && mergedAliases == existing.topicAliases) {
                    current
                } else {
                    changed = true
                    current.map {
                        if (it.id == existing.id) {
                            it.copy(topics = merged, topicAliases = mergedAliases)
                        } else {
                            it
                        }
                    }
                }
            } else {
                changed = true
                current + subscription
            }
        }
        if (changed) {
            persist()
            reconcileStreams()
        }
        return changed
    }

    /**
     * 把一批主题迁移到新的凭据下（自动保存时用户补填/修改了凭据）。
     * 同服务器、同凭据的记录会合并。
     */
    @Synchronized
    fun moveTopics(
        serverUrl: String,
        topics: List<String>,
        username: String?,
        password: String?,
        token: String?,
        topicAliases: Map<String, String> = emptyMap(),
    ) {
        if (topics.isEmpty()) return
        val moved = topics.toSet()
        val aliases = topicAliases.filterKeys { it in moved }
        _subscriptions.update { current ->
            val stripped = current.mapNotNull { subscription ->
                if (subscription.serverUrl != serverUrl) return@mapNotNull subscription
                val remaining = subscription.topics.filterNot { it in moved }
                when {
                    remaining.size == subscription.topics.size -> subscription
                    remaining.isEmpty() -> null
                    else -> subscription.copy(
                        topics = remaining,
                        topicAliases = subscription.topicAliases.filterKeys { it in remaining },
                    )
                }
            }
            val target = stripped.firstOrNull {
                it.serverUrl == serverUrl &&
                    it.username == username &&
                    it.password == password &&
                    it.token == token
            }
            if (target != null) {
                stripped.map {
                    if (it.id == target.id) {
                        it.copy(
                            topics = (it.topics + topics).distinct(),
                            topicAliases = it.topicAliases + aliases,
                        )
                    } else {
                        it
                    }
                }
            } else {
                stripped + Subscription(
                    serverUrl = serverUrl,
                    topics = topics.distinct(),
                    topicAliases = aliases,
                    username = username,
                    password = password,
                    token = token,
                )
            }
        }
        persist()
        reconcileStreams()
    }

    /** 查询主题的备注名；没有备注时返回 null。 */
    fun aliasOf(serverUrl: String, topic: String): String? =
        _subscriptions.value
            .firstOrNull { it.serverUrl == serverUrl && topic in it.topics }
            ?.topicAliases?.get(topic)

    fun removeTopic(serverUrl: String, topic: String) {
        _subscriptions.update { list ->
            list.mapNotNull { subscription ->
                if (subscription.serverUrl == serverUrl && topic in subscription.topics) {
                    val remaining = subscription.topics - topic
                    if (remaining.isEmpty()) {
                        null
                    } else {
                        subscription.copy(
                            topics = remaining,
                            topicAliases = subscription.topicAliases - topic,
                        )
                    }
                } else {
                    subscription
                }
            }
        }
        _messages.update { list -> list.filterNot { it.serverUrl == serverUrl && it.topic == topic } }
        persist()
        reconcileStreams()
    }

    /** 删除同一服务器地址上的全部订阅和消息。 */
    fun removeServer(serverUrl: String) {
        _subscriptions.update { list -> list.filterNot { it.serverUrl == serverUrl } }
        _messages.update { list -> list.filterNot { it.serverUrl == serverUrl } }
        persist()
        reconcileStreams()
    }

    fun refresh() {
        reconcileStreams(force = true)
    }

    /**
     * 定向刷新：只重拉某个服务器上的单个主题。
     * 聚合页的刷新走 [refresh] 重拉全部。
     */
    fun refreshTopic(serverUrl: String, topic: String) {
        reconcileStreams(force = true, onlyServerUrl = serverUrl, onlyTopic = topic)
    }

    @Synchronized
    fun stopStreams() {
        streamJobs.values.forEach(::stopStream)
        streamJobs.clear()
        // 保留「已停止」的离线状态，避免界面误显示为「连接中」。
        _streamStates.value = _subscriptions.value
            .flatMap { sub -> sub.topics.map { sub.streamKey + "|" + it } }
            .associateWith { ServerStatus(ConnectionState.OFFLINE, null, STOPPED_REASON) }
    }

    fun markAllRead(serverUrl: String? = null, topic: String? = null) {
        var changed = false
        _messages.update { list ->
            list.map { message ->
                if (message.unread && message.matches(serverUrl, topic)) {
                    changed = true
                    message.copy(unread = false)
                } else {
                    message
                }
            }
        }
        if (changed) persist()
    }

    fun markMessageRead(key: String) {
        var changed = false
        _messages.update { list ->
            list.map { message ->
                if (message.key == key && message.unread) {
                    changed = true
                    message.copy(unread = false)
                } else {
                    message
                }
            }
        }
        if (changed) persist()
    }

    fun markReadSince(serverUrl: String?, topic: String?, since: Long) {
        var changed = false
        _messages.update { list ->
            list.map { message ->
                if (message.unread && message.time >= since && message.matches(serverUrl, topic)) {
                    changed = true
                    message.copy(unread = false)
                } else {
                    message
                }
            }
        }
        if (changed) persist()
    }

    fun deleteMessage(key: String): NtfyMessage? {
        val message = _messages.value.firstOrNull { it.key == key } ?: return null
        _messages.update { list -> list.filterNot { it.key == key } }
        dismissKey(key)
        persist()
        return message
    }

    fun restoreMessage(message: NtfyMessage) {
        // 撤销删除：把 key 从黑名单里拿掉，避免重拉时被再次拦截。
        _dismissedKeys.update { it - message.key }
        var changed = false
        _messages.update { current ->
            if (current.any { it.key == message.key }) {
                current
            } else {
                changed = true
                trimMessages(current + message)
            }
        }
        if (changed) persist()
    }

    fun clearMessages(serverUrl: String? = null, topic: String? = null) {
        _messages.update { list ->
            list.filterNot { message ->
                if (message.matches(serverUrl, topic)) {
                    _dismissedKeys.update { it + message.key }
                    true
                } else {
                    false
                }
            }
        }
        persist()
    }

    private fun NtfyMessage.matches(serverUrl: String?, topic: String?): Boolean =
        (serverUrl == null || this.serverUrl == serverUrl) &&
            (topic == null || this.topic == topic)

    private fun acceptMessage(message: NtfyMessage) {
        var added = false
        _messages.update { current ->
            if (message.key in _dismissedKeys.value || current.any { it.key == message.key }) {
                current
            } else {
                added = true
                trimMessages(listOf(message) + current)
            }
        }
        if (added) {
            persist()
            _newMessages.tryEmit(message)
        }
    }

    private fun trimMessages(messages: List<NtfyMessage>): List<NtfyMessage> {
        val perTopic = messages
            .groupBy { it.topicKey }
            .values
            .flatMap { list -> list.sortedByDescending { it.time }.take(MAX_PER_TOPIC) }
        return perTopic.sortedByDescending { it.time }.take(MAX_TOTAL)
    }

    @Synchronized
    private fun reconcileStreams(
        force: Boolean = false,
        onlyServerUrl: String? = null,
        onlyTopic: String? = null,
    ) {
        val subs = _subscriptions.value

        if (force) {
            if (onlyServerUrl == null) {
                streamJobs.values.forEach(::stopStream)
                streamJobs.clear()
                _streamStates.value = emptyMap()
            } else {
                // 定向刷新：只停掉目标主题的连接，其它主题保持不断流。
                val targetPrefix = "$onlyServerUrl|"
                streamJobs.entries
                    .filter { it.key.startsWith(targetPrefix) && (onlyTopic == null || it.key.endsWith("|$onlyTopic")) }
                    .forEach { (key, handle) ->
                        stopStream(handle)
                        streamJobs.remove(key)
                    }
                _streamStates.update { states ->
                    states.filterKeys { key ->
                        !(key.startsWith(targetPrefix) && (onlyTopic == null || key.endsWith("|$onlyTopic")))
                    }
                }
            }
        }

        val desired = LinkedHashMap<String, StreamHandle>()
        val disconnected = _disconnectedServers.value
        for (subscription in subs) {
            // 用户主动断开的服务器不再建立连接。
            if (subscription.serverUrl in disconnected) continue
            for (topic in subscription.topics) {
                val key = "${subscription.streamKey}|$topic"
                // streamKey 含凭据哈希，定向过滤时按 serverUrl 前缀匹配即可覆盖。
                desired[key] = StreamHandle(
                    key = key,
                    serverUrl = subscription.serverUrl,
                    username = subscription.username,
                    password = subscription.password,
                    token = subscription.token,
                    topics = listOf(topic),
                )
            }
        }

        val removeKeys = streamJobs.keys.filter { it !in desired.keys }
        removeKeys.forEach { key -> streamJobs.remove(key)?.let(::stopStream) }

        for ((key, handle) in desired) {
            if (streamJobs.containsKey(key)) continue
            streamJobs[key] = handle
            _streamStates.update { it + (key to ServerStatus(ConnectionState.CONNECTING)) }
            handle.job = scope.launch { runStream(handle) }
        }

        _streamStates.update { states -> states.filterKeys { it in desired.keys } }
    }

    private suspend fun runStream(handle: StreamHandle) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            setStatus(handle.key, ServerStatus(ConnectionState.CONNECTING))
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val call = client.newCall(buildRequest(handle))
                handle.call = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string()?.take(200).orEmpty()
                        throw IOException("HTTP ${response.code}" + if (body.isNotBlank()) " · $body" else "")
                    }
                    val latency = SystemClock.elapsedRealtime() - startedAt
                    setStatus(handle.key, ServerStatus(ConnectionState.ONLINE, latency))
                    attempt = 0

                    val source = response.body?.source() ?: throw IOException("服务器没有返回数据")
                    while (currentCoroutineContext().isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        handleLine(handle, line)
                    }
                    throw IOException("连接已断开")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!currentCoroutineContext().isActive) break
                setStatus(
                    handle.key,
                    ServerStatus(ConnectionState.OFFLINE, null, error.message ?: "无法连接服务器"),
                )
            } finally {
                handle.call = null
            }

            attempt++
            delay((1_000L shl min(attempt, 5)).coerceAtMost(60_000L))
        }
    }

    private fun buildRequest(handle: StreamHandle): Request {
        val base = handle.serverUrl.toHttpUrlOrNull() ?: throw IOException("服务器地址无效")
        val url = base.newBuilder()
            .addPathSegment(handle.topics.joinToString(","))
            .addPathSegment("json")
            .addQueryParameter("since", sinceFor(handle.serverUrl).toString())
            .build()

        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ntfy-hub-android/1.0")
            .apply {
                val token = handle.token
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                } else if (!handle.username.isNullOrBlank()) {
                    header("Authorization", Credentials.basic(handle.username, handle.password.orEmpty()))
                }
            }
            .build()
    }

    private fun sinceFor(serverUrl: String): Long {
        val now = System.currentTimeMillis()
        val latest = _messages.value
            .asSequence()
            .filter { it.serverUrl == serverUrl }
            .maxOfOrNull { it.time } ?: 0L
        val start = maxOf(now - HISTORY_WINDOW_MS, latest - 60_000L)
        return start / 1000
    }

    private fun handleLine(handle: StreamHandle, line: String) {
        val obj = try {
            JSONObject(line)
        } catch (_: Exception) {
            return
        }
        when (obj.optString("event")) {
            "message" -> {
                val topic = obj.optString("topic")
                if (topic.isBlank()) return
                val subscription = _subscriptions.value.firstOrNull {
                    it.serverUrl == handle.serverUrl && topic in it.topics
                } ?: return
                val id = obj.optString("id")
                if (id.isBlank()) return

                val tags = obj.optJSONArray("tags")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i)
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?.let(::add)
                        }
                    }
                } ?: emptyList()

                val timeSeconds = obj.optLong("time", System.currentTimeMillis() / 1000)
                    .takeIf { it > 0 }
                    ?: (System.currentTimeMillis() / 1000)

                // 附件字段（ntfy JSON）：name/size 类型，url 为下载地址。
                val attachment = obj.optJSONObject("attachment")
                val attachmentUrl = attachment?.optString("url")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val attachmentName = attachment?.optString("name")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val attachmentSize = attachment?.optLong("size", 0L)?.takeIf { it > 0 }

                acceptMessage(
                    NtfyMessage(
                        id = id,
                        subscriptionId = subscription.id,
                        serverUrl = handle.serverUrl,
                        topic = topic,
                        title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" },
                        body = obj.optString("message").takeIf { it != "null" }.orEmpty(),
                        time = timeSeconds * 1000,
                        unread = true,
                        priority = obj.optInt("priority", 3),
                        tags = tags,
                        clickUrl = obj.optString("click").takeIf { it.isNotBlank() && it != "null" },
                        attachmentName = attachmentName,
                        attachmentUrl = attachmentUrl,
                        attachmentSize = attachmentSize,
                    )
                )
            }
        }
    }

    private fun setStatus(streamKey: String, status: ServerStatus) {
        _streamStates.update { it + (streamKey to status) }
    }

    private fun stopStream(handle: StreamHandle) {
        handle.call?.cancel()
        handle.job?.cancel()
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(250)
            store.save(PersistedState(_subscriptions.value, _messages.value), _dismissedKeys.value)
        }
    }

    private class StreamHandle(
        val key: String,
        val serverUrl: String,
        val username: String?,
        val password: String?,
        val token: String?,
        val topics: List<String>,
    ) {
        var job: Job? = null

        @Volatile
        var call: Call? = null
    }

    companion object {
        /** 用户通过通知动作主动停止监听时的状态说明。 */
        const val STOPPED_REASON = "监听已停止"

        private const val MAX_PER_TOPIC = 250
        private const val MAX_TOTAL = 800
        private const val HISTORY_WINDOW_MS = 12 * 60 * 60 * 1000L

        /** 已删除消息黑名单上限，超过后丢弃最早的记录。 */
        private const val MAX_DISMISSED_KEYS = 2000
    }
}
