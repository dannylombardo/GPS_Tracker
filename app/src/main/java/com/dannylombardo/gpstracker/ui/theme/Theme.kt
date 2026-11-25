package com.dannylombardo.gpstracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dannylombardo.gpstracker.R

/*
 * An Apple-style look: the iOS system colours (blue tint, grouped grey background, white
 * cards; true black in dark mode), Inter type set at the iOS sizes, and generously rounded
 * shapes. Glass surfaces (bars, tab bar, cards) get their colours from [GlassColors].
 */

/** Light, dark, or whatever the phone is set to. Picked in Settings. */
enum class ThemeMode { System, Light, Dark }

/** Whether the app is drawing in dark mode, which can differ from the phone's setting. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The iOS system colours the app uses beyond the Material roles. */
@Immutable
data class AppleColors(
    val blue: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val teal: Color,
    val indigo: Color,
    val yellow: Color,
    /** The grey behind grouped content. */
    val groupedBackground: Color,
    /** Cards and grouped rows. */
    val card: Color,
    /** Fills for tracks, unselected controls and thumbnails. */
    val fill: Color,
    val separator: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
)

private val LightApple = AppleColors(
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    teal = Color(0xFF30B0C7),
    indigo = Color(0xFF5856D6),
    yellow = Color(0xFFFFCC00),
    groupedBackground = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    fill = Color(0xFFE9E9EE),
    separator = Color(0xFFD1D1D6),
    secondaryLabel = Color(0xFF6E6E73),
    tertiaryLabel = Color(0xFFAEAEB2),
)

private val DarkApple = AppleColors(
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    teal = Color(0xFF40C8E0),
    indigo = Color(0xFF5E5CE6),
    yellow = Color(0xFFFFD60A),
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    fill = Color(0xFF2C2C2E),
    separator = Color(0xFF38383A),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF636366),
)

val LocalAppleColors = staticCompositionLocalOf { LightApple }

/** Shorthand for the iOS colours: `MaterialTheme.apple.green`. */
val MaterialTheme.apple: AppleColors
    @Composable get() = LocalAppleColors.current

private fun lightScheme(c: AppleColors) = lightColorScheme(
    primary = c.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EFFF),
    onPrimaryContainer = Color(0xFF0060CC),
    secondary = c.secondaryLabel,
    onSecondary = Color.White,
    secondaryContainer = c.fill,
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF0F98AE),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF4F8),
    onTertiaryContainer = Color(0xFF0B6F80),
    error = c.red,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFFC4221A),
    background = c.groupedBackground,
    onBackground = Color.Black,
    surface = c.groupedBackground,
    onSurface = Color.Black,
    surfaceVariant = c.fill,
    onSurfaceVariant = c.secondaryLabel,
    outline = c.tertiaryLabel,
    outlineVariant = c.separator,
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF0A84FF),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
    surfaceBright = c.card,
    surfaceDim = c.groupedBackground,
    surfaceContainerLowest = c.card,
    surfaceContainerLow = c.card,
    surfaceContainer = c.card,
    surfaceContainerHigh = c.card,
    surfaceContainerHighest = c.fill,
)

private fun darkScheme(c: AppleColors) = darkColorScheme(
    primary = c.blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B2A4F),
    onPrimaryContainer = Color(0xFF7AB8FF),
    secondary = c.secondaryLabel,
    onSecondary = Color.Black,
    secondaryContainer = c.fill,
    onSecondaryContainer = Color.White,
    tertiary = c.teal,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF0D3840),
    onTertiaryContainer = Color(0xFF7FE0F0),
    error = c.red,
    onError = Color.White,
    errorContainer = Color(0xFF3D1412),
    onErrorContainer = Color(0xFFFF8F87),
    background = c.groupedBackground,
    onBackground = Color.White,
    surface = c.groupedBackground,
    onSurface = Color.White,
    surfaceVariant = c.fill,
    onSurfaceVariant = c.secondaryLabel,
    outline = c.tertiaryLabel,
    outlineVariant = c.separator,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF007AFF),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = c.groupedBackground,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = c.card,
    surfaceContainer = c.card,
    surfaceContainerHigh = c.card,
    surfaceContainerHighest = c.fill,
)

/** Where a drive started and ended, on the map and in route previews. */
object RouteColors {
    val start = Color(0xFF34C759)
    val end = Color(0xFFFF3B30)
}

/** Route colours on the drive map, slowest to fastest. Red under 10 km/h, yellow to 50, green to 90, purple above. */
object SpeedColors {
    val crawling = Color(0xFFE5392F)
    val slow = Color(0xFFF2B705)
    val fast = Color(0xFF26A65B)
    val fastest = Color(0xFF8E44E0)
}

/** The coloured headline cards: blue for driving, teal for fuel, green while recording. Same in light and dark. */
object HeroColors {
    val drive = listOf(Color(0xFF2F8CFF), Color(0xFF3F4FD8))
    val fuel = listOf(Color(0xFF1EB6C9), Color(0xFF0E7F8F))
    val live = listOf(Color(0xFF3ACB63), Color(0xFF178A44))
    val content = Color.White
}

/** Inter: the closest open typeface to Apple's San Francisco. Display cuts for the big sizes. */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val InterDisplay = FontFamily(
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: TextUnit,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
)

/*
 * The iOS type ramp: Large Title 34, Title 1 28, Title 2 22, Title 3 20, Headline 17
 * semibold, Body 17, Subheadline 15, Footnote 13, Caption 12 and 11.
 */
private val AppTypography = Typography(
    displayLarge = style(InterDisplay, 56, 60, FontWeight.Bold, (-1.2).sp),
    displayMedium = style(InterDisplay, 44, 50, FontWeight.Bold, (-0.9).sp),
    displaySmall = style(InterDisplay, 36, 42, FontWeight.Bold, (-0.6).sp),
    headlineLarge = style(InterDisplay, 34, 41, FontWeight.Bold, (-0.5).sp),
    headlineMedium = style(InterDisplay, 28, 34, FontWeight.Bold, (-0.4).sp),
    headlineSmall = style(InterDisplay, 22, 28, FontWeight.Bold, (-0.3).sp),
    titleLarge = style(Inter, 20, 25, FontWeight.SemiBold, (-0.4).sp),
    titleMedium = style(Inter, 17, 22, FontWeight.SemiBold, (-0.4).sp),
    titleSmall = style(Inter, 15, 20, FontWeight.SemiBold, (-0.2).sp),
    bodyLarge = style(Inter, 17, 22, FontWeight.Normal, (-0.4).sp),
    bodyMedium = style(Inter, 15, 20, FontWeight.Normal, (-0.2).sp),
    bodySmall = style(Inter, 13, 18, FontWeight.Normal, (-0.05).sp),
    labelLarge = style(Inter, 17, 22, FontWeight.SemiBold, (-0.4).sp),
    labelMedium = style(Inter, 13, 16, FontWeight.Medium, (-0.05).sp),
    labelSmall = style(Inter, 11, 13, FontWeight.Medium, 0.05.sp),
)

/** Same-width digits, so numbers don't jiggle as they change and line up in lists. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val apple = if (dark) DarkApple else LightApple
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalAppleColors provides apple) {
        val scheme = if (dark) darkScheme(apple) else lightScheme(apple)
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            // The pages are drawn on plain boxes rather than Material surfaces, so set the
            // default text and icon colour here; otherwise it stays black, even in dark mode.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
