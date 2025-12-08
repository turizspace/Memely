package com.memely.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages user's favorite templates with SharedPreferences persistence
 */
object FavoritesManager {
    private const val FAVORITES_PREFS = "memely_favorites"
    private const val FAVORITES_KEY = "favorite_templates"
    
    // Flow to track favorites changes for UI updates
    private val _favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
    val favoritesFlow: StateFlow<Set<String>> = _favoritesFlow
    
    /**
     * Initialize favorites from SharedPreferences
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        _favoritesFlow.value = prefs.getStringSet(FAVORITES_KEY, null) ?: emptySet()
        println("📋 FavoritesManager: Initialized with ${_favoritesFlow.value.size} favorites")
    }
    
    /**
     * Add a template URL to favorites
     */
    fun addFavorite(context: Context, templateUrl: String) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        // CRITICAL: Create a mutable copy of the set!
        val current = (prefs.getStringSet(FAVORITES_KEY, null) ?: emptySet()).toMutableSet()
        current.add(templateUrl)
        prefs.edit().putStringSet(FAVORITES_KEY, current).apply()
        
        // Update StateFlow to trigger UI refresh
        _favoritesFlow.value = current
        
        println("❤️ FavoritesManager: Added favorite - $templateUrl (Total: ${current.size})")
    }
    
    /**
     * Remove a template URL from favorites
     */
    fun removeFavorite(context: Context, templateUrl: String) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        // CRITICAL: Create a mutable copy of the set!
        val current = (prefs.getStringSet(FAVORITES_KEY, null) ?: emptySet()).toMutableSet()
        current.remove(templateUrl)
        prefs.edit().putStringSet(FAVORITES_KEY, current).apply()
        
        // Update StateFlow to trigger UI refresh
        _favoritesFlow.value = current
        
        println("🖤 FavoritesManager: Removed favorite - $templateUrl (Total: ${current.size})")
    }
    
    /**
     * Get all favorite template URLs
     */
    fun getFavorites(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet(FAVORITES_KEY, null) ?: emptySet()
        println("📊 FavoritesManager: Retrieved ${favorites.size} favorites")
        return favorites
    }
    
    /**
     * Check if a template URL is in favorites
     */
    fun isFavorite(context: Context, templateUrl: String): Boolean {
        return _favoritesFlow.value.contains(templateUrl)
    }
    
    /**
     * Get count of favorites
     */
    fun getFavoritesCount(context: Context): Int {
        val count = _favoritesFlow.value.size
        println("❤️ FavoritesManager: Total favorites count = $count")
        return count
    }
    
    /**
     * Clear all favorites
     */
    fun clearAllFavorites(context: Context) {
        val prefs = context.getSharedPreferences(FAVORITES_PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(FAVORITES_KEY).apply()
        _favoritesFlow.value = emptySet()
        println("🗑️ FavoritesManager: Cleared all favorites")
    }
}
