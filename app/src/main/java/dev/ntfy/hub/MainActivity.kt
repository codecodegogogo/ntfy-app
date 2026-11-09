package dev.ntfy.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import dev.ntfy.hub.service.Notifications
import dev.ntfy.hub.service.NtfyService
import dev.ntfy.hub.ui.HubApp
import dev.ntfy.hub.ui.HubViewModel
import dev.ntfy.hub.ui.TopicTarget
import dev.ntfy.hub.ui.screens.AppLanguage
import dev.ntfy.hub.ui.screens.FontSize
import dev.ntfy.hub.ui.theme.NtfyTheme
import dev.ntfy.hub.ui.theme.ThemeMode

class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private val viewModel: HubViewModel by viewModels { HubViewModel.factory(application as NtfyApp) }
    private var pendingTarget by mutableStateOf<TopicTarget?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTarget = targetFromIntent(intent)

        setContent {
            val prefs = remember { getSharedPreferences("ntfy_prefs", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var themeMode by remember {
                mutableStateOf(
                    when {
                        prefs.contains("theme_mode") -> runCatching {
                            ThemeMode.valueOf(
                                prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
                                    ?: ThemeMode.SYSTEM.name
                            )
                        }.getOrDefault(ThemeMode.SYSTEM)
                        // 兼容旧版本保存的 dark_theme 布尔值。
                        prefs.contains("dark_theme") ->
                            if (prefs.getBoolean("dark_theme", false)) ThemeMode.DARK else ThemeMode.LIGHT
                        else -> ThemeMode.SYSTEM
                    }
                )
            }
            var language by remember {
                mutableStateOf(
                    runCatching {
                        AppLanguage.valueOf(prefs.getString("app_language", AppLanguage.SYSTEM.name) ?: "SYSTEM")
                    }.getOrDefault(AppLanguage.SYSTEM)
                )
            }
            var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }
            var fontSize by remember {
                mutableStateOf(
                    runCatching {
                        FontSize.valueOf(prefs.getString("font_size", FontSize.MEDIUM.name) ?: FontSize.MEDIUM.name)
                    }.getOrDefault(FontSize.MEDIUM)
                )
            }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 应用内主题与系统主题可能不同，状态栏/导航栏图标要跟随应用内配色。
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            NtfyTheme(darkTheme = darkTheme) {
                // 字体大小：用 fontScale 全局缩放 sp 文本。
                val currentDensity = LocalDensity.current
                val scaledDensity = Density(
                    density = currentDensity.density,
                    fontScale = currentDensity.fontScale * fontSize.scale,
                )
                CompositionLocalProvider(
                    LocalDensity provides scaledDensity
                ) {
                    HubApp(
                        viewModel = viewModel,
                        pendingTarget = pendingTarget,
                        onPendingTargetHandled = { pendingTarget = null },
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            prefs.edit().putString("theme_mode", mode.name).apply()
                        },
                        language = language,
                        onLanguageChange = { lang ->
                            language = lang
                            prefs.edit().putString("app_language", lang.name).apply()
                            applyLanguage(lang)
                        },
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChange = { enabled ->
                            hapticsEnabled = enabled
                            prefs.edit().putBoolean("haptics_enabled", enabled).apply()
                        },
                        fontSize = fontSize,
                        onFontSizeChange = { size ->
                            fontSize = size
                            prefs.edit().putString("font_size", size.name).apply()
                        },
                    )
                }
            }
        }

        requestNotificationPermissionOnce()
        // 启动时恢复用户选择的应用内语言。
        applyLanguage(languageFromPrefs())
    }

    private fun languageFromPrefs(): AppLanguage = runCatching {
        AppLanguage.valueOf(
            getSharedPreferences("ntfy_prefs", MODE_PRIVATE)
                .getString("app_language", AppLanguage.SYSTEM.name) ?: "SYSTEM"
        )
    }.getOrDefault(AppLanguage.SYSTEM)

    /** 应用应用内语言：跟随系统时清空覆盖列表。 */
    private fun applyLanguage(lang: AppLanguage) {
        val locales = if (lang == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    override fun onStart() {
        super.onStart()
        (application as NtfyApp).repository.start()
        // 用户可能通过服务通知的「停止」结束过监听，回到前台时恢复。
        NtfyService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTarget = targetFromIntent(intent)
    }

    private fun targetFromIntent(intent: Intent?): TopicTarget? {
        val serverUrl = intent?.getStringExtra(Notifications.EXTRA_SERVER_URL) ?: return null
        val topic = intent.getStringExtra(Notifications.EXTRA_TOPIC) ?: return null
        val alias = (application as NtfyApp).repository.aliasOf(serverUrl, topic)
        return TopicTarget(serverUrl, topic, alias ?: topic)
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        val prefs = getSharedPreferences("ntfy_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
