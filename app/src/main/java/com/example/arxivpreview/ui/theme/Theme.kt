package com.example.arxivpreview.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.arxivpreview.data.ThemeMode

val LocalDarkAppearance = staticCompositionLocalOf { false }
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF), onPrimaryContainer = Color(0xFF0055B3),
    secondary = Color(0xFF6E6E73), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F2FF), onSecondaryContainer = Color(0xFF0055B3),
    tertiary = Color(0xFF248A3D), error = Color(0xFFD70015),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
    surface = Color.White, onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF636366),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F7), surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC7C7CC), outlineVariant = Color(0xFFE5E5EA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF102C4C), onPrimaryContainer = Color(0xFFB6D8FF),
    secondary = Color(0xFFAEAEB2),
    secondaryContainer = Color(0xFF102C4C), onSecondaryContainer = Color(0xFFB6D8FF),
    tertiary = Color(0xFF30D158), error = Color(0xFFFF453A),
    background = Color(0xFF000000), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF38383A), onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF38383A),
    outline = Color(0xFF636366), outlineVariant = Color(0xFF38383A),
)
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
    headlineMedium = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontFamily = FontFamily.SansSerif),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ArxivPreviewTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDarkAppearance provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
            content = content,
        )
    }
}
