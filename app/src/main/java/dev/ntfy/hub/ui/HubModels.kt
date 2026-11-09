package dev.ntfy.hub.ui

import dev.ntfy.hub.data.NtfyMessage
import dev.ntfy.hub.data.Subscription
import dev.ntfy.hub.util.serverDisplayName

data class TopicTarget(
    val serverUrl: String?,
    val topic: String?,
    val title: String,
) {
    val isAggregate: Boolean get() = serverUrl == null || topic == null
}

data class HubTopic(
    val serverUrl: String,
    val topic: String,
    val subscriptionId: String,
    val unread: Int,
    val latest: NtfyMessage?,
    /** 用户设置的备注名，用于界面显示。 */
    val alias: String? = null,
) {
    val displayName: String get() = alias?.takeIf { it.isNotBlank() } ?: topic
}

data class HubServer(
    val serverUrl: String,
    val displayName: String,
    val topics: List<HubTopic>,
    val unread: Int,
)

fun buildHubServers(
    subscriptions: List<Subscription>,
    messages: List<NtfyMessage>,
): List<HubServer> {
    if (subscriptions.isEmpty()) return emptyList()
    val messagesByTopic = messages.groupBy { it.topicKey }
    val refs = LinkedHashMap<String, HubTopic>()

    for (subscription in subscriptions.sortedBy { it.createdAt }) {
        for (topic in subscription.topics) {
            val key = "${subscription.serverUrl}|$topic"
            if (refs.containsKey(key)) continue
            val topicMessages = messagesByTopic[key].orEmpty()
            refs[key] = HubTopic(
                serverUrl = subscription.serverUrl,
                topic = topic,
                subscriptionId = subscription.id,
                unread = topicMessages.count { it.unread },
                latest = topicMessages.maxByOrNull { it.time },
                alias = subscription.topicAliases[topic],
            )
        }
    }

    return refs.values
        .groupBy { it.serverUrl }
        .map { (serverUrl, topics) ->
            HubServer(
                serverUrl = serverUrl,
                displayName = serverDisplayName(serverUrl),
                topics = topics.sortedWith(
                    compareByDescending<HubTopic> { it.latest?.time ?: 0L }
                        .thenBy { it.topic }
                ),
                unread = topics.sumOf { it.unread },
            )
        }
        .sortedByDescending { server ->
            server.topics.maxOfOrNull { it.latest?.time ?: 0L } ?: 0L
        }
}
