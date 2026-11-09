package dev.ntfy.hub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntfy.hub.data.ConnectionState
import dev.ntfy.hub.data.ServerStatus
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import dev.ntfy.hub.ui.theme.MonoFontFamily
import java.util.Locale

@Composable
fun HubSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun CountBadge(count: Int, pulsing: Boolean = true) {
    val palette = LocalHubPalette.current
    val transition = rememberInfiniteTransition(label = "badge")
    val pulse by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "badge-pulse",
    )
    Surface(
        shape = CircleShape,
        color = palette.badgeBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.badgeBorder),
        modifier = Modifier
            .defaultMinSize(minWidth = 27.dp, minHeight = 23.dp)
            .alpha(if (pulsing) pulse else 1f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.coerceAtMost(999).toString(),
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = palette.badgeText,
            )
        }
    }
}

@Composable
fun StatusDot(state: ConnectionState, size: Dp = 8.dp) {
    val palette = LocalHubPalette.current
    val color = when (state) {
        ConnectionState.ONLINE -> palette.online
        ConnectionState.CONNECTING -> palette.connecting
        ConnectionState.OFFLINE -> palette.offline
    }
    val transition = rememberInfiniteTransition(label = "ping")
    val pingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing)),
        label = "ping-scale",
    )
    val pingAlpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing)),
        label = "ping-alpha",
    )
    Box(modifier = Modifier.size(size + 8.dp), contentAlignment = Alignment.Center) {
        if (state == ConnectionState.ONLINE) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(pingScale)
                    .alpha(pingAlpha)
                    .background(color, CircleShape)
            )
        }
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }
}

@Composable
fun LatencyLabel(status: ServerStatus) {
    val palette = LocalHubPalette.current
    val (color, text) = when (status.state) {
        ConnectionState.ONLINE -> palette.online to (
            status.latencyMs?.let { latency ->
                if (latency < 1000) "${latency}ms" else String.format(Locale.US, "%.1fs", latency / 1000.0)
            } ?: "在线"
        )
        ConnectionState.CONNECTING -> palette.connecting to "连接中"
        ConnectionState.OFFLINE -> palette.offline to "离线"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(5.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = color,
        )
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 38.dp,
) {
    val palette = LocalHubPalette.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 1.dp,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun HubTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    mono: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalHubPalette.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else palette.inputBorder,
        label = "field-border",
    )
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(palette.inputBg)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (mono) MonoFontFamily else InterFontFamily,
                fontSize = 14.sp,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onNext = { onImeAction() },
                onGo = { onImeAction() },
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = if (mono) MonoFontFamily else InterFontFamily,
                            fontSize = 13.sp,
                            color = palette.muted.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun TopicChip(topic: String, onRemove: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = CircleShape,
        color = primary.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = primary.copy(alpha = 0.65f),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = topic,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = primary,
            )
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.10f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "移除主题 $topic",
                    tint = primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
fun OrDivider() {
    val palette = LocalHubPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "OR",
            fontFamily = MonoFontFamily,
            fontSize = 10.sp,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 分段选择器（SegmentedControl）：浅灰底圆角容器，选中项白底浮起。
 * 容器高度与选中项一致（只保留 3dp 内边距），用于设置页的「外观 / 语言」等单选场景。
 * 点击带触感反馈，选中底块有滑动动画。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHubPalette.current
    val haptics = LocalHapticFeedback.current
    val itemShape = CircleShape
    Surface(
        shape = itemShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        modifier = modifier,
    ) {
        // 上下不留灰边：灰容器与白色选中块上下贴合（只保留左右 3dp 间隔）。
        Row(modifier = Modifier.padding(horizontal = 3.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelected(index)
                    },
                    shape = itemShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                    } else {
                        null
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = InterFontFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
                if (index < options.lastIndex) Spacer(Modifier.width(2.dp))
            }
        }
    }
}

@Composable
fun UnreadDot(size: Dp = 8.dp) {
    val palette = LocalHubPalette.current
    val transition = rememberInfiniteTransition(label = "unread")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "unread-scale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(palette.online, CircleShape)
    )
}

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
    )
}

private val UrlRegex = Regex("""https?://[^\s]+""")

private fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    UrlRegex.findAll(text).forEach { match ->
        append(text.substring(last, match.range.first))
        val raw = match.value.trimEnd('.', ',', ';', ':', ')', ']', '。', '，', '；', '：', '）')
        if (raw.isNotEmpty()) {
            val link = LinkAnnotation.Url(
                url = raw,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                ),
            )
            withLink(link) { append(raw) }
            append(match.value.substring(raw.length))
        } else {
            append(match.value)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

data class TopicVisual(val icon: ImageVector, val tint: Color? = null)

@Composable
fun topicVisual(topic: String): TopicVisual {
    val palette = LocalHubPalette.current
    val name = topic.lowercase()
    return when {
        name.hasAny("alert", "warn", "alarm", "告警", "警报") ->
            TopicVisual(Icons.Rounded.WarningAmber, palette.warning)

        name.hasAny("backup", "snapshot", "备份", "快照") ->
            TopicVisual(Icons.Rounded.Backup)

        name.hasAny("deploy", "release", "ci", "发布", "部署") ->
            TopicVisual(Icons.Rounded.RocketLaunch)

        name.hasAny("nas", "storage", "disk", "存储") ->
            TopicVisual(Icons.Rounded.Storage)

        name.hasAny("homelab", "home", "cluster", "集群") ->
            TopicVisual(Icons.Rounded.Dns)

        name.hasAny("monitor", "cpu", "load", "health", "监控", "负载") ->
            TopicVisual(Icons.Rounded.MonitorHeart)

        name.hasAny("security", "auth", "shield", "安全") ->
            TopicVisual(Icons.Rounded.Shield)

        name.hasAny("docker", "container", "容器") ->
            TopicVisual(Icons.Rounded.Inventory2)

        name.hasAny("git", "build", "编译") ->
            TopicVisual(Icons.Rounded.Build)

        name.hasAny("weather", "天气") ->
            TopicVisual(Icons.Rounded.Cloud)

        name.hasAny("mail", "email", "邮件") ->
            TopicVisual(Icons.Rounded.Email)

        name.hasAny("chat", "消息") ->
            TopicVisual(Icons.AutoMirrored.Rounded.Chat)

        name.hasAny("code", "api", "代码") ->
            TopicVisual(Icons.Rounded.Code)

        else -> TopicVisual(Icons.Rounded.Terminal)
    }
}

private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it) }
