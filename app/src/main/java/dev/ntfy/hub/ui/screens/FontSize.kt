package dev.ntfy.hub.ui.screens

/**
 * 字体大小：小号 = 当前默认大小（缩放 1.0），中号 = 1.1，大号 = 1.25。
 * 通过 Density.fontScale 全局缩放所有 sp 文本。
 */
enum class FontSize(val scale: Float) {
    SMALL(1.0f),
    MEDIUM(1.1f),
    LARGE(1.25f),
}
