package dev.ntfy.hub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ntfy.hub.data.BackupManager
import dev.ntfy.hub.data.PersistedState
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.components.CircleIconButton
import dev.ntfy.hub.ui.components.HubTextField
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.JakartaFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import kotlinx.coroutines.launch

/**
 * 备份 / 还原（独立全屏页面）：
 * - 备份：可选密码加密 → 上传 WebDAV；
 * - 还原：从 WebDAV 下载，填密码解密后覆盖本地数据。
 */
@Composable
fun BackupScreen(
    viewModel: HubViewModel,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = LocalHubPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    val haptics = LocalHapticFeedback.current
    fun tap() {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // 表单状态
    var backupPassword by remember { mutableStateOf("") }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    fun notify(text: String, isError: Boolean) {
        message = text
        messageIsError = isError
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
                text = stringResource(dev.ntfy.hub.R.string.backup_title),
                fontFamily = JakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(dev.ntfy.hub.R.string.close),
                onClick = { tap(); onDismiss() },
                size = 38.dp,
            )
        }
        Spacer(Modifier.height(14.dp))

        // ── 备份区 ──
        Text(
            text = stringResource(dev.ntfy.hub.R.string.backup_step),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        ActionButton(
            text = stringResource(dev.ntfy.hub.R.string.upload_webdav),
            enabled = !busy && (backupPassword.isBlank() || backupPassword.length >= 6),
            hapticsEnabled = hapticsEnabled,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val json = backupManager.exportJson(
                            state = PersistedState(
                                viewModel.subscriptions.value,
                                viewModel.messages.value,
                            ),
                            dismissedKeys = viewModel.dismissedKeys.value,
                            password = backupPassword.takeIf { it.isNotBlank() },
                        )
                        backupManager.uploadToWebDav(
                            url = webDavUrl,
                            username = webDavUser,
                            password = webDavPassword,
                            content = json,
                            remotePath = BackupManager.DEFAULT_REMOTE_PATH,
                        )
                        notify(context.getString(dev.ntfy.hub.R.string.upload_ok), false)
                    } catch (e: Exception) {
                        notify(e.message ?: context.getString(dev.ntfy.hub.R.string.webdav_upload_failed_generic), true)
                    } finally {
                        busy = false
                    }
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        HubTextField(value = webDavUrl, onValueChange = { webDavUrl = it }, placeholder = stringResource(dev.ntfy.hub.R.string.webdav_url_hint), mono = true)
        Spacer(Modifier.height(8.dp))
        HubTextField(value = webDavUser, onValueChange = { webDavUser = it }, placeholder = stringResource(dev.ntfy.hub.R.string.webdav_user_hint))
        Spacer(Modifier.height(8.dp))
        HubTextField(
            value = webDavPassword,
            onValueChange = { webDavPassword = it },
            placeholder = stringResource(dev.ntfy.hub.R.string.webdav_password_hint),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        // 备份密码（可选）：填写则加密备份，留空则明文备份。
        HubTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it },
            placeholder = stringResource(dev.ntfy.hub.R.string.backup_password_hint),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = palette.cardBorder)
        Spacer(Modifier.height(16.dp))

        // ── 还原区 ──
        Text(
            text = stringResource(dev.ntfy.hub.R.string.restore_step),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        HubTextField(
            value = restorePassword,
            onValueChange = { restorePassword = it },
            placeholder = stringResource(dev.ntfy.hub.R.string.restore_password_hint),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(10.dp))
        ActionButton(
            text = stringResource(dev.ntfy.hub.R.string.restore_from_webdav),
            enabled = !busy,
            hapticsEnabled = hapticsEnabled,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val text = backupManager.downloadFromWebDav(
                            url = webDavUrl,
                            username = webDavUser,
                            password = webDavPassword,
                            remotePath = BackupManager.DEFAULT_REMOTE_PATH,
                        )
                        val plus = backupManager.parseBackup(text, restorePassword.takeIf { it.isNotBlank() })
                        viewModel.restoreBackup(plus)
                        notify(context.getString(dev.ntfy.hub.R.string.restore_ok, plus.state.subscriptions.size, plus.state.messages.size), false)
                    } catch (e: Exception) {
                        notify(e.message ?: context.getString(dev.ntfy.hub.R.string.webdav_download_failed_generic), true)
                    } finally {
                        busy = false
                    }
                }
            },
        )

        message?.let { text ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = if (messageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
            )
        }
    }
}
