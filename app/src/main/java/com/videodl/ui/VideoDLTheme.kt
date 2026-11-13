package com.videodl.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF101010),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF101010),
    background = Color(0xFF090909),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFC8C8C8),
    outline = Color(0xFF505050)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF555555),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFFB8B8B8)
)

@Composable
fun VideoDLTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
