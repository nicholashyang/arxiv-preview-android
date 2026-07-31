package com.example.arxivpreview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C1D18),
    secondary = Color(0xFF755654),
    tertiary = Color(0xFF765B00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    secondary = Color(0xFFE7BDB9),
    tertiary = Color(0xFFE9C349),
)

@Composable
fun ArxivPreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
