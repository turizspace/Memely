package com.memely.ui.theme

import android.content.Context
import android.content.SharedPreferences

typealias ThemePreference = String

/**
 * Manages theme preferences (Light/Dark mode)
 */
object ThemeManager {
    private const val PREFS_NAME = "memely_theme_prefs"
    private const val KEY_THEME = "theme_mode"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    
    private lateinit var prefs: SharedPreferences
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getThemePreference(context: Context): ThemePreference {
        if (!::prefs.isInitialized) {
            initialize(context)
        }
        return prefs.getString(KEY_THEME, THEME_LIGHT) ?: THEME_LIGHT
    }
    
    fun setThemePreference(context: Context, theme: ThemePreference) {
        if (!::prefs.isInitialized) {
            initialize(context)
        }
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    
    fun saveThemePreference(context: Context, theme: ThemePreference) {
        setThemePreference(context, theme)
    }
    
    fun toggleTheme(context: Context): ThemePreference {
        val currentTheme = getThemePreference(context)
        val newTheme = if (currentTheme == THEME_LIGHT) THEME_DARK else THEME_LIGHT
        setThemePreference(context, newTheme)
        return newTheme
    }
    
    fun isLightTheme(theme: ThemePreference): Boolean = theme == THEME_LIGHT
    fun isDarkTheme(theme: ThemePreference): Boolean = theme == THEME_DARK
}

fun isDarkTheme(theme: ThemePreference): Boolean = ThemeManager.isDarkTheme(theme)
fun isLightTheme(theme: ThemePreference): Boolean = ThemeManager.isLightTheme(theme)
