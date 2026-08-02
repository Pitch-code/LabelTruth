package com.labeltruth.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Brand palette. Green reads as health and trust; red is reserved exclusively
// for genuine risk so that it keeps its meaning.
val BrandGreenDeep = Color(0xFF0E7C5A)
val BrandGreen = Color(0xFF16B981)
val BrandGreenLight = Color(0xFF6EE7B7)

/**
 * Dark surface ladder.
 *
 * Previously `background` and `surface` were the same colour and
 * `surfaceVariant` sat only six points above them, so cards, sheets and chips
 * had almost nothing separating them from the page. On a phone the whole screen
 * read as one flat slab, and an unselected chip was very nearly invisible.
 *
 * These are now distinct, evenly spaced steps. Each one is a perceptible lift on
 * the one below, which is what makes a dark interface look deliberate rather
 * than washed out, and it lets a card sit *on* the page instead of dissolving
 * into it.
 */
val SurfaceDark = Color(0xFF070F0D)
val SurfaceDarkLow = Color(0xFF0E1917)
val SurfaceDarkElevated = Color(0xFF16241F)
val SurfaceDarkHigh = Color(0xFF1C2C27)
val SurfaceDarkHighest = Color(0xFF24352F)
val OutlineDark = Color(0xFF3A554E)
val OutlineDarkVariant = Color(0xFF24352F)

// Risk scale. Deliberately distinguishable by lightness as well as hue, so it
// still works for colour-blind users.
val RiskSafe = Color(0xFF16B981)
val RiskCaution = Color(0xFFA3C644)
val RiskModerate = Color(0xFFF5A524)
val RiskAvoid = Color(0xFFEF4444)
val RiskUnknown = Color(0xFF8A9A94)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF04231A),
    primaryContainer = BrandGreenDeep,
    onPrimaryContainer = Color(0xFFD7F5E8),
    secondary = BrandGreenLight,
    onSecondary = Color(0xFF00281C),
    secondaryContainer = Color(0xFF1B3A30),
    onSecondaryContainer = Color(0xFFBFEFDA),
    background = SurfaceDark,
    onBackground = Color(0xFFE9F3EF),
    surface = SurfaceDarkLow,
    onSurface = Color(0xFFE9F3EF),
    surfaceContainerLowest = Color(0xFF050B0A),
    surfaceContainerLow = SurfaceDarkLow,
    surfaceContainer = SurfaceDarkElevated,
    surfaceContainerHigh = SurfaceDarkHigh,
    surfaceContainerHighest = SurfaceDarkHighest,
    surfaceVariant = SurfaceDarkHigh,
    // Lifted from #B4C6C0 so secondary text stays legible against the darker
    // background rather than fading into it.
    onSurfaceVariant = Color(0xFFA9BFB8),
    outline = OutlineDark,
    outlineVariant = OutlineDarkVariant,
    error = RiskAvoid,
    onError = Color(0xFF2B0000)
)

private val LightColors = lightColorScheme(
    primary = BrandGreenDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5E8),
    onPrimaryContainer = Color(0xFF00281C),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF3E3),
    onSecondaryContainer = Color(0xFF04231A),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF10201B),
    surface = Color.White,
    onSurface = Color(0xFF10201B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFCFB),
    surfaceContainer = Color(0xFFEFF5F2),
    surfaceContainerHigh = Color(0xFFE7EFEB),
    surfaceContainerHighest = Color(0xFFDDE8E3),
    surfaceVariant = Color(0xFFE7EFEB),
    onSurfaceVariant = Color(0xFF3D4B46),
    outline = Color(0xFFA9BAB3),
    outlineVariant = Color(0xFFD3DFDA),
    error = Color(0xFFC1272D),
    onError = Color.White
)

/**
 * Type scale.
 *
 * Weights and sizes carry the hierarchy rather than colour alone, because on a
 * dark screen tinting text is a weak signal: it mostly just makes it harder to
 * read. Negative tracking on the large sizes keeps headings from looking loose,
 * and generous line heights keep the ingredient prose readable, which is most
 * of what this app asks people to do.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun LabelTruthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current

    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
