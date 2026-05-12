package com.memely.data.repositories

import android.content.Context
import com.memely.ui.theme.ThemeManager
import com.memely.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val _theme = MutableStateFlow(ThemeManager.getThemePreference(appContext))
    val theme: StateFlow<ThemePreference> = _theme.asStateFlow()

    fun setTheme(theme: ThemePreference) {
        ThemeManager.saveThemePreference(appContext, theme)
        _theme.value = theme
    }
}
