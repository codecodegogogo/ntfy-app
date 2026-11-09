package dev.ntfy.hub.ui.theme

/** 应用外观模式：跟随系统 / 日间 / 夜间。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}
