package com.memely.ui.components

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.memely.ui.theme.ThemeManager
import com.memely.ui.theme.ThemePreference

@Composable
fun ThemeToggleButton(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    val isDarkMode = ThemeManager.isDarkTheme(currentTheme)
    
    IconButton(
        onClick = {
            val newTheme = if (isDarkMode) {
                ThemeManager.THEME_LIGHT
            } else {
                ThemeManager.THEME_DARK
            }
            onThemeChange(newTheme)
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
            tint = tint
        )
    }
}
