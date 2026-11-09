package dev.ntfy.hub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ntfy.hub.data.Subscription
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.components.CircleIconButton
import dev.ntfy.hub.ui.components.HubTextField
import dev.ntfy.hub.ui.components.OrDivider
import dev.ntfy.hub.ui.components.SectionLabel
import dev.ntfy.hub.ui.components.TopicChip
import dev.ntfy.hub.ui.theme.InterFontFamily
import dev.ntfy.hub.ui.theme.LocalHubPalette
import dev.ntfy.hub.ui.theme.MonoFontFamily
import dev.ntfy.hub.util.isValidTopic
import dev.ntfy.hub.util.normalizeServerUrl
import dev.ntfy.hub.util.parseTopicInput
import kotlinx.coroutines.delay

/**
 * 添加/管理订阅弹窗。每一张卡片是一组「服务器 + 主题 + 凭据」，
 * 已有订阅会直接展示并允许修改；填写即自动保存，
 * 点「再次添加订阅」继续追加一组。
 */
@Composable
fun AddSubscriptionDialog(
    viewModel: HubViewModel,
    prefillUrl: String?,
    onDismiss: () -> Unit,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val drafts = remember {
        mutableStateListOf<DraftState>().apply {
            subscriptions.sortedBy { it.createdAt }.forEach { add(DraftState.from(it)) }
            if (prefillUrl != null) {
                add(DraftState(prefillUrl.ifBlank { DEFAULT_SERVER_URL }))
            }
            if (isEmpty()) add(DraftState(DEFAULT_SERVER_URL))
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val palette = LocalHubPalette.current

    // 关闭前把输入框里尚未确认的主题也保存下来。
    fun dismiss() {
        keyboard?.hide()
        drafts.forEach { it.flush(viewModel) }
        onDismiss()
    }

    BackHandler(onBack = ::dismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                // 键盘高度交给 imePadding 处理，避免与 safeDrawing 重复计算。
                contentWindowInsets = WindowInsets.systemBars,
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "添加订阅",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        CircleIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            onClick = ::dismiss,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 28.dp),
                    ) {
                        drafts.forEachIndexed { index, draft ->
                            DraftCard(
                                draft = draft,
                                index = index,
                                canRemove = drafts.size > 1 || draft.isSaved,
                                viewModel = viewModel,
                                scrollState = scrollState,
                                onRemove = {
                                    draft.forget(viewModel)
                                    drafts.remove(draft)
                                    if (drafts.isEmpty()) drafts.add(DraftState(DEFAULT_SERVER_URL))
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        Surface(
                            onClick = {
                                keyboard?.hide()
                                drafts.add(DraftState(DEFAULT_SERVER_URL))
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "再次添加订阅",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "填写后自动保存 · 支持 ntfy.sh 与自建服务器",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = palette.muted,
                        )
                    }
                }
            }
        }
}

private const val DEFAULT_SERVER_URL = "https://ntfy.sh"

private enum class SaveState { IDLE, SAVED, ERROR }

/** 一组订阅表单的可变状态，字段变化后自动保存。 */
private class DraftState(prefillUrl: String) {
    var serverUrl by mutableStateOf(prefillUrl)
    val topics: SnapshotStateList<String> = mutableStateListOf()
    /** 主题备注：key 是真实主题名，value 是显示名。 */
    val aliases: SnapshotStateMap<String, String> = mutableStateMapOf()
    var topicInput by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var token by mutableStateOf("")
    var credentialsExpanded by mutableStateOf(false)
    var showPassword by mutableStateOf(false)
    var showToken by mutableStateOf(false)

    var topicError by mutableStateOf<String?>(null)
    var saveState by mutableStateOf(SaveState.IDLE)
    var saveMessage by mutableStateOf<String?>(null)

    private var savedTopics: List<String> = emptyList()
    private var savedServerUrl: String? = null
    private var savedUsername: String? = null
    private var savedPassword: String? = null
    private var savedToken: String? = null

    /** 这张卡片是否对应已存在的订阅。 */
    val isSaved: Boolean get() = savedServerUrl != null

    companion object {
        /** 用已有订阅初始化卡片，打开弹窗后可直接管理。 */
        fun from(subscription: Subscription): DraftState =
            DraftState(subscription.serverUrl).apply {
                topics.addAll(subscription.topics)
                aliases.putAll(subscription.topicAliases)
                username = subscription.username.orEmpty()
                password = subscription.password.orEmpty()
                token = subscription.token.orEmpty()
                credentialsExpanded =
                    subscription.username != null || subscription.token != null
                savedTopics = subscription.topics.toList()
                savedServerUrl = subscription.serverUrl
                savedUsername = subscription.username
                savedPassword = subscription.password
                savedToken = subscription.token
                saveState = SaveState.SAVED
            }
    }

    fun addTopicToChips() {
        val (topic, alias) = parseTopicInput(topicInput)
        if (topic.isEmpty()) return
        if (!isValidTopic(topic)) {
            topicError = "主题仅支持字母、数字、- 和 _，长度 1–64"
            return
        }
        if (topics.any { it.equals(topic, ignoreCase = true) }) {
            topicError = "已添加该主题"
            return
        }
        if (topics.size >= 20) {
            topicError = "一次最多添加 20 个主题"
            return
        }
        topics.add(topic)
        if (alias != null) aliases[topic] = alias else aliases.remove(topic)
        topicInput = ""
        topicError = null
    }

    /** 移除主题 chip；若该主题已自动保存，同时取消订阅。 */
    fun removeTopic(topic: String, viewModel: HubViewModel) {
        topics.remove(topic)
        aliases.remove(topic)
        val url = savedServerUrl ?: return
        if (topic in savedTopics) {
            viewModel.removeTopic(url, topic)
            savedTopics = savedTopics - topic
        }
        if (topics.isEmpty()) {
            saveState = SaveState.IDLE
            saveMessage = null
        }
    }

    /** 删除整张卡片：把已自动保存的订阅一并取消。 */
    fun forget(viewModel: HubViewModel) {
        val url = savedServerUrl ?: return
        savedTopics.forEach { viewModel.removeTopic(url, it) }
        savedTopics = emptyList()
        savedServerUrl = null
    }

    /** 关闭弹窗时调用：把输入框里的主题转成 chip 并立即保存。 */
    fun flush(viewModel: HubViewModel) {
        val (pending, alias) = parseTopicInput(topicInput)
        if (pending.isNotEmpty() && isValidTopic(pending) &&
            topics.none { it.equals(pending, ignoreCase = true) }
        ) {
            topics.add(pending)
            if (alias != null) aliases[pending] = alias
            topicInput = ""
        }
        autoSave(viewModel)
    }

    /** 填写即保存：只提交尚未保存的主题，凭据变化时迁移已保存的主题。 */
    fun autoSave(viewModel: HubViewModel) {
        val url = normalizeServerUrl(serverUrl)
        if (url == null) {
            if (topics.isNotEmpty() && serverUrl.isNotBlank()) {
                saveState = SaveState.ERROR
                saveMessage = "请输入有效的服务器地址（http 或 https）"
            }
            return
        }
        if (topics.isEmpty()) {
            saveState = SaveState.IDLE
            saveMessage = null
            return
        }

        val tokenNow = token.trim().takeIf { it.isNotBlank() }
        val usernameNow = if (tokenNow == null) username.trim().takeIf { it.isNotBlank() } else null
        val passwordNow = if (tokenNow == null) password.takeIf { it.isNotBlank() } else null
        if (tokenNow == null && (usernameNow == null) != (passwordNow == null)) {
            saveMessage = "用户名和密码需要同时填写"
            return
        }

        val previousUrl = savedServerUrl
        val urlChanged = previousUrl != null && previousUrl != url
        val credentialsChanged =
            usernameNow != savedUsername || passwordNow != savedPassword || tokenNow != savedToken

        if (previousUrl == null) {
            // 首次保存：把当前所有主题写入。
            val error = viewModel.addSubscription(
                serverUrl, topics, username, password, token, topicAliases = aliases,
            )
            if (error == null || error.contains("已经订阅")) {
                markSaved(url, usernameNow, passwordNow, tokenNow)
            } else {
                saveState = SaveState.ERROR
                saveMessage = error
            }
            return
        }

        if (urlChanged) {
            // 服务器地址被修改：把已保存的主题从旧服务器迁走。
            savedTopics.forEach { viewModel.removeTopic(previousUrl, it) }
            val error = viewModel.addSubscription(
                serverUrl, topics, username, password, token, topicAliases = aliases,
            )
            if (error == null || error.contains("已经订阅")) {
                markSaved(url, usernameNow, passwordNow, tokenNow)
            } else {
                saveState = SaveState.ERROR
                saveMessage = error
            }
            return
        }

        val unsaved = topics.filterNot { it in savedTopics }
        if (unsaved.isNotEmpty()) {
            val error = viewModel.addSubscription(
                serverUrl, unsaved, username, password, token, topicAliases = aliases,
            )
            if (error != null && !error.contains("已经订阅")) {
                saveState = SaveState.ERROR
                saveMessage = error
                return
            }
            savedTopics = (savedTopics + unsaved).distinct()
            savedUsername = usernameNow
            savedPassword = passwordNow
            savedToken = tokenNow
            saveState = SaveState.SAVED
            saveMessage = null
            return
        }

        if (credentialsChanged) {
            val error = viewModel.moveTopics(
                serverUrl, savedTopics, username, password, token, topicAliases = aliases,
            )
            if (error != null) {
                saveState = SaveState.ERROR
                saveMessage = error
                return
            }
            savedUsername = usernameNow
            savedPassword = passwordNow
            savedToken = tokenNow
        }
        saveState = SaveState.SAVED
        saveMessage = null
    }

    private fun markSaved(
        url: String,
        username: String?,
        password: String?,
        token: String?,
    ) {
        savedTopics = topics.toList()
        savedServerUrl = url
        savedUsername = username
        savedPassword = password
        savedToken = token
        saveState = SaveState.SAVED
        saveMessage = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(
    draft: DraftState,
    index: Int,
    canRemove: Boolean,
    viewModel: HubViewModel,
    scrollState: androidx.compose.foundation.ScrollState,
    onRemove: () -> Unit,
) {
    val palette = LocalHubPalette.current

    // 主题或服务器地址变化后自动保存。
    LaunchedEffect(draft.topics.size, draft.serverUrl) {
        if (draft.topics.isNotEmpty()) {
            delay(700)
            draft.autoSave(viewModel)
        }
    }

    // 凭据停止输入 700ms 后自动保存（首次组合时也会执行，空表单会被忽略）。
    LaunchedEffect(draft.username, draft.password, draft.token) {
        delay(700)
        draft.autoSave(viewModel)
    }

    // 只有用户手动展开凭据时才滚到底部；已有订阅默认展开时不打扰。
    var credentialsTouched by remember { mutableStateOf(false) }
    LaunchedEffect(draft.credentialsExpanded) {
        if (!credentialsTouched) {
            credentialsTouched = true
            return@LaunchedEffect
        }
        if (draft.credentialsExpanded) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, palette.cardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "服务器 ${index + 1}",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))

                when (draft.saveState) {
                    SaveState.SAVED -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "已保存",
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    SaveState.ERROR -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "未保存",
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SaveState.IDLE -> Unit
                }

                if (canRemove) {
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "移除这组订阅",
                        onClick = onRemove,
                        size = 30.dp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel(icon = Icons.Rounded.Link, text = "订阅服务器地址")
            Spacer(Modifier.height(10.dp))

            val urlTrailing: (@Composable () -> Unit)? = if (draft.serverUrl.isNotEmpty()) {
                {
                    ClearIconButton {
                        draft.serverUrl = ""
                        draft.saveMessage = null
                    }
                }
            } else {
                null
            }
            HubTextField(
                value = draft.serverUrl,
                onValueChange = {
                    draft.serverUrl = it
                    draft.saveMessage = null
                },
                placeholder = "https://ntfy.example.com",
                leadingIcon = Icons.Rounded.Link,
                trailing = urlTrailing,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                mono = true,
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(Modifier.height(18.dp))

            SectionLabel(icon = Icons.Rounded.Tag, text = "订阅主题")
            Spacer(Modifier.height(10.dp))

            if (draft.topics.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.topics.forEach { topic ->
                        TopicChip(
                            topic = draft.aliases[topic] ?: topic,
                            onRemove = {
                                draft.removeTopic(topic, viewModel)
                                draft.topicError = null
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                HubTextField(
                    value = draft.topicInput,
                    onValueChange = {
                        draft.topicInput = it
                        draft.topicError = null
                    },
                    placeholder = "主题名称，如 komari//探针",
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Done,
                    onImeAction = draft::addTopicToChips,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = draft::addTopicToChips,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "添加主题",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            draft.topicError?.let { ErrorText(it) }
            Spacer(Modifier.height(7.dp))
            Text(
                text = "主题仅支持字母、数字、- 和 _；可用「主题//备注」起别名，如 komari//探针",
                fontFamily = InterFontFamily,
                fontSize = 10.sp,
                color = palette.muted,
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { draft.credentialsExpanded = !draft.credentialsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "此订阅的访问凭据",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "可选",
                        fontFamily = MonoFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val rotation by animateFloatAsState(
                    targetValue = if (draft.credentialsExpanded) 180f else 0f,
                    label = "credentials-chevron",
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (draft.credentialsExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = draft.credentialsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HubTextField(
                        value = draft.username,
                        onValueChange = {
                            draft.username = it
                            draft.saveMessage = null
                        },
                        placeholder = "用户名（可选）",
                        leadingIcon = Icons.Rounded.Person,
                        imeAction = ImeAction.Next,
                    )
                    Spacer(Modifier.height(10.dp))

                    val passwordTrailing: (@Composable () -> Unit) = {
                        EyeButton(
                            visible = draft.showPassword,
                            onClick = { draft.showPassword = !draft.showPassword },
                        )
                    }
                    HubTextField(
                        value = draft.password,
                        onValueChange = {
                            draft.password = it
                            draft.saveMessage = null
                        },
                        placeholder = "密码（可选）",
                        leadingIcon = Icons.Rounded.Lock,
                        trailing = passwordTrailing,
                        visualTransformation = if (draft.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        imeAction = ImeAction.Next,
                        mono = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OrDivider()
                    Spacer(Modifier.height(12.dp))

                    val tokenTrailing: (@Composable () -> Unit) = {
                        EyeButton(
                            visible = draft.showToken,
                            onClick = { draft.showToken = !draft.showToken },
                        )
                    }
                    HubTextField(
                        value = draft.token,
                        onValueChange = {
                            draft.token = it
                            draft.saveMessage = null
                        },
                        placeholder = "访问令牌 tk_…（可选）",
                        leadingIcon = Icons.Rounded.Key,
                        trailing = tokenTrailing,
                        visualTransformation = if (draft.showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        imeAction = ImeAction.Done,
                        onImeAction = { draft.credentialsExpanded = false },
                        mono = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "凭据仅保存在本机，用于访问受保护的 ntfy 服务器。",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        color = palette.muted,
                    )
                }
            }

            draft.saveMessage?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun ClearIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "清除",
            tint = LocalHubPalette.current.muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun EyeButton(visible: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (visible) "隐藏" else "显示",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
    )
}
