package dev.ntfy.hub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 简单的 JSON 文件持久化。订阅和消息都不大（消息有上限），
 * 不引入 Room，减少依赖和构建时间。
 */
class HubStore(context: Context) {

    private val file = File(context.filesDir, "hub_state.json")
    private val dismissedFile = File(context.filesDir, "dismissed_keys.json")
    private val serverStateFile = File(context.filesDir, "server_state.json")
    private val writeMutex = Mutex()

    /** 读取用户主动断开的服务器地址集合；文件缺失或损坏时返回空集合。 */
    fun loadDisconnectedServers(): Set<String> {
        if (!serverStateFile.exists()) return emptySet()
        return try {
            val root = JSONObject(serverStateFile.readText())
            val array = root.optJSONArray("disconnected") ?: return emptySet()
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** 保存用户主动断开的服务器地址集合（临时文件 + 重命名原子写）。 */
    suspend fun saveDisconnectedServers(servers: Set<String>) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val root = JSONObject().put(
                        "disconnected",
                        JSONArray(servers.toList()),
                    )
                    serverStateFile.parentFile?.mkdirs()
                    val tmp = File(serverStateFile.parentFile, serverStateFile.name + ".tmp")
                    tmp.writeText(root.toString())
                    if (serverStateFile.exists() && !serverStateFile.delete()) {
                        serverStateFile.writeText(root.toString())
                        tmp.delete()
                    } else if (!tmp.renameTo(serverStateFile)) {
                        serverStateFile.writeText(root.toString())
                        tmp.delete()
                    }
                } catch (_: Exception) {
                    // 持久化失败不应影响前台使用
                }
            }
        }
    }

    fun load(): PersistedState {
        if (!file.exists()) return PersistedState()
        return try {
            val root = JSONObject(file.readText())
            val subscriptions = parseSubscriptions(root.optJSONArray("subscriptions"))
            val validTopics = subscriptions
                .flatMap { sub -> sub.topics.map { sub.serverUrl to it } }
                .toSet()
            val messages = parseMessages(root.optJSONArray("messages"))
                .filter { (it.serverUrl to it.topic) in validTopics }
                .sortedByDescending { it.time }
                .take(MAX_MESSAGES)
            PersistedState(subscriptions, messages)
        } catch (_: Exception) {
            PersistedState()
        }
    }

    /** 读取已删除消息的黑名单；文件缺失或损坏时返回空集合。 */
    fun loadDismissedKeys(): Set<String> {
        if (!dismissedFile.exists()) return emptySet()
        return try {
            val array = JSONArray(dismissedFile.readText())
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * 从备份 JSON 解析订阅与消息（不写盘，由调用方决定是否覆盖）。
     * 与 [load] 相同的清洗规则：过滤无效订阅、剔除失效消息。
     */
    fun restoreFromJson(root: JSONObject): PersistedState {
        val subscriptions = parseSubscriptions(root.optJSONArray("subscriptions"))
        val validTopics = subscriptions
            .flatMap { sub -> sub.topics.map { sub.serverUrl to it } }
            .toSet()
        val messages = parseMessages(root.optJSONArray("messages"))
            .filter { (it.serverUrl to it.topic) in validTopics }
            .sortedByDescending { it.time }
            .take(MAX_MESSAGES)
        return PersistedState(subscriptions, messages)
    }

    suspend fun save(state: PersistedState, dismissedKeys: Set<String> = emptySet()) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val root = JSONObject()
                    root.put("version", 1)
                    root.put("subscriptions", JSONArray().apply {
                        state.subscriptions.forEach { put(it.toJson()) }
                    })
                    root.put("messages", JSONArray().apply {
                        state.messages.forEach { put(it.toJson()) }
                    })
                    file.parentFile?.mkdirs()
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(root.toString())
                    if (file.exists() && !file.delete()) {
                        file.writeText(root.toString())
                        tmp.delete()
                    } else if (!tmp.renameTo(file)) {
                        file.writeText(root.toString())
                        tmp.delete()
                    }
                    saveDismissedKeys(dismissedKeys)
                } catch (_: Exception) {
                    // 持久化失败不应影响前台使用
                }
            }
        }
    }

    private fun saveDismissedKeys(keys: Set<String>) {
        try {
            val tmp = File(dismissedFile.parentFile, dismissedFile.name + ".tmp")
            tmp.writeText(JSONArray(keys.toList()).toString())
            if (dismissedFile.exists() && !dismissedFile.delete()) {
                dismissedFile.writeText(JSONArray(keys.toList()).toString())
                tmp.delete()
            } else if (!tmp.renameTo(dismissedFile)) {
                dismissedFile.writeText(JSONArray(keys.toList()).toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            // 忽略黑名单写入失败
        }
    }

    private fun parseSubscriptions(array: JSONArray?): List<Subscription> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val serverUrl = obj.optString("serverUrl").trim().trimEnd('/')
                if (serverUrl.isBlank()) continue
                val topicsArray = obj.optJSONArray("topics") ?: continue
                val topics = buildList {
                    for (j in 0 until topicsArray.length()) {
                        val topic = topicsArray.optString(j).trim()
                        if (topic.isNotBlank() && topic != "null") add(topic)
                    }
                }.distinct()
                if (topics.isEmpty()) continue
                val aliasesObject = obj.optJSONObject("topicAliases")
                val topicAliases = buildMap {
                    if (aliasesObject != null) {
                        for (key in aliasesObject.keys()) {
                            val alias = aliasesObject.optString(key).trim()
                            if (alias.isNotBlank() && key in topics) put(key, alias)
                        }
                    }
                }
                add(
                    Subscription(
                        id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        serverUrl = serverUrl,
                        topics = topics,
                        topicAliases = topicAliases,
                        serverAlias = obj.stringOrNull("serverAlias"),
                        username = obj.stringOrNull("username"),
                        password = obj.stringOrNull("password"),
                        token = obj.stringOrNull("token"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
        }
    }

    private fun parseMessages(array: JSONArray?): List<NtfyMessage> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val serverUrl = obj.optString("serverUrl")
                val topic = obj.optString("topic")
                if (id.isBlank() || serverUrl.isBlank() || topic.isBlank()) continue
                val tagsArray = obj.optJSONArray("tags")
                val tags = buildList {
                    if (tagsArray != null) {
                        for (j in 0 until tagsArray.length()) {
                            tagsArray.optString(j).takeIf { it.isNotBlank() && it != "null" }?.let(::add)
                        }
                    }
                }
                add(
                    NtfyMessage(
                        id = id,
                        subscriptionId = obj.optString("subscriptionId"),
                        serverUrl = serverUrl,
                        topic = topic,
                        title = obj.stringOrNull("title"),
                        body = obj.optString("body"),
                        time = obj.optLong("time", System.currentTimeMillis()),
                        unread = obj.optBoolean("unread", true),
                        priority = obj.optInt("priority", 3),
                        tags = tags,
                        clickUrl = obj.stringOrNull("clickUrl"),
                        attachmentName = obj.stringOrNull("attachmentName"),
                        attachmentUrl = obj.stringOrNull("attachmentUrl"),
                        attachmentSize = obj.optLong("attachmentSize", 0L).takeIf { it > 0 },
                    )
                )
            }
        }
    }

    private fun Subscription.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("serverUrl", serverUrl)
        put("topics", JSONArray(topics))
        put("topicAliases", JSONObject().apply {
            topicAliases.forEach { (topic, alias) -> put(topic, alias) }
        })
        put("serverAlias", serverAlias ?: JSONObject.NULL)
        put("username", username ?: JSONObject.NULL)
        put("password", password ?: JSONObject.NULL)
        put("token", token ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    private fun NtfyMessage.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("subscriptionId", subscriptionId)
        put("serverUrl", serverUrl)
        put("topic", topic)
        put("title", title ?: JSONObject.NULL)
        put("body", body)
        put("time", time)
        put("unread", unread)
        put("priority", priority)
        put("tags", JSONArray(tags))
        put("clickUrl", clickUrl ?: JSONObject.NULL)
        put("attachmentName", attachmentName ?: JSONObject.NULL)
        put("attachmentUrl", attachmentUrl ?: JSONObject.NULL)
        put("attachmentSize", attachmentSize ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val MAX_MESSAGES = 800
    }
}
