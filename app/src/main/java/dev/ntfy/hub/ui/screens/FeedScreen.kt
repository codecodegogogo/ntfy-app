package dev.ntfy.hub.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ntfy.hub.data.NtfyMessage
import dev.ntfy.hub.service.Notifications
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.TopicTarget
import dev.ntfy.hub.ui.components.CircleIconButton
import dev.ntfy.hub.ui.components.HubSnackbarHost
import dev.ntfy.hub.ui.components.LinkifiedText
import dev.ntfy.hub.ui.components.UnreadDot
import dev.ntfy.hub.ui.components.UnreadPillBadge
import dev.ntfy.hub.ui.components.topicVisual
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.JakartaFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import dev.ntfy.hub.ui.theme.MonoFontFamily
import dev.ntfy.hub.util.serverDisplayName
import dev.ntfy.hub.util.clockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页信息流：默认显示全部订阅的聚合消息，也可通过侧栏切换到单个主题。
 * 样式对齐设计稿：卡片首行为「图标 + 服务器/主题」，有附件才显示下载按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: HubViewModel,
    target: TopicTarget?,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val allMessages by viewModel.messages.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val messages = remember(allMessages, target) {
        if (target == null || target.isAggregate) {
            allMessages
        } else {
            allMessages.filter {
                it.serverUrl == target.serverUrl && it.topic == target.topic
            }
        }
    }
    val unread = messages.count { it.unread }
    val title = target?.title ?: context.getString(dev.ntfy.hub.R.string.all_subscriptions)
    // 主题备注映射：消息卡片显示用户设置的别名。
    val aliasMap = remember(subscriptions) {
        buildMap {
            subscriptions.forEach { subscription ->
                subscription.topics.forEach { topic ->
                    subscription.topicAliases[topic]?.let { alias ->
                        put("${subscription.serverUrl}|$topic", alias)
                    }
                }
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openedAt = remember(target) { System.currentTimeMillis() - 2_000L }
    val listState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }

    var showClearDialog by remember { mutableStateOf(false) }
    var showUnsubscribeDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // 打开页面之后新到的消息自动算已读，已有的未读保持原样。
    // 只有列表停在顶部时才滚到最新并标记已读；用户正在翻看历史时不打扰。
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val atTop = listState.firstVisibleItemIndex <= 2 &&
            listState.firstVisibleItemScrollOffset == 0
        if (atTop) {
            viewModel.markReadSince(target?.serverUrl, target?.topic, openedAt)
            listState.animateScrollToItem(0)
        }
    }

    DisposableEffect(target) {
        val key = target?.takeIf { !it.isAggregate }?.let { "${it.serverUrl}|${it.topic}" }
        if (key != null) Notifications.visibleTopicKey = key
        onDispose {
            if (key != null && Notifications.visibleTopicKey == key) {
                Notifications.visibleTopicKey = null
            }
        }
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
            FeedHeader(
                title = title,
                unread = unread,
                hasMessages = messages.isNotEmpty(),
                canUnsubscribe = target != null && !target.isAggregate,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                onOpenDrawer = onOpenDrawer,
                onMarkAllRead = {
                    viewModel.markAllRead(target?.serverUrl, target?.topic)
                    scope.launch { snackbar.showSnackbar(context.getString(dev.ntfy.hub.R.string.all_marked_read)) }
                },
                onClear = { showClearDialog = true },
                onUnsubscribe = {
                    menuExpanded = false
                    showUnsubscribeDialog = true
                },
            )

            // 下拉刷新：聚合视图重拉全部订阅；单主题视图只重拉当前主题。
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    val current = target
                    if (current != null && !current.isAggregate) {
                        viewModel.refreshTopic(current.serverUrl.orEmpty(), current.topic.orEmpty())
                    } else {
                        viewModel.refresh()
                    }
                    scope.launch {
                        delay(700)
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (messages.isEmpty()) {
                    EmptyFeed(
                        hasSubscriptions = subscriptions.isNotEmpty(),
                        onOpenDrawer = onOpenDrawer,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(messages, key = { it.key }) { message ->
                            MessageCard(
                                message = message,
                                topicName = aliasMap["${message.serverUrl}|${message.topic}"]
                                    ?: message.topic,
                                showTopic = target == null || target.isAggregate,
                                onMarkRead = { viewModel.markMessageRead(message.key) },
                                onDelete = {
                                    val removed = viewModel.deleteMessage(message.key)
                                    if (removed != null) {
                                        scope.launch {
                                            val result = snackbar.showSnackbar(
                                                message = context.getString(dev.ntfy.hub.R.string.deleted_one),
                                                actionLabel = context.getString(dev.ntfy.hub.R.string.undo),
                                                duration = SnackbarDuration.Long,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreMessage(removed)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "footer") {
                            Text(
                                text = stringResource(dev.ntfy.hub.R.string.no_more_messages),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                textAlign = TextAlign.Center,
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                color = LocalHubPalette.current.muted.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空消息？", fontFamily = JakartaFontFamily) },
            text = { Text("将删除 ${messages.size} 条本地消息，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMessages(target?.serverUrl, target?.topic)
                        showClearDialog = false
                        scope.launch { snackbar.showSnackbar(context.getString(dev.ntfy.hub.R.string.cleared)) }
                    }
                ) {
                    Text(stringResource(dev.ntfy.hub.R.string.clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(dev.ntfy.hub.R.string.cancel)) }
            },
        )
    }

    if (showUnsubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeDialog = false },
            title = { Text(stringResource(dev.ntfy.hub.R.string.unsubscribe_title), fontFamily = JakartaFontFamily) },
            text = { Text(stringResource(dev.ntfy.hub.R.string.unsubscribe_text, target?.title ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val serverUrl = target?.serverUrl
                        val topic = target?.topic
                        showUnsubscribeDialog = false
                        if (serverUrl != null && topic != null) {
                            viewModel.removeTopic(serverUrl, topic)
                        }
                    }
                ) {
                    Text(stringResource(dev.ntfy.hub.R.string.unsubscribe), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribeDialog = false }) { Text(stringResource(dev.ntfy.hub.R.string.keep)) }
            },
        )
    }
}

@Composable
private fun FeedHeader(
    title: String,
    unread: Int,
    hasMessages: Boolean,
    canUnsubscribe: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClear: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val palette = LocalHubPalette.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.Rounded.Menu,
                contentDescription = stringResource(dev.ntfy.hub.R.string.open_drawer),
                onClick = onOpenDrawer,
                size = 38.dp,
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 设计稿：标题（当前主题/全部订阅）+ 未读数胶囊徽章。
                Text(
                    text = title,
                    fontFamily = JakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                UnreadPillBadge(unread = unread, labelRead = stringResource(dev.ntfy.hub.R.string.all_read), labelUnread = stringResource(dev.ntfy.hub.R.string.unread_count, unread))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.DoneAll,
                    contentDescription = stringResource(dev.ntfy.hub.R.string.mark_all_read),
                    onClick = onMarkAllRead,
                    enabled = unread > 0,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 36.dp,
                )
                CircleIconButton(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(dev.ntfy.hub.R.string.clear_messages),
                    onClick = onClear,
                    enabled = hasMessages,
                    size = 36.dp,
                )
                if (canUnsubscribe) {
                    Box {
                        CircleIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(dev.ntfy.hub.R.string.more),
                            onClick = { onMenuExpandedChange(true) },
                            size = 36.dp,
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { onMenuExpandedChange(false) },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(dev.ntfy.hub.R.string.unsubscribe),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = onUnsubscribe,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = palette.cardBorder,
            thickness = 1.dp,
        )
    }
}

@Composable
private fun MessageCard(
    message: NtfyMessage,
    topicName: String,
    showTopic: Boolean,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHubPalette.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val accent = if (message.unread) {
        listOf(palette.accentStart, palette.accentEnd)
    } else {
        listOf(palette.muted.copy(alpha = 0.30f), palette.muted.copy(alpha = 0.30f))
    }
    val hasTitle = !message.title.isNullOrBlank()
    val clickUrl = message.clickUrl
    val clickable = !clickUrl.isNullOrBlank()
    // 设计稿：卡片首行是「图标 + 服务器/主题」等宽字体标识。
    val sourceLabel = remember(message.serverUrl, message.topic) {
        "${serverDisplayName(message.serverUrl)}/${message.topic}"
    }
    val sourceIcon = topicVisual(message.topic)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (clickable) {
                    Modifier.clickable { uriHandler.openUri(clickUrl) }
                } else {
                    Modifier
                }
            ),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 未读左侧竖条（设计稿 unread-pill）。
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(accent))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = sourceIcon.icon,
                        contentDescription = null,
                        tint = sourceIcon.tint ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sourceLabel,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (hasTitle) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.title.orEmpty(),
                            fontFamily = JakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (message.unread) {
                            Spacer(Modifier.width(8.dp))
                            UnreadDot()
                        }
                    }
                    if (message.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LinkifiedText(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinkifiedText(
                            text = message.body.ifBlank { context.getString(dev.ntfy.hub.R.string.empty_message) },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (message.unread) {
                            Spacer(Modifier.width(8.dp))
                            UnreadDot()
                        }
                    }
                }

                if (message.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        message.tags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.iconBg,
                            ) {
                                Text(
                                    text = tag,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(8.dp))
                // 设计稿：日期两行等宽字体；右侧下载/已读/删除，下载按钮只有带附件的消息才有。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = clockDate(message.time),
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = palette.muted,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = clockHms(message.time),
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = palette.muted,
                    )
                    Spacer(Modifier.weight(1f))
                    if (message.hasAttachment) {
                        CircleIconButton(
                            icon = Icons.Rounded.Download,
                            contentDescription = stringResource(dev.ntfy.hub.R.string.download_attachment),
                            onClick = { downloadAttachment(context, message) },
                            tint = MaterialTheme.colorScheme.primary,
                            size = 34.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleIconButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = stringResource(dev.ntfy.hub.R.string.mark_read),
                        onClick = onMarkRead,
                        enabled = message.unread,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 34.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(dev.ntfy.hub.R.string.delete_message),
                        onClick = onDelete,
                        size = 34.dp,
                    )
                }
            }
        }
    }
}

/** 用系统 DownloadManager（下载管理器）把附件保存到公共下载目录。 */
private fun downloadAttachment(context: Context, message: NtfyMessage) {
    val url = message.attachmentUrl ?: return
    val fileName = message.attachmentName ?: "ntfy-attachment"
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle(fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        allowScanningByMediaScanner()
    }
    context.getSystemService(DownloadManager::class.java).enqueue(request)
}

private fun clockDate(timeMillis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))

private fun clockHms(timeMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))

@Composable
private fun EmptyFeed(hasSubscriptions: Boolean, onOpenDrawer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (hasSubscriptions) stringResource(dev.ntfy.hub.R.string.no_messages) else stringResource(dev.ntfy.hub.R.string.no_subscription),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasSubscriptions) {
                stringResource(dev.ntfy.hub.R.string.messages_auto_appear)
            } else {
                stringResource(dev.ntfy.hub.R.string.swipe_to_manage)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasSubscriptions) {
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onOpenDrawer) {
                Text(stringResource(dev.ntfy.hub.R.string.open_manage), fontFamily = InterFontFamily, fontSize = 13.sp)
            }
        }
    }
}
