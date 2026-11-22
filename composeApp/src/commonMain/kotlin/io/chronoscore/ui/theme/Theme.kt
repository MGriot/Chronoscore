package io.chronoscore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chronoscore.resources.Res
import io.chronoscore.resources.inter
import io.chronoscore.resources.inter_tight
import io.chronoscore.viewmodel.ThemeMode
import org.jetbrains.compose.resources.Font

/** Design tokens from the Chronoscore UI kit (docs/ui-kit/01-foundations.png). */
@Immutable
data class ChronoColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val outline: Color,
    val muted: Color,
    val text: Color,
    /** Primary pill fill and its label. */
    val primary: Color,
    val onPrimary: Color,
    val destructive: Color,
    val positive: Color,
    val negative: Color,
    val isDark: Boolean,
)

private val DarkTokens = ChronoColors(
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF18181B),
    raised = Color(0xFF27272A),
    outline = Color(0xFF3F3F46),
    muted = Color(0xFFA1A1AA),
    text = Color(0xFFFFFFFF),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF09090B),
    destructive = Color(0xFFE11D48),
    positive = Color(0xFF4ADE80),
    negative = Color(0xFFFB7185),
    isDark = true,
)

private val LightTokens = ChronoColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFF4F4F5),
    raised = Color(0xFFE4E4E7),
    // At least 3:1 on background and surface, so control borders stay visible (WCAG 1.4.11).
    outline = Color(0xFF8A8A93),
    muted = Color(0xFF52525B),
    text = Color(0xFF09090B),
    primary = Color(0xFF09090B),
    onPrimary = Color(0xFFFFFFFF),
    destructive = Color(0xFFE11D48),
    positive = Color(0xFF15803D),
    negative = Color(0xFFE11D48),
    isDark = false,
)

@Immutable
data class ChronoType(
    /** Inter Tight Bold, tabular figures; size is set per use (auto-sized in seats). */
    val score: TextStyle,
    /** Counter value · 20 / 700. */
    val counter: TextStyle,
    /** Label · 12 / 500, +1 tracking, uppercase. */
    val label: TextStyle,
    /** Body & buttons · Inter 15 / 400. */
    val body: TextStyle,
)

object ChronoShapes {
    val control = RoundedCornerShape(6.dp)
    val dialog = RoundedCornerShape(10.dp)
    val hub = RoundedCornerShape(22.dp)
}

/** Every interactive target is at least this tall/wide. */
val MinTouch = 44.dp

private val LocalChronoColors = staticCompositionLocalOf { DarkTokens }
private val LocalChronoType = staticCompositionLocalOf<ChronoType> { error("ChronoscoreTheme missing") }

object ChronoTheme {
    val colors: ChronoColors
        @Composable @ReadOnlyComposable get() = LocalChronoColors.current
    val type: ChronoType
        @Composable @ReadOnlyComposable get() = LocalChronoType.current
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun chronoType(): ChronoType {
    fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))
    val inter = FontFamily(
        Font(Res.font.inter, FontWeight.Normal, variationSettings = weight(400)),
        Font(Res.font.inter, FontWeight.Medium, variationSettings = weight(500)),
        Font(Res.font.inter, FontWeight.SemiBold, variationSettings = weight(600)),
        Font(Res.font.inter, FontWeight.Bold, variationSettings = weight(700)),
    )
    val interTight = FontFamily(
        Font(Res.font.inter_tight, FontWeight.Bold, variationSettings = weight(700)),
    )
    return ChronoType(
        score = TextStyle(fontFamily = interTight, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = (-0.02).sp),
        counter = TextStyle(fontFamily = inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFeatureSettings = "tnum"),
        label = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp),
        body = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    )
}

@Composable
fun ChronoscoreTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkTokens else LightTokens
    val type = chronoType()
    val material = if (dark) {
        darkColorScheme(
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.raised, onSurfaceVariant = c.muted, outline = c.outline, outlineVariant = c.outline,
            primary = c.primary, onPrimary = c.onPrimary, error = c.destructive,
        )
    } else {
        lightColorScheme(
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.raised, onSurfaceVariant = c.muted, outline = c.outline, outlineVariant = c.outline,
            primary = c.primary, onPrimary = c.onPrimary, error = c.destructive,
        )
    }
    CompositionLocalProvider(LocalChronoColors provides c, LocalChronoType provides type) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

fun parseHex(hex: String): Color {
    val v = hex.removePrefix("#").toLongOrNull(16) ?: return Color.Gray
    return Color(0xFF000000 or v)
}

/**
 * Seat fill for the "Colored" style (and filled seat tags): the accent, deepened until white text
 * reaches [SEAT_TEXT_CONTRAST], so 85 % white labels still pass 4.5:1. Dark accents
 * (blue, violet) keep the light 12 % shade; bright ones (amber, lime, green) go deeper.
 */
fun seatFill(accent: Color): Color {
    var t = 0.12f
    var fill = lerp(accent, Color.Black, t)
    while (contrastWithWhite(fill) < SEAT_TEXT_CONTRAST && t < 0.6f) {
        t += 0.02f
        fill = lerp(accent, Color.Black, t)
    }
    return fill
}

private const val SEAT_TEXT_CONTRAST = 5.7f

private fun contrastWithWhite(c: Color): Float = 1.05f / (c.luminance() + 0.05f)
