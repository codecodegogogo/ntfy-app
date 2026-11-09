package dev.ntfy.hub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntfy.hub.ui.theme.InterFontFamily

/**
 * 未读数胶囊徽章：未读时红色、全部已读后绿色，
 * 与顶栏圆形按钮同风格（胶囊描边 + 白底）。
 */
@Composable
fun UnreadPillBadge(
    unread: Int,
    modifier: Modifier = Modifier,
    labelRead: String = "已读",
    labelUnread: String = "$unread 条未读",
) {
    val hasUnread = unread > 0
    val tint = if (hasUnread) {
        MaterialTheme.colorScheme.error
    } else {
        Color(0xFF10B981)
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (hasUnread) labelUnread else labelRead,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
