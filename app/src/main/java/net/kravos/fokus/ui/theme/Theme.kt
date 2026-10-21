package net.kravos.fokus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.kravos.fokus.data.ThemeMode

/** Accent colors shared by all screens. */
object K {
    val Accent = Color(0xFF7C8CFF)
    val Good = Color(0xFF30D158)
    val Warn = Color(0xFFFFB340)
    val Bad = Color(0xFFFF453A)
    val Strict = Color(0xFFFF6B5E)
    val Soft = Color(0xFF64D2FF)
    /** Series colors for top apps / categories. */
    val Series = listOf(
        Color(0xFF7C8CFF), Color(0xFF64D2FF), Color(0xFF30D158), Color(0xFFFFB340),
        Color(0xFFFF6B9A), Color(0xFFBF8CFF), Color(0xFF8E8E93),
    )
}

data class Palette(val dark: Boolean, val card: Color, val divider: Color, val dim: Color, val track: Color)
val LocalPalette = staticCompositionLocalOf { Palette(true, Color(0xFF15171D), Color(0xFF23262E), Color(0xFF8E8E93), Color(0xFF262A33)) }

private val Dark = darkColorScheme(
    primary = K.Accent,
    onPrimary = Color(0xFF0B1040),
    primaryContainer = Color(0xFF232A5C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB9C1CC),
    secondaryContainer = Color(0xFF262A35),
    onSecondaryContainer = Color(0xFFE6E9EE),
    tertiary = K.Soft,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF1C1E25),
    onSurfaceVariant = Color(0xFF9EA4AD),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0E12),
    surfaceContainer = Color(0xFF15171D),
    surfaceContainerHigh = Color(0xFF1C1E25),
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF5A6069),
    outlineVariant = Color(0xFF23262E),
    error = K.Bad,
)

private val Light = lightColorScheme(
    primary = Color(0xFF4655D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0B1040),
    secondary = Color(0xFF545C66),
    secondaryContainer = Color(0xFFE4E6F0),
    tertiary = Color(0xFF0A84C6),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F4),
    surfaceContainerHighest = Color(0xFFE6E6EB),
    outlineVariant = Color(0xFFE0E0E5),
)

private val Type = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
fun FokusTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val pal = if (dark) Palette(true, Color(0xFF15171D), Color(0xFF23262E), Color(0xFF8E8E93), Color(0xFF262A33))
    else Palette(false, Color.White, Color(0xFFE5E5EA), Color(0xFF8A8A8E), Color(0xFFE6E6EE))
    CompositionLocalProvider(LocalPalette provides pal) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}
