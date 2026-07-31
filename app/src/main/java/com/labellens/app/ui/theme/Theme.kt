package com.labellens.app.ui.theme

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

val SurfaceDark = Color(0xFF0B1412)
val SurfaceDarkElevated = Color(0xFF14201D)
val OutlineDark = Color(0xFF294039)

// Risk scale. Deliberately distinguishable by lightness as well as hue, so it
// still works for colour-blind users.
val RiskSafe = Color(0xFF16B981)
val RiskCaution = Color(0xFFA3C644)
val RiskModerate = Color(0xFFF5A524)
val RiskAvoid = Color(0xFFEF4444)
val RiskUnknown = Color(0xFF8A9A94)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF00281C),
    primaryContainer = BrandGreenDeep,
    onPrimaryContainer = Color(0xFFD7F5E8),
    secondary = BrandGreenLight,
    onSecondary = Color(0xFF00281C),
    background = SurfaceDark,
    onBackground = Color(0xFFE6F0EC),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6F0EC),
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Color(0xFFB4C6C0),
    outline = OutlineDark,
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
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF10201B),
    surface = Color.White,
    onSurface = Color(0xFF10201B),
    surfaceVariant = Color(0xFFE7EFEB),
    onSurfaceVariant = Color(0xFF43514C),
    outline = Color(0xFFC2CFC9),
    error = Color(0xFFC1272D),
    onError = Color.White
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
)

@Composable
fun LabelLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current

    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
