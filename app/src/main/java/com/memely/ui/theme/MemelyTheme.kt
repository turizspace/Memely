package com.memely.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Theme Colors
val LightMemelyColors = lightColors(
    primary = Color(0xFF6366F1),
    primaryVariant = Color(0xFF4F46E5),
    secondary = Color(0xFFEC4899),
    secondaryVariant = Color(0xFFDB2777),
    background = Color(0xFFF9FAFB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1F2937),
    onSurface = Color(0xFF1F2937),
    error = Color(0xFFEF4444)
)

// Dark Theme Colors
val DarkMemelyColors = darkColors(
    primary = Color(0xFF818CF8),
    primaryVariant = Color(0xFF6366F1),
    secondary = Color(0xFFF472B6),
    secondaryVariant = Color(0xFFEC4899),
    background = Color(0xFF111827),
    surface = Color(0xFF1F2937),
    onPrimary = Color(0xFF111827),
    onSecondary = Color(0xFF111827),
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    error = Color(0xFFFCA5A5)
)

@Composable
fun MemelyTheme(
    isDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkMode) DarkMemelyColors else LightMemelyColors
    
    MaterialTheme(
        colors = colors,
        content = content
    )
}