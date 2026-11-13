package com.videodl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF5F5F4),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFB8B8B5),
    onSecondary = Color(0xFF151515),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFF4F4F2),
    surface = Color(0xFF151517),
    onSurface = Color(0xFFF4F4F2),
    surfaceVariant = Color(0xFF222225),
    onSurfaceVariant = Color(0xFFB5B5B2),
    outline = Color(0xFF414144),
    error = Color(0xFFFF6B68),
    errorContainer = Color(0xFF3A2020),
    onErrorContainer = Color(0xFFFFDAD8)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF151515),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF555552),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F2),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFECECE8),
    onSurfaceVariant = Color(0xFF686863),
    outline = Color(0xFFD7D7D1),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFE8E6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun VideoDLTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
