package dev.ntfy.hub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.components.CircleIconButton
import dev.ntfy.hub.ui.components.SegmentedControl
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.JakartaFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import dev.ntfy.hub.ui.theme.ThemeMode

/**
 * 设置页（独立全屏页面，带左右滑入动画）：
 * 1. 外观：跟随系统 / 日间 / 夜间（行内左侧标签 + 右侧分段选择器）；
 * 2. 语言：跟随系统 / 中文 / EN；
 * 3. 触感反馈开关；
 * 4. 备份与还原入口（独立全屏页面）。
 */
@Composable
fun SettingsScreen(
    viewModel: HubViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    fontSize: FontSize,
    onFontSizeChange: (FontSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHubPalette.current
    val haptics = LocalHapticFeedback.current
    var showBackup by remember { mutableStateOf(false) }
    fun tap() {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(dev.ntfy.hub.R.string.settings),
                fontFamily = JakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(dev.ntfy.hub.R.string.close_settings),
                onClick = { tap(); onDismiss() },
                size = 38.dp,
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── 外观：一行（左标签 + 右分段选择器） ──
        SettingsRow(label = stringResource(dev.ntfy.hub.R.string.appearance)) {
            SegmentedControl(
                options = listOf(
                    stringResource(dev.ntfy.hub.R.string.follow_system),
                    stringResource(dev.ntfy.hub.R.string.light_mode),
                    stringResource(dev.ntfy.hub.R.string.dark_mode),
                ),
                selectedIndex = themeMode.ordinal,
                onSelected = {
                    tap()
                    onThemeModeChange(ThemeMode.entries[it])
                },
            )
        }
        RowDivider()

        // ── 语言 ──
        SettingsRow(label = stringResource(dev.ntfy.hub.R.string.language)) {
            SegmentedControl(
                options = listOf(
                    stringResource(dev.ntfy.hub.R.string.follow_system),
                    "中文",
                    "EN",
                ),
                selectedIndex = language.ordinal,
                onSelected = {
                    tap()
                    onLanguageChange(AppLanguage.entries[it])
                },
            )
        }
        RowDivider()

        // ── 字体大小：小号 / 中号 / 大号 ──
        SettingsRow(label = stringResource(dev.ntfy.hub.R.string.font_size)) {
            SegmentedControl(
                options = listOf(
                    stringResource(dev.ntfy.hub.R.string.font_small),
                    stringResource(dev.ntfy.hub.R.string.font_medium),
                    stringResource(dev.ntfy.hub.R.string.font_large),
                ),
                selectedIndex = fontSize.ordinal,
                onSelected = {
                    tap()
                    onFontSizeChange(FontSize.entries[it])
                },
            )
        }
        RowDivider()

        // ── 触感反馈开关 ──
        SettingsRow(label = stringResource(dev.ntfy.hub.R.string.haptics)) {
            Switch(
                checked = hapticsEnabled,
                onCheckedChange = {
                    onHapticsChange(it)
                    if (it) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
            )
        }
        RowDivider()

        // ── 备份与还原入口 ──
        SectionHeading(
            icon = Icons.Rounded.CloudUpload,
            text = stringResource(dev.ntfy.hub.R.string.backup_restore),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(dev.ntfy.hub.R.string.backup_desc),
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = { tap(); showBackup = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(dev.ntfy.hub.R.string.open_backup),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    // 备份与还原：独立全屏页面（左右滑入）。
    AnimatedVisibility(
        visible = showBackup,
        enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        BackupScreen(
            viewModel = viewModel,
            hapticsEnabled = hapticsEnabled,
            onDismiss = { showBackup = false },
        )
    }
}

/** 设置页通用行：左侧标签 + 右侧内容，同行排列、垂直居中。 */
@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

/** 行与行之间的细分隔线。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(color = LocalHubPalette.current.cardBorder.copy(alpha = 0.6f))
}

/** 区块标题（带图标），用于备份与还原。 */
@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
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
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 应用语言：跟随系统 / 简体中文 / 英文。tag 传给 AppCompatDelegate.setApplicationLocales。 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ZH_CN("zh-CN"),
    EN("en"),
}
