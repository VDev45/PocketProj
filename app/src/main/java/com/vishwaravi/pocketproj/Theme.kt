package com.vishwaravi.pocketproj

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import androidx.compose.ui.unit.sp

/* ────────────────────────────────────────────────────────────────────────────
   Voket design tokens.

   The visual language is a soft, tonal, "expressive" one: lavender accents,
   wide-radius surfaces, pill controls and a small number of organic shapes.
   Everything below is expressed as a token so screens never invent their own
   colours, radii or durations.
   ──────────────────────────────────────────────────────────────────────────── */

/** Linear blend between two colours. Used to derive decorative tones from the scheme. */
fun Color.mixWith(other: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = alpha + (other.alpha - alpha) * t
    )
}

/**
 * Semantic colour tokens. Surfaces form a tonal ladder (canvas → lowest → low →
 * base → raised → high → highest) so depth comes from tone, not from borders.
 */
object PocketColors {

    // Accent family — the single saturated colour in the app.
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val AccentSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val OnAccentSoft: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer

    // Tonal surface ladder.
    val Canvas: Color @Composable get() = MaterialTheme.colorScheme.background
    val SurfaceLowest: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val SurfaceLow: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceRaised: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val SurfaceHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val SurfaceHighest: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    // Content.
    val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val InkMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val InkFaint: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Hairline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

    // Secondary (sage) — used for "private / safe" affordances.
    val Sage: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val OnSage: Color @Composable get() = MaterialTheme.colorScheme.onSecondary
    val SageSoft: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val OnSageSoft: Color @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer

    // Tertiary (amber) — reserved for favourites and warm highlights.
    val Amber: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val OnAmber: Color @Composable get() = MaterialTheme.colorScheme.onTertiary
    val AmberSoft: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val OnAmberSoft: Color @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer

    // Destructive.
    val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val OnDanger: Color @Composable get() = MaterialTheme.colorScheme.onError
    val DangerSoft: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    val OnDangerSoft: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer

    // PIN keypad: a self-contained pair that stays legible in both themes.
    val KeypadFace: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val KeypadGlyph: Color @Composable get() = MaterialTheme.colorScheme.onSurface

    /* ── Focal washes ────────────────────────────────────────────────────────
       Hero surfaces get a *whisper* of the accent rather than a saturated fill,
       so the type sitting on them stays the loudest thing on the page. */
    // ratio is the amount of the destination colour. Keep only a small accent tint on surfaces.
    val HeroWash: Color @Composable get() = Accent.mixWith(Surface, 0.91f)
    val HeroWashStrong: Color @Composable get() = Accent.mixWith(Surface, 0.84f)
    val OnHeroWash: Color @Composable get() = Ink

    /* Decorative layer — deliberately faint. The backdrop is a texture, not a
       subject: these are mixed almost to the canvas so the page reads as calm
       tonal ground and the content supplies the colour. */
    val BlobLilac: Color @Composable get() = Accent.mixWith(Canvas, 0.87f)
    val BlobMint: Color @Composable get() = Sage.mixWith(Canvas, 0.89f)
    val BlobAmber: Color @Composable get() = Amber.mixWith(Canvas, 0.90f)
    val BlobRose: Color @Composable get() = Danger.mixWith(Canvas, 0.92f)
    val Mist: Color @Composable get() = Accent.mixWith(Canvas, 0.97f)
}

/* ────────────────────────────────────────────────────────────────────────────
   Contrast checking
   ──────────────────────────────────────────────────────────────────────────── */

/**
 * Flattens a translucent colour onto an opaque background the way the renderer would, so a
 * `copy(alpha = …)` text colour can be measured against the surface it actually sits on.
 *
 * Composited in sRGB, which is what Skia does for a plain window surface.
 */
internal fun Color.compositedOver(background: Color): Color = Color(
    red = red * alpha + background.red * (1f - alpha),
    green = green * alpha + background.green * (1f - alpha),
    blue = blue * alpha + background.blue * (1f - alpha),
    alpha = 1f
)

private fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) + 0.0722f * linear(color.blue)
}

/**
 * WCAG 2.1 contrast ratio between two opaque colours: 1.0 when identical, 21.0 for black on
 * white. WCAG AA wants 4.5:1 for body text, and 3:1 for large text and for non-text UI such as
 * icons and button glyphs.
 *
 * This exists so a colour pairing can be asserted in a test rather than judged from a screenshot.
 * The Collections hero CTA shipped at 2.4:1 in light theme and 1.3:1 in dark because a reviewer
 * read "Accent on AccentSoft" as slightly washed out and nobody measured it.
 */
internal fun contrastRatio(foreground: Color, background: Color): Float {
    val fg = relativeLuminance(foreground)
    val bg = relativeLuminance(background)
    return (maxOf(fg, bg) + 0.05f) / (minOf(fg, bg) + 0.05f)
}

/** Spacing rhythm. Gaps between major groups are deliberately large. */
object PocketSpace {
    val hair: Dp = 2.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 18.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 44.dp
    val huge: Dp = 64.dp

    /**
     * Quiet zone. Reserved for breathing room around focal content and around
     * the navigation, so sections do not run into one another and each zone
     * gets a moment of stillness.
     */
    val quiet: Dp = 28.dp
}

/** Shape scale — never one radius for everything. */
object PocketShape {
    val xs: Shape = RoundedCornerShape(10.dp)
    val sm: Shape = RoundedCornerShape(14.dp)
    val md: Shape = RoundedCornerShape(18.dp)
    val lg: Shape = RoundedCornerShape(24.dp)
    val xl: Shape = RoundedCornerShape(30.dp)
    val xxl: Shape = RoundedCornerShape(38.dp)

    /** The largest tier, reserved for the single hero surface on a screen. */
    val sheet: Shape = RoundedCornerShape(46.dp)

    val pill: Shape = RoundedCornerShape(percent = 50)
    val circle: Shape = RoundedCornerShape(percent = 50)

    /**
     * A soft asymmetric "squircle" used for icon containers so repeated icons
     * do not read as a grid of identical circles.
     */
    val squircle: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 10.dp, bottomEnd = 16.dp, bottomStart = 10.dp)

    /** Organic blob — reserved for empty states, avatars and one hero accent. */
    val blob: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w * 0.40f, 0f)
        cubicTo(w * 0.82f, h * 0.01f, w * 1.03f, h * 0.30f, w * 0.97f, h * 0.64f)
        cubicTo(w * 0.91f, h * 0.99f, w * 0.54f, h * 1.07f, w * 0.26f, h * 0.95f)
        cubicTo(-w * 0.01f, h * 0.84f, -w * 0.05f, h * 0.38f, w * 0.15f, h * 0.12f)
        cubicTo(w * 0.23f, h * 0.01f, w * 0.32f, h * -0.01f, w * 0.40f, 0f)
        close()
    }

    /** A second organic silhouette so decorative pairs never look cloned. */
    val pebble: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w * 0.16f, h * 0.10f)
        cubicTo(w * 0.46f, h * -0.04f, w * 1.00f, h * 0.10f, w * 0.98f, h * 0.52f)
        cubicTo(w * 0.96f, h * 0.94f, w * 0.50f, h * 1.10f, w * 0.20f, h * 0.88f)
        cubicTo(-w * 0.06f, h * 0.69f, w * -0.04f, h * 0.20f, w * 0.16f, h * 0.10f)
        close()
    }
}

/** Elevation is a last resort — tone does most of the separating work. */
object PocketElevation {
    val flat: Dp = 0.dp
    val hairline: Dp = 1.dp
    val whisper: Dp = 2.dp
    val lifted: Dp = 3.dp
    val floating: Dp = 10.dp
    val overlay: Dp = 16.dp
}

/** Motion tokens. Every duration below respects the system animation scale. */
object PocketMotion {
    const val instant: Int = 90
    const val micro: Int = 140
    const val normal: Int = 240
    const val large: Int = 380
    const val expressive: Int = 520
}

private val animationsEnabled: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        true
    }

/** Tween that collapses to a snap when the user has disabled animations. */
fun <T> pocketTween(durationMillis: Int): DurationBasedAnimationSpec<T> =
    if (animationsEnabled) tween(durationMillis = durationMillis) else snap()

/* ── Typography ───────────────────────────────────────────────────────────── */

private val Tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Double = 0.0,
    family: FontFamily = FontFamily.SansSerif
) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = family,
    letterSpacing = tracking.sp,
    lineHeightStyle = Tight
)

/** The type scale. Hierarchy is created by size, weight and contrast. */
object PocketType {
    val display: TextStyle = style(34, FontWeight.Black, 40, -0.8)
    val headline: TextStyle = style(26, FontWeight.ExtraBold, 32, -0.5)
    val headlineSmall: TextStyle = style(22, FontWeight.Bold, 28, -0.3)
    val title: TextStyle = style(19, FontWeight.Bold, 25, -0.2)
    val titleSmall: TextStyle = style(16, FontWeight.Bold, 22, -0.1)
    val body: TextStyle = style(14, FontWeight.Normal, 21)
    val bodyStrong: TextStyle = style(14, FontWeight.SemiBold, 21)
    val bodySmall: TextStyle = style(13, FontWeight.Normal, 19)
    val label: TextStyle = style(12, FontWeight.SemiBold, 16, 0.1)
    val caption: TextStyle = style(11, FontWeight.Medium, 15, 0.2)
    val overline: TextStyle = style(11, FontWeight.ExtraBold, 14, 1.1)
    val button: TextStyle = style(15, FontWeight.Bold, 20, 0.1)
    val numeric: TextStyle = style(30, FontWeight.Black, 34, -1.0)
    val mono: TextStyle = style(11, FontWeight.Medium, 15, 0.2, FontFamily.Monospace)
    val glyph: TextStyle = style(17, FontWeight.Black, 20)
}

private val PocketTypography = Typography(
    displayLarge = PocketType.display,
    displayMedium = PocketType.display.copy(fontSize = 30.sp, lineHeight = 36.sp),
    displaySmall = PocketType.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineLarge = PocketType.headline,
    headlineMedium = PocketType.headlineSmall,
    headlineSmall = PocketType.title,
    titleLarge = PocketType.title,
    titleMedium = PocketType.titleSmall,
    titleSmall = PocketType.bodyStrong,
    bodyLarge = PocketType.body,
    bodyMedium = PocketType.bodySmall,
    bodySmall = PocketType.caption,
    labelLarge = PocketType.button,
    labelMedium = PocketType.label,
    labelSmall = PocketType.caption
)

/* ── Colour schemes ───────────────────────────────────────────────────────── */

internal val VoketLight = lightColorScheme(
    primary = Color(0xFF6644A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBE0FF),
    onPrimaryContainer = Color(0xFF23064F),
    inversePrimary = Color(0xFFD6BBFF),
    secondary = Color(0xFF4C6558),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFEBDC),
    onSecondaryContainer = Color(0xFF0A2018),
    tertiary = Color(0xFF7B5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDFAFF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFEAE3F2),
    onSurfaceVariant = Color(0xFF4C4654),
    surfaceTint = Color(0xFF6644A6),
    inverseSurface = Color(0xFF31303A),
    inverseOnSurface = Color(0xFFF4EFF7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7D7787),
    outlineVariant = Color(0xFFCDC6D4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDFAFF),
    surfaceDim = Color(0xFFDDD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FB),
    surfaceContainer = Color(0xFFF2ECF7),
    surfaceContainerHigh = Color(0xFFECE6F1),
    surfaceContainerHighest = Color(0xFFE6E0EB),
)

internal val VoketDark = darkColorScheme(
    primary = Color(0xFFD6BBFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F3A7C),
    onPrimaryContainer = Color(0xFFEBE0FF),
    inversePrimary = Color(0xFF6644A6),
    secondary = Color(0xFFB4CFBF),
    onSecondary = Color(0xFF1F3529),
    secondaryContainer = Color(0xFF344C40),
    onSecondaryContainer = Color(0xFFCFEBDC),
    tertiary = Color(0xFFEBC248),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF131118),
    onBackground = Color(0xFFE7E0EB),
    surface = Color(0xFF16141C),
    onSurface = Color(0xFFE7E0EB),
    surfaceVariant = Color(0xFF4C4654),
    onSurfaceVariant = Color(0xFFCEC7D5),
    surfaceTint = Color(0xFFD6BBFF),
    inverseSurface = Color(0xFFE7E0EB),
    inverseOnSurface = Color(0xFF31303A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF978F9E),
    outlineVariant = Color(0xFF4C4654),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3742),
    surfaceDim = Color(0xFF131118),
    surfaceContainerLowest = Color(0xFF0E0B12),
    surfaceContainerLow = Color(0xFF1D1A23),
    surfaceContainer = Color(0xFF211E28),
    surfaceContainerHigh = Color(0xFF2C2832),
    surfaceContainerHighest = Color(0xFF37323D),
)

/* ── Theme entry point ────────────────────────────────────────────────────── */

val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/**
 * Voket theme modes:
 * - Default: the Voket light tonal palette
 * - Dark: the Voket dark tonal palette
 * - Material You: Android 12+ dynamic system colours (same expressive tokens)
 */
@Composable
fun PocketProjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    materialYou: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> VoketDark
        else -> VoketLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketTypography,
        shapes = PocketShapes,
        content = content
    )
}
