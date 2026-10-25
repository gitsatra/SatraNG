package com.v2ray.ang.ui.compose.satra

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * SatraNG design tokens.
 *
 * Depth is built from solid layered surfaces, hairline separators and soft ambient shadows —
 * there is no blur, glass or backdrop effect anywhere in the app. Orange is the single brand accent.
 */
@Immutable
class SatraPalette(
    val dark: Boolean,
    /** Page background. */
    val background: Color,
    /** Cards / grouped rows. */
    val surface: Color,
    /** Elevated layer on top of [surface] (sheets, popovers). */
    val surfaceRaised: Color,
    /** Inputs, tracks, chips. */
    val fill: Color,
    val fillStrong: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val scrim: Color,
    val shadow: Color,
)

object SatraBrand {
    val Orange = Color(0xFFFF5E00)
    val OrangeLight = Color(0xFFFF8A3D)
    val OrangeDeep = Color(0xFFE04F00)

    /** "Protected" tones the Home orb blends to once connected (orange stays the idle/brand colour). */
    val Green = Color(0xFF22C55E)
    val GreenLight = Color(0xFF5EE38F)
    val GreenDeep = Color(0xFF15913F)
}

val SatraDarkPalette = SatraPalette(
    dark = true,
    background = Color(0xFF09090B),
    surface = Color(0xFF151518),
    surfaceRaised = Color(0xFF1E1E22),
    fill = Color(0xFF232328),
    fillStrong = Color(0xFF2E2E34),
    separator = Color(0x1FFFFFFF),
    textPrimary = Color(0xFFF6F6F8),
    textSecondary = Color(0xFFA3A3AC),
    textTertiary = Color(0xFF6E6E78),
    accent = Color(0xFFFF6A1A),
    accentPressed = Color(0xFFE85A0C),
    accentSoft = Color(0xFF2B1709),
    onAccent = Color.White,
    success = Color(0xFF32D768),
    successSoft = Color(0xFF0F2A19),
    warning = Color(0xFFFFB524),
    warningSoft = Color(0xFF2E2208),
    danger = Color(0xFFFF4F45),
    dangerSoft = Color(0xFF2E1210),
    info = Color(0xFF4DA3FF),
    infoSoft = Color(0xFF10233A),
    scrim = Color(0x99000000),
    shadow = Color(0x66000000),
)

val SatraLightPalette = SatraPalette(
    dark = false,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFEEEEF3),
    fillStrong = Color(0xFFE2E2E9),
    separator = Color(0x1F3C3C43),
    textPrimary = Color(0xFF0E0E12),
    textSecondary = Color(0xFF62626C),
    textTertiary = Color(0xFF9A9AA5),
    accent = Color(0xFFF25A00),
    accentPressed = Color(0xFFD64E00),
    accentSoft = Color(0xFFFFEBDD),
    onAccent = Color.White,
    success = Color(0xFF1E9E4A),
    successSoft = Color(0xFFDDF5E5),
    warning = Color(0xFFD98300),
    warningSoft = Color(0xFFFFF0D2),
    danger = Color(0xFFE5362C),
    dangerSoft = Color(0xFFFDE3E1),
    info = Color(0xFF1F7AE0),
    infoSoft = Color(0xFFDFEDFF),
    scrim = Color(0x55000000),
    shadow = Color(0x1F000000),
)

object SatraShapes {
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val XLarge = RoundedCornerShape(28.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

object SatraSpace {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 28.dp
    val Xxxl = 40.dp
    /** Screen gutter. */
    val Gutter = 20.dp
    /** Apple HIG minimum hit target. */
    val MinTouch = 44.dp
}

@Immutable
class SatraTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title2: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val numberHuge: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val numberSmall: TextStyle,
)

private val VazirFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun buildTypography(family: FontFamily, rtl: Boolean): SatraTypography {
    // Persian glyphs sit lower and need more leading than the Latin metrics we tuned for.
    val k = if (rtl) 1.12f else 1f
    fun st(size: Float, line: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = (line * k).sp,
        fontWeight = weight,
        letterSpacing = if (rtl) 0.sp else spacing.sp,
    )
    val tabular = "tnum"
    return SatraTypography(
        largeTitle = st(34f, 40f, FontWeight.Bold, -0.6f),
        title = st(24f, 30f, FontWeight.Bold, -0.3f),
        title2 = st(20f, 26f, FontWeight.SemiBold, -0.2f),
        headline = st(17f, 22f, FontWeight.SemiBold, -0.1f),
        body = st(16f, 22f, FontWeight.Normal),
        callout = st(15f, 20f, FontWeight.Normal),
        subhead = st(14f, 19f, FontWeight.Normal),
        footnote = st(13f, 18f, FontWeight.Normal),
        caption = st(12f, 16f, FontWeight.Medium, 0.1f),
        numberHuge = st(52f, 58f, FontWeight.Bold, -1.2f).copy(fontFeatureSettings = tabular),
        numberLarge = st(28f, 34f, FontWeight.Bold, -0.5f).copy(fontFeatureSettings = tabular),
        numberMedium = st(17f, 22f, FontWeight.SemiBold).copy(fontFeatureSettings = tabular),
        numberSmall = st(14f, 18f, FontWeight.Medium).copy(fontFeatureSettings = tabular),
    )
}

internal val LocalSatraPalette = staticCompositionLocalOf { SatraDarkPalette }
internal val LocalSatraType = staticCompositionLocalOf { buildTypography(FontFamily.Default, false) }

/** Accessor: `Satra.colors.accent`, `Satra.type.headline`. */
object Satra {
    val colors: SatraPalette
        @Composable @ReadOnlyComposable get() = LocalSatraPalette.current
    val type: SatraTypography
        @Composable @ReadOnlyComposable get() = LocalSatraType.current
}

fun SatraPalette.accentGradient(): Brush = Brush.linearGradient(
    listOf(SatraBrand.OrangeLight, accent, SatraBrand.OrangeDeep),
)

fun SatraPalette.materialScheme(): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accent,
        secondary = info, onSecondary = Color.White, secondaryContainer = infoSoft, onSecondaryContainer = info,
        tertiary = warning, onTertiary = Color.Black, tertiaryContainer = warningSoft, onTertiaryContainer = warning,
        error = danger, onError = Color.White, errorContainer = dangerSoft, onErrorContainer = danger,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = fill, onSurfaceVariant = textSecondary,
        outline = textTertiary, outlineVariant = separator,
        inverseSurface = textPrimary, inverseOnSurface = background, inversePrimary = accentPressed,
        scrim = scrim, surfaceTint = Color.Transparent,
        surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = fillStrong,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentPressed,
        secondary = info, onSecondary = Color.White, secondaryContainer = infoSoft, onSecondaryContainer = info,
        tertiary = warning, onTertiary = Color.White, tertiaryContainer = warningSoft, onTertiaryContainer = warning,
        error = danger, onError = Color.White, errorContainer = dangerSoft, onErrorContainer = danger,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = fill, onSurfaceVariant = textSecondary,
        outline = textTertiary, outlineVariant = separator,
        inverseSurface = textPrimary, inverseOnSurface = background, inversePrimary = accentSoft,
        scrim = scrim, surfaceTint = Color.Transparent,
        surfaceContainerLowest = surface, surfaceContainerLow = background, surfaceContainer = fill,
        surfaceContainerHigh = fill, surfaceContainerHighest = fillStrong,
    )
}

private fun SatraTypography.toMaterial(): Typography = Typography(
    displayLarge = numberHuge,
    headlineLarge = largeTitle,
    headlineMedium = title,
    headlineSmall = title2,
    titleLarge = title2,
    titleMedium = headline,
    titleSmall = subhead.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = subhead.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = caption,
    labelSmall = caption.copy(fontSize = 11.sp),
)

@Composable
fun SatraTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val palette = if (dark) SatraDarkPalette else SatraLightPalette
    val lang = LocalConfiguration.current.locales.let { if (it.isEmpty) "" else it[0].language }
    val rtlScript = lang == "fa" || lang == "ar" || lang == "bqi" || lang == "ur"
    val type = androidx.compose.runtime.remember(rtlScript) {
        buildTypography(if (rtlScript) VazirFamily else FontFamily.Default, rtlScript)
    }
    CompositionLocalProvider(
        LocalSatraPalette provides palette,
        LocalSatraType provides type,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = palette.materialScheme(),
            typography = type.toMaterial(),
            content = content,
        )
    }
}
