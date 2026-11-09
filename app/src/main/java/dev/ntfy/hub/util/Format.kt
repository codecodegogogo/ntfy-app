package dev.ntfy.hub.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 规范化服务器地址：补全协议、去掉末尾斜杠。返回 null 表示地址无效。 */
fun normalizeServerUrl(raw: String): String? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (!value.startsWith("http://", ignoreCase = true) &&
        !value.startsWith("https://", ignoreCase = true)
    ) {
        value = "https://$value"
    }
    val parsed = value.toHttpUrlOrNull() ?: return null
    if (parsed.host.isBlank()) return null
    if (parsed.scheme != "http" && parsed.scheme != "https") return null
    return value.trimEnd('/')
}

fun normalizeTopic(raw: String): String = raw.trim().removePrefix("#").trim()

/** ntfy 主题名只允许 ASCII 字母、数字、短横线和下划线，最长 64 字符。 */
fun isValidTopic(topic: String): Boolean = topic.matches(Regex("^[-_A-Za-z0-9]{1,64}$"))

/**
 * 解析「主题//备注」输入，例如 `komari//探针`。
 * 主题用于实际连接，备注只用于界面显示；没有备注时返回 null。
 */
fun parseTopicInput(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    val separator = trimmed.indexOf("//")
    if (separator < 0) return normalizeTopic(trimmed) to null
    val topic = normalizeTopic(trimmed.substring(0, separator))
    val alias = trimmed.substring(separator + 2).trim().take(32).ifBlank { null }
    return topic to alias
}

/** 主题的显示名：有备注用备注，否则用主题本身。 */
fun topicDisplayName(topic: String, alias: String?): String =
    alias?.takeIf { it.isNotBlank() } ?: topic

fun serverDisplayName(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    val isDefaultPort = (parsed.scheme == "https" && parsed.port == 443) ||
        (parsed.scheme == "http" && parsed.port == 80)
    val port = if (isDefaultPort) "" else ":${parsed.port}"
    val path = parsed.encodedPath.trimEnd('/')
    return buildString {
        append(parsed.host)
        append(port)
        if (path.isNotEmpty() && path != "/") append(path)
    }
}

fun relativeTime(timeMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timeMillis).coerceAtLeast(0)
    return when {
        diff < 45_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000}天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timeMillis))
    }
}

fun clockTime(timeMillis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm:ss" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMillis))
}
