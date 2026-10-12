package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IndustrialBlue,
    onPrimary = Color.White,
    primaryContainer = IndustrialBlueDark,
    secondary = IndustrialCyan,
    onSecondary = Color.Black,
    error = FailRed,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFE2E4E9),
    onSurface = Color(0xFFE2E4E9),
    onSurfaceVariant = Color(0xFFC0C5CF)
)

private val LightColorScheme = lightColorScheme(
    primary = IndustrialBlue,
    onPrimary = Color.White,
    secondary = IndustrialCyan,
    onSecondary = Color.White,
    error = FailRed,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color(0xFF191C20),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E)
)

@Composable
fun ChipVisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
