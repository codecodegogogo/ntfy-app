package dev.ntfy.hub.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份 / 还原：把订阅、消息、黑名单导出成 JSON，
 * 可选 WebDAV 上传；密码加密采用 PBKDF2（口令派生密钥）+ AES-GCM（认证加密）。
 */
class BackupManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 生成备份 JSON 字符串；password 非空时整体加密。 */
    fun exportJson(state: PersistedState, dismissedKeys: Set<String>, password: String?): String {
        val root = JSONObject()
            .put("app", "ntfy-hub")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("subscriptions", JSONArray().apply {
                state.subscriptions.forEach { put(subscriptionJson(it)) }
            })
            .put("messages", JSONArray().apply {
                state.messages.forEach { put(messageJson(it)) }
            })
            .put("dismissedKeys", JSONArray(dismissedKeys.toList()))

        if (password.isNullOrBlank()) return root.toString()

        // 加密备份：随机盐 + 随机 IV，PBKDF2 派生 256 位密钥，AES-GCM 认证加密。
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))

        return JSONObject()
            .put("app", "ntfy-hub")
            .put("version", 1)
            .put("encrypted", true)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", PBKDF2_ITERATIONS)
            .put("salt", salt.toBase64())
            .put("iv", iv.toBase64())
            .put("payload", encrypted.toBase64())
            .toString()
    }

    /** 解析备份字符串；带密码的备份需要正确的密码。失败抛异常。 */
    fun parseBackup(text: String, password: String?): PersistedStatePlus {
        val root = JSONObject(text)
        if (!root.optBoolean("encrypted", false)) {
            val store = HubStore(context)
            val state = store.restoreFromJson(root)
            val dismissed = buildSet {
                val array = root.optJSONArray("dismissedKeys")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            return PersistedStatePlus(state, dismissed)
        }

        val pwd = password?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(context.getString(dev.ntfy.hub.R.string.backup_encrypted_required))
        val salt = root.getString("salt").fromBase64()
        val iv = root.getString("iv").fromBase64()
        val payload = root.getString("payload").fromBase64()
        val iterations = root.optInt("iterations", PBKDF2_ITERATIONS)
        val key = deriveKey(pwd, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        // 密码错误时 GCM 校验失败会抛 AEADBadTagException。
        val plain = cipher.doFinal(payload)
        val inner = JSONObject(String(plain, Charsets.UTF_8))
        val store = HubStore(context)
        val state = store.restoreFromJson(inner)
        val dismissed = buildSet {
            val array = inner.optJSONArray("dismissedKeys")
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return PersistedStatePlus(state, dismissed)
    }

    /** 写入本地临时文件，供系统分享 / 用户手动保存。 */
    suspend fun exportToFile(state: PersistedState, dismissedKeys: Set<String>, password: String?): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "backup").apply { mkdirs() }
            val file = File(dir, "ntfy-hub-backup.json")
            file.writeText(exportJson(state, dismissedKeys, password))
            file
        }

    /** 上传备份到 WebDAV 服务器。失败抛异常，异常消息可直接展示。 */
    suspend fun uploadToWebDav(
        url: String,
        username: String,
        password: String,
        content: String,
        remotePath: String,
    ): Unit = withContext(Dispatchers.IO) {
        val base = normalizeWebDavUrl(url)
            ?: throw IllegalArgumentException(context.getString(dev.ntfy.hub.R.string.webdav_addr_invalid))
        val target = base.newBuilder()
            .addPathSegments(remotePath.trim().trim('/'))
            .build()

        val request = Request.Builder()
            .url(target)
            .put(content.toRequestBody("application/json".toMediaType()))
            .apply {
                if (username.isNotBlank()) {
                    header("Authorization", okhttp3.Credentials.basic(username, password))
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(dev.ntfy.hub.R.string.webdav_upload_failed, response.code))
            }
        }
    }

    /** 从 WebDAV 下载备份内容。失败抛异常。 */
    suspend fun downloadFromWebDav(
        url: String,
        username: String,
        password: String,
        remotePath: String,
    ): String = withContext(Dispatchers.IO) {
        val base = normalizeWebDavUrl(url)
            ?: throw IllegalArgumentException(context.getString(dev.ntfy.hub.R.string.webdav_addr_invalid))
        val target = base.newBuilder()
            .addPathSegments(remotePath.trim().trim('/'))
            .build()

        val request = Request.Builder()
            .url(target)
            .get()
            .apply {
                if (username.isNotBlank()) {
                    header("Authorization", okhttp3.Credentials.basic(username, password))
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(dev.ntfy.hub.R.string.webdav_download_failed, response.code))
            }
            response.body?.string() ?: throw IOException(context.getString(dev.ntfy.hub.R.string.webdav_empty))
        }
    }

    private fun normalizeWebDavUrl(raw: String) = raw.trim().toHttpUrlOrNull()

    /** PBKDF2（基于口令的密钥派生函数）：把用户口令拉伸成 AES 密钥。 */
    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun subscriptionJson(s: Subscription): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("serverUrl", s.serverUrl)
        put("topics", JSONArray(s.topics))
        put("topicAliases", JSONObject().apply { s.topicAliases.forEach { (k, v) -> put(k, v) } })
        put("serverAlias", s.serverAlias ?: JSONObject.NULL)
        put("username", s.username ?: JSONObject.NULL)
        put("password", s.password ?: JSONObject.NULL)
        put("token", s.token ?: JSONObject.NULL)
        put("createdAt", s.createdAt)
    }

    private fun messageJson(m: NtfyMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("subscriptionId", m.subscriptionId)
        put("serverUrl", m.serverUrl)
        put("topic", m.topic)
        put("title", m.title ?: JSONObject.NULL)
        put("body", m.body)
        put("time", m.time)
        put("unread", m.unread)
        put("priority", m.priority)
        put("tags", JSONArray(m.tags))
        put("clickUrl", m.clickUrl ?: JSONObject.NULL)
        put("attachmentName", m.attachmentName ?: JSONObject.NULL)
        put("attachmentUrl", m.attachmentUrl ?: JSONObject.NULL)
        put("attachmentSize", m.attachmentSize ?: JSONObject.NULL)
    }

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val PBKDF2_ITERATIONS = 120_000

        /** 备份文件在 WebDAV 上的默认路径。 */
        const val DEFAULT_REMOTE_PATH = "ntfy-hub/ntfy-hub-backup.json"
    }
}

/** 还原结果：订阅 + 消息 + 黑名单。 */
data class PersistedStatePlus(
    val state: PersistedState,
    val dismissedKeys: Set<String>,
)
