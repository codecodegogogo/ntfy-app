package dev.ntfy.hub.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ntfy.hub.data.ConnectionState
import dev.ntfy.hub.data.ServerStatus
import dev.ntfy.hub.ui.HubServer
import dev.ntfy.hub.ui.HubTopic
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.TopicTarget
import dev.ntfy.hub.ui.buildHubServers
import dev.ntfy.hub.ui.components.CircleIconButton
import dev.ntfy.hub.ui.components.CountBadge
import dev.ntfy.hub.ui.components.HubSnackbarHost
import dev.ntfy.hub.ui.components.LatencyLabel
import dev.ntfy.hub.ui.components.StatusDot
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.JakartaFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import dev.ntfy.hub.ui.theme.MonoFontFamily
import dev.ntfy.hub.ui.theme.ThemeMode
import dev.ntfy.hub.util.relativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    currentTarget: TopicTarget?,
    themeMode: ThemeMode,
    onOpenSettings: () -> Unit,
    onSelectTarget: (TopicTarget?) -> Unit,
    onAddSubscription: (String?) -> Unit,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val serverStates by viewModel.serverStates.collectAsStateWithLifecycle()
    val disconnectedServers by viewModel.disconnectedServers.collectAsStateWithLifecycle()
    val servers = remember(subscriptions, messages) { buildHubServers(subscriptions, messages) }
    val totalTopics = servers.sumOf { it.topics.size }
    val totalUnread = messages.count { it.unread }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<HubTopic?>(null) }
    // 长按服务器弹出的操作菜单目标。
    var serverMenuTarget by remember { mutableStateOf<HubServer?>(null) }
    // 长按主题弹出的更多操作目标（取消订阅/删除同名主题）。
    var topicMenuTarget by remember { mutableStateOf<HubTopic?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { HubSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HubHeader(
                onSettings = onOpenSettings,
                onAdd = { onAddSubscription(null) },
            )

            val offlineServer = servers.firstOrNull {
                // 用户主动断开的服务器不显示「连接失败」横幅。
                serverStates[it.serverUrl]?.state == ConnectionState.OFFLINE &&
                    it.serverUrl !in disconnectedServers
            }
            if (offlineServer != null) {
                OfflineBanner(
                    server = offlineServer,
                    status = serverStates[offlineServer.serverUrl],
                    onRetry = viewModel::refresh,
                )
            }

            if (servers.isEmpty()) {
                EmptyHub(onAdd = onAddSubscription)
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        viewModel.refresh()
                        scope.launch {
                            delay(700)
                            refreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(key = "all-subscriptions") {
                            AllSubscriptionsCard(
                                topicCount = totalTopics,
                                unread = totalUnread,
                                selected = currentTarget == null,
                                onClick = { onSelectTarget(null) },
                            )
                        }
                        items(servers, key = { it.serverUrl }) { server ->
                            ServerSection(
                                server = server,
                                status = serverStates[server.serverUrl] ?: ServerStatus(),
                                disconnected = server.serverUrl in disconnectedServers,
                                selectedTopic = currentTarget
                                    ?.takeIf { it.serverUrl == server.serverUrl }
                                    ?.topic,
                                onOpenTopic = { topic ->
                                    onSelectTarget(
                                        TopicTarget(server.serverUrl, topic.topic, topic.displayName)
                                    )
                                },
                                onLongPressTopic = { topicMenuTarget = it },
                                onLongPressServer = { serverMenuTarget = server },
                            )
                        }
                        item(key = "footer") {
                            Text(
                                text = stringResource(dev.ntfy.hub.R.string.footer_hint),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                textAlign = TextAlign.Center,
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                color = LocalHubPalette.current.muted.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(dev.ntfy.hub.R.string.unsubscribe_title), fontFamily = JakartaFontFamily) },
            text = { Text(stringResource(dev.ntfy.hub.R.string.unsubscribe_text, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeTopic(target.serverUrl, target.topic)
                        removeTarget = null
                    }
                ) {
                    Text(stringResource(dev.ntfy.hub.R.string.unsubscribe), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text(stringResource(dev.ntfy.hub.R.string.keep)) }
            },
        )
    }

    // 长按主题：取消订阅。
    topicMenuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { topicMenuTarget = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("#${target.displayName}", fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    stringResource(dev.ntfy.hub.R.string.unsubscribe_text, target.displayName),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuActionButton(
                        text = stringResource(dev.ntfy.hub.R.string.unsubscribe),
                        danger = true,
                        onClick = {
                            viewModel.removeTopic(target.serverUrl, target.topic)
                            topicMenuTarget = null
                        },
                    )
                    MenuActionButton(
                        text = stringResource(dev.ntfy.hub.R.string.cancel),
                        onClick = { topicMenuTarget = null },
                    )
                }
            },
        )
    }

    // 长按服务器：删除全部订阅 / 断开 / 重连。
    serverMenuTarget?.let { server ->
        val disconnected = server.serverUrl in disconnectedServers
        AlertDialog(
            onDismissRequest = { serverMenuTarget = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(server.displayName, fontFamily = JakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    if (disconnected) {
                        stringResource(dev.ntfy.hub.R.string.server_menu_hint_disconnected, server.displayName)
                    } else {
                        stringResource(dev.ntfy.hub.R.string.server_menu_hint_connected, server.displayName)
                    },
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuActionButton(
                        text = stringResource(dev.ntfy.hub.R.string.delete_all_subscriptions),
                        danger = true,
                        onClick = {
                            viewModel.removeServer(server.serverUrl)
                            serverMenuTarget = null
                        },
                    )
                    if (disconnected) {
                        MenuActionButton(
                            text = stringResource(dev.ntfy.hub.R.string.reconnect),
                            onClick = {
                                viewModel.reconnectServer(server.serverUrl)
                                serverMenuTarget = null
                            },
                        )
                    } else {
                        MenuActionButton(
                            text = stringResource(dev.ntfy.hub.R.string.disconnect),
                            danger = true,
                            onClick = {
                                viewModel.disconnectServer(server.serverUrl)
                                serverMenuTarget = null
                            },
                        )
                    }
                    MenuActionButton(
                        text = stringResource(dev.ntfy.hub.R.string.cancel),
                        onClick = { serverMenuTarget = null },
                    )
                }
            },
        )
    }
}

/**
 * 长按菜单里的操作按钮：与应用风格一致的边框按钮（圆角 + cardBorder 描边）。
 * danger = true 时文字用 error 色。
 */
@Composable
private fun MenuActionButton(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val palette = LocalHubPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 1.dp,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = text,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HubHeader(
    onSettings: () -> Unit,
    onAdd: () -> Unit,
) {
    val palette = LocalHubPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(dev.ntfy.hub.R.string.manage),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        // 夜间模式调节移到设置页，这里换成设置按钮。
        CircleIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(dev.ntfy.hub.R.string.open_settings),
            onClick = onSettings,
            tint = MaterialTheme.colorScheme.primary,
            size = 40.dp,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, palette.cardBorder),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(dev.ntfy.hub.R.string.subscribe),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AllSubscriptionsCard(
    topicCount: Int,
    unread: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalHubPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primary,
                0.06f,
            )
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else palette.cardBorder,
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(dev.ntfy.hub.R.string.all_subscriptions), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(dev.ntfy.hub.R.string.all_topics_count, topicCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unread > 0) CountBadge(unread)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerSection(
    server: HubServer,
    status: ServerStatus,
    disconnected: Boolean,
    selectedTopic: String?,
    onOpenTopic: (HubTopic) -> Unit,
    onLongPressTopic: (HubTopic) -> Unit,
    onLongPressServer: () -> Unit,
) {
    val palette = LocalHubPalette.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 长按服务器地址：更多操作（删除全部订阅 / 断开 / 重连）。
                .combinedClickable(onClick = {}, onLongClick = onLongPressServer)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (disconnected) ConnectionState.OFFLINE else status.state)
            Spacer(Modifier.width(4.dp))
            Text(
                text = server.displayName + if (disconnected) stringResource(dev.ntfy.hub.R.string.disconnected_suffix) else "",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (disconnected) {
                Text(
                    text = stringResource(dev.ntfy.hub.R.string.disconnected_label),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LatencyLabel(status)
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, palette.cardBorder),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                server.topics.forEachIndexed { index, topic ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            thickness = 1.dp,
                        )
                    }
                    TopicRow(
                        topic = topic,
                        selected = topic.topic == selectedTopic,
                        onClick = { onOpenTopic(topic) },
                        onLongClick = { onLongPressTopic(topic) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicRow(
    topic: HubTopic,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalHubPalette.current
    val latest = topic.latest
    val preview = latest?.let { message ->
        message.title?.takeIf { it.isNotBlank() }
            ?: message.body.replace('\n', ' ').ifBlank { "（空消息）" }
    } ?: "暂无消息"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary,
                        0.07f,
                    )
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.displayName,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (latest != null) {
                    Text(
                        text = " · ${relativeTime(latest.time)}",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = palette.muted,
                        maxLines = 1,
                    )
                }
            }
        }
        if (topic.unread > 0) {
            Spacer(Modifier.width(10.dp))
            CountBadge(topic.unread)
        }
    }
}

@Composable
private fun OfflineBanner(server: HubServer, status: ServerStatus?, onRetry: () -> Unit) {
    val stopped = status?.error == dev.ntfy.hub.data.NtfyRepository.STOPPED_REASON
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (stopped) {
                    "${server.displayName} " + stringResource(dev.ntfy.hub.R.string.listening_stopped)
                } else {
                    "${server.displayName} " + stringResource(dev.ntfy.hub.R.string.connection_failed)
                },
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = if (stopped) stringResource(dev.ntfy.hub.R.string.restore) else stringResource(dev.ntfy.hub.R.string.retry),
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyHub(onAdd: (String?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(text = stringResource(dev.ntfy.hub.R.string.no_subscription), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(dev.ntfy.hub.R.string.messages_auto_appear),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Surface(
            onClick = { onAdd(null) },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(dev.ntfy.hub.R.string.subscribe),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onAdd("https://ntfy.sh") }) {
            Text(
                text = "ntfy.sh",
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
