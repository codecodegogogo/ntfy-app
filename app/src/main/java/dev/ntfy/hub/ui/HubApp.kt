package dev.ntfy.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ntfy.hub.ui.screens.AddSubscriptionDialog
import dev.ntfy.hub.ui.screens.AppLanguage
import dev.ntfy.hub.ui.screens.FontSize
import dev.ntfy.hub.ui.screens.FeedScreen
import dev.ntfy.hub.ui.screens.HubScreen
import dev.ntfy.hub.ui.screens.SettingsScreen
import dev.ntfy.hub.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * 首页是消息流；向右滑动打开订阅管理侧栏；
 * 「订阅」按钮以弹窗形式添加订阅。
 */
@Composable
fun HubApp(
    viewModel: HubViewModel,
    pendingTarget: TopicTarget?,
    onPendingTargetHandled: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    fontSize: FontSize,
    onFontSizeChange: (FontSize) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    // null 表示「全部订阅」聚合视图。
    var currentTarget by remember { mutableStateOf<TopicTarget?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var prefillUrl by remember { mutableStateOf<String?>(null) }

    // 点击通知：首页切换到对应主题。
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        currentTarget = target
        onPendingTargetHandled()
    }

    // 订阅被取消后，首页回退到聚合视图。
    LaunchedEffect(subscriptions, currentTarget) {
        val target = currentTarget ?: return@LaunchedEffect
        val stillExists = subscriptions.any {
            it.serverUrl == target.serverUrl && target.topic in it.topics
        }
        if (!stillExists) currentTarget = null
    }

    fun selectTarget(target: TopicTarget?) {
        currentTarget = target
        scope.launch { drawerState.animateTo(DrawerValue.Closed, tween(200)) }
    }

    BackHandler(enabled = drawerState.isOpen && !showAddDialog) {
        scope.launch { drawerState.animateTo(DrawerValue.Closed, tween(200)) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.background,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    HubScreen(
                        viewModel = viewModel,
                        currentTarget = currentTarget,
                        themeMode = themeMode,
                        onOpenSettings = { showSettings = true },
                        onSelectTarget = { selectTarget(it) },
                        onAddSubscription = { prefill ->
                            prefillUrl = prefill
                            showAddDialog = true
                        },
                    )
                }
            },
        ) {
            FeedScreen(
                viewModel = viewModel,
                target = currentTarget,
                // 用快速 tween 而不是默认动画：侧栏滑出更快，不慢慢悠悠。
                onOpenDrawer = {
                    scope.launch { drawerState.animateTo(DrawerValue.Open, tween(220)) }
                },
            )
        }

        // 用应用内覆盖层而不是 Dialog：输入法 insets 与主窗口一致，避免键盘弹出时的黑框。
        if (showAddDialog) {
            AddSubscriptionDialog(
                viewModel = viewModel,
                prefillUrl = prefillUrl,
                onDismiss = { showAddDialog = false },
            )
        }

        if (showSettings) {
            // 设置页带左右滑入/滑出动画。
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    language = language,
                    onLanguageChange = onLanguageChange,
                    hapticsEnabled = hapticsEnabled,
                    onHapticsChange = onHapticsChange,
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    onDismiss = { showSettings = false },
                )
            }
        }
    }
}
