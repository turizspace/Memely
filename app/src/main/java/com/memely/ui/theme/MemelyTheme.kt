package com.memely.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MemelyColors = lightColors(
    primary = Color(0xFF6366F1),
    primaryVariant = Color(0xFF4F46E5),
    secondary = Color(0xFFEC4899),
    secondaryVariant = Color(0xFFDB2777),
    background = Color(0xFFF9FAFB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1F2937),
    onSurface = Color(0xFF1F2937)
)

@Composable
fun MemelyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MemelyColors,
        content = content
    )
}