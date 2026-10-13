package nl.nielsnotsmurf.cleanui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Gym white": a bright, high-contrast palette meant to be read from a distance
 * under gym lighting. Black is the primary action colour, one accent marks load and
 * the main Start action, and each lifter brings their own colour.
 *
 * The accent follows the trainer LED colour picked in Settings (orange until one is picked).
 * Reads are snapshot state, so screens recolour as soon as it changes.
 */
object CleanColors {
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF3F4F6)
    val Ink = Color(0xFF0F1217)
    val Muted = Color(0xFF6B7280)
    val Line = Color(0xFFE7E9ED)
    val Success = Color(0xFF12805C)
    val Danger = Color(0xFFDC2626)

    val DefaultAccent = Color(0xFFFF5A1F)

    private var led by mutableStateOf(DefaultAccent)

    /** The LED colour as it is, for glows. */
    val Glow: Color get() = led

    /** The LED colour, darkened where needed so white text and icons stay readable on it. */
    val Accent: Color get() = darkenTo(led, maxLuminance = 0.32f)
    val AccentSoft: Color get() = lerp(led, Color.White, 0.86f)

    /** Accent for text on white. */
    val AccentInk: Color get() = darkenTo(led, maxLuminance = 0.15f)

    fun setAccent(color: Color) {
        led = color
    }

    private fun darkenTo(color: Color, maxLuminance: Float): Color {
        var t = 0f
        var c = color
        while (c.luminance() > maxLuminance && t < 0.8f) {
            t += 0.05f
            c = lerp(color, Color.Black, t)
        }
        return c
    }
}

private fun cleanColorScheme() = lightColorScheme(
    primary = CleanColors.Ink,
    onPrimary = Color.White,
    primaryContainer = CleanColors.Surface,
    onPrimaryContainer = CleanColors.Ink,
    secondary = CleanColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = CleanColors.AccentSoft,
    onSecondaryContainer = CleanColors.AccentInk,
    tertiary = CleanColors.Success,
    onTertiary = Color.White,
    background = CleanColors.Background,
    onBackground = CleanColors.Ink,
    surface = CleanColors.Background,
    onSurface = CleanColors.Ink,
    surfaceVariant = CleanColors.Surface,
    onSurfaceVariant = CleanColors.Muted,
    surfaceContainer = CleanColors.Surface,
    surfaceContainerLow = CleanColors.Background,
    surfaceContainerHigh = CleanColors.Surface,
    surfaceContainerHighest = CleanColors.Line,
    outline = CleanColors.Line,
    outlineVariant = CleanColors.Line,
    error = CleanColors.Danger,
    onError = Color.White
)

private val CleanTypography = Typography(
    displayLarge = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 96.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = CleanColors.Muted),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
)

private val CleanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CleanTheme(content: @Composable () -> Unit) {
    val accent = CleanColors.Accent
    val colorScheme = remember(accent) { cleanColorScheme() }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CleanTypography,
        shapes = CleanShapes,
        content = content
    )
}

/** Lifter colours, picked to stay readable on white. Falls back by index when a profile colour is too light. */
object LifterColors {
    private val palette = listOf(
        Color(0xFF2563EB), // blue
        Color(0xFFD9467A), // pink
        Color(0xFF0D9488), // teal
        Color(0xFF7C3AED)  // violet
    )

    fun forLifter(colorArgb: Int, index: Int): Color {
        val c = Color(colorArgb)
        val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        return if (luminance in 0.08f..0.6f) c else palette[index % palette.size]
    }
}
