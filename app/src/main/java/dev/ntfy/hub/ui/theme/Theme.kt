package dev.ntfy.hub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF004643),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0F5F5B),
    onPrimaryContainer = Color(0xFF91D6D1),
    secondary = Color(0xFF1E6964),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA7EDE6),
    onSecondaryContainer = Color(0xFF246D68),
    tertiary = Color(0xFF623019),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF0B1C30),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF4FF),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceDim = Color(0xFFDCE4F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF7),
    surfaceContainerHighest = Color(0xFFDFE8F4),
    surfaceTint = Color(0xFF004643),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    inverseSurface = Color(0xFF213145),
    inverseOnSurface = Color(0xFFEAF1FF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED3CD),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF0F5F5B),
    onPrimaryContainer = Color(0xFFAAEFE9),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF00201E),
    secondaryContainer = Color(0xFF0F3D33),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFFB598),
    onTertiary = Color(0xFF360F00),
    background = Color(0xFF0A101C),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF111A2B),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceDim = Color(0xFF070C15),
    surfaceBright = Color(0xFF1E2A3F),
    surfaceContainerLowest = Color(0xFF0A101C),
    surfaceContainerLow = Color(0xFF0E1624),
    surfaceContainer = Color(0xFF111A2B),
    surfaceContainerHigh = Color(0xFF162033),
    surfaceContainerHighest = Color(0xFF1B2740),
    surfaceTint = Color(0xFF8ED3CD),
    outline = Color(0xFF3B4A61),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6EDF7),
    inverseOnSurface = Color(0xFF111A2B),
)

@Immutable
data class HubPalette(
    val cardBorder: Color,
    val iconBg: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val badgeBg: Color,
    val badgeBorder: Color,
    val badgeText: Color,
    val online: Color,
    val offline: Color,
    val connecting: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val warning: Color,
    val muted: Color,
)

private val LightPalette = HubPalette(
    cardBorder = Color(0xCCE2E8F0),
    iconBg = Color(0xFFF1F5F9),
    inputBg = Color(0xFFF8FAFC),
    inputBorder = Color(0xFFE2E8F0),
    badgeBg = Color(0x1A0F5F5B),
    badgeBorder = Color(0x330F5F5B),
    badgeText = Color(0xFF0F5F5B),
    online = Color(0xFF10B981),
    offline = Color(0xFFEF4444),
    connecting = Color(0xFFF59E0B),
    accentStart = Color(0xFF0F5F5B),
    accentEnd = Color(0xFF34D399),
    warning = Color(0xFFF59E0B),
    muted = Color(0xFF94A3B8),
)

private val DarkPalette = HubPalette(
    cardBorder = Color(0xFF1E293B),
    iconBg = Color(0xFF1A2438),
    inputBg = Color(0xFF0F1726),
    inputBorder = Color(0xFF243249),
    badgeBg = Color(0x1F34D399),
    badgeBorder = Color(0x4034D399),
    badgeText = Color(0xFF6EE7B7),
    online = Color(0xFF34D399),
    offline = Color(0xFFF87171),
    connecting = Color(0xFFFBBF24),
    accentStart = Color(0xFF2DD4BF),
    accentEnd = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    muted = Color(0xFF64748B),
)

val LocalHubPalette = staticCompositionLocalOf { LightPalette }

private val HubShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun NtfyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHubPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HubTypography,
            shapes = HubShapes,
            content = content,
        )
    }
}
