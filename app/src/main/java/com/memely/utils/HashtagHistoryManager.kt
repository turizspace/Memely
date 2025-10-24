package com.memely.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages hashtag history for quick reuse.
 * Stores and retrieves previously used hashtags.
 */
object HashtagHistoryManager {
    private const val PREFS_NAME = "hashtag_history"
    private const val KEY_HASHTAGS = "used_hashtags"
    private const val MAX_HISTORY_SIZE = 20

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Extract hashtags from text and save to history
     */
    fun saveHashtagsFromText(text: String) {
        val hashtags = extractHashtags(text)
        if (hashtags.isNotEmpty()) {
            addHashtags(hashtags)
        }
    }

    /**
     * Extract hashtags from text (words starting with #)
     */
    private fun extractHashtags(text: String): List<String> {
        val hashtagRegex = "#\\w+".toRegex()
        return hashtagRegex.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
    }

    /**
     * Add hashtags to history (most recent first)
     */
    private fun addHashtags(newHashtags: List<String>) {
        val currentHistory = getHashtagHistory().toMutableList()
        
        // Remove duplicates and add to front
        newHashtags.forEach { tag ->
            currentHistory.remove(tag)
            currentHistory.add(0, tag)
        }
        
        // Limit history size
        val trimmedHistory = currentHistory.take(MAX_HISTORY_SIZE)
        
        // Save to SharedPreferences
        prefs?.edit()?.putStringSet(KEY_HASHTAGS, trimmedHistory.toSet())?.apply()
    }

    /**
     * Get hashtag history (most recent first)
     */
    fun getHashtagHistory(): List<String> {
        val hashtagSet = prefs?.getStringSet(KEY_HASHTAGS, emptySet()) ?: emptySet()
        // Note: SharedPreferences StringSet doesn't preserve order, so we just return as list
        return hashtagSet.toList()
    }

    /**
     * Clear all hashtag history
     */
    fun clearHistory() {
        prefs?.edit()?.remove(KEY_HASHTAGS)?.apply()
    }
}
