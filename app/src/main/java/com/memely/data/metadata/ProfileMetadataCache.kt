package com.memely.data.metadata

import com.memely.nostr.MetadataParser
import com.memely.util.SecureLog
import com.memely.util.SecureStorage
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent profile metadata cache that survives app restarts.
 * 
 * Features:
 * - In-memory cache for fast access
 * - Encrypted disk persistence using SecureStorage
 * - Automatic save/load on app lifecycle
 * - Timestamp-based freshness validation
 */
object ProfileMetadataCache {
    private val memoryCache = ConcurrentHashMap<String, CachedMetadata>()
    
    data class CachedMetadata(
        val pubkey: String,
        val metadata: MetadataParser.UserMetadata?,
        val relays: List<String>,
        val createdAt: Long,           // Metadata's Nostr created_at
        val cachedAt: Long,            // When cached locally
        val ttl: Long = 0              // 0 = never expire by time (only by newer metadata)
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("pubkey", pubkey)
                put("name", metadata?.name ?: "")
                put("about", metadata?.about ?: "")
                put("picture", metadata?.picture ?: "")
                put("nip05", metadata?.nip05 ?: "")
                put("lud16", metadata?.lud16 ?: "")
                put("banner", metadata?.banner ?: "")
                put("website", metadata?.website ?: "")
                put("createdAt", createdAt)
                put("cachedAt", cachedAt)
                put("relays", org.json.JSONArray(relays))
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String, pubkey: String): CachedMetadata? {
                return try {
                    val obj = JSONObject(json)
                    CachedMetadata(
                        pubkey = pubkey,
                        metadata = MetadataParser.UserMetadata(
                            name = obj.optString("name", null),
                            about = obj.optString("about", null),
                            picture = obj.optString("picture", null),
                            nip05 = obj.optString("nip05", null),
                            lud16 = obj.optString("lud16", null),
                            banner = obj.optString("banner", null),
                            website = obj.optString("website", null),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis() / 1000)
                        ),
                        relays = try {
                            val arr = obj.optJSONArray("relays")
                            if (arr != null) {
                                (0 until arr.length()).map { arr.getString(it) }
                            } else emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis() / 1000),
                        cachedAt = obj.optLong("cachedAt", System.currentTimeMillis())
                    )
                } catch (e: Exception) {
                    SecureLog.w("ProfileMetadataCache: Failed to deserialize profile for $pubkey: ${e.message}")
                    null
                }
            }
        }
    }
    
    private const val CACHE_KEY_PREFIX = "profile_metadata_"
    
    /**
     * Load all profiles from persistent storage
     * Called on app startup
     */
    fun loadFromDisk() {
        try {
            SecureLog.d("ProfileMetadataCache: Loading profiles from disk...")
            val storage = SecureStorage.getInstance()
            
            // Get all stored profile keys (we'll search for keys starting with prefix)
            // Since SharedPreferences doesn't have a getAll that's easily enumerable,
            // we'll use a manifest file approach or store a list of pubkeys
            val pubkeyListKey = "${CACHE_KEY_PREFIX}pubkeys_list"
            val pubkeyListJson = storage.getString(pubkeyListKey)
            
            if (!pubkeyListJson.isNullOrEmpty()) {
                try {
                    val pubkeys = pubkeyListJson.split(",").filter { it.isNotEmpty() }
                    var loadedCount = 0
                    
                    for (pubkey in pubkeys) {
                        val cacheKey = "$CACHE_KEY_PREFIX$pubkey"
                        val json = storage.getString(cacheKey)
                        
                        if (!json.isNullOrEmpty()) {
                            val cached = CachedMetadata.fromJson(json, pubkey)
                            if (cached != null) {
                                memoryCache[pubkey] = cached
                                loadedCount++
                            }
                        }
                    }
                    
                    SecureLog.d("ProfileMetadataCache: Loaded $loadedCount profiles from disk")
                } catch (e: Exception) {
                    SecureLog.w("ProfileMetadataCache: Error loading pubkey list: ${e.message}")
                }
            } else {
                SecureLog.d("ProfileMetadataCache: No profiles stored on disk (first launch)")
            }
        } catch (e: Exception) {
            SecureLog.e("ProfileMetadataCache: Error loading from disk: ${e.message}")
        }
    }
    
    /**
     * Cache a profile and persist to disk
     */
    fun cache(pubkey: String, metadata: MetadataParser.UserMetadata?, relays: List<String>) {
        try {
            val cached = CachedMetadata(
                pubkey = pubkey,
                metadata = metadata,
                relays = relays,
                createdAt = metadata?.createdAt ?: System.currentTimeMillis() / 1000,
                cachedAt = System.currentTimeMillis()
            )
            
            // Store in memory
            memoryCache[pubkey] = cached
            
            // Persist to disk
            val storage = SecureStorage.getInstance()
            val cacheKey = "$CACHE_KEY_PREFIX$pubkey"
            val json = cached.toJson()
            storage.putString(cacheKey, json)
            
            // Update pubkey list
            updatePubkeyList(pubkey)
            
            SecureLog.d("ProfileMetadataCache: Cached and persisted profile for ${pubkey.take(8)}")
        } catch (e: Exception) {
            SecureLog.e("ProfileMetadataCache: Error caching profile: ${e.message}")
        }
    }
    
    /**
     * Get cached profile from memory or disk
     */
    fun get(pubkey: String): CachedMetadata? {
        return memoryCache[pubkey]
    }
    
    /**
     * Remove cached profile
     */
    fun remove(pubkey: String) {
        try {
            memoryCache.remove(pubkey)
            
            val storage = SecureStorage.getInstance()
            val cacheKey = "$CACHE_KEY_PREFIX$pubkey"
            storage.remove(cacheKey)
            
            // Update pubkey list
            removePubkeyFromList(pubkey)
            
            SecureLog.d("ProfileMetadataCache: Removed cached profile for ${pubkey.take(8)}")
        } catch (e: Exception) {
            SecureLog.e("ProfileMetadataCache: Error removing profile: ${e.message}")
        }
    }
    
    /**
     * Clear all cached profiles
     */
    fun clear() {
        try {
            val storage = SecureStorage.getInstance()
            
            // Remove all cached profiles
            memoryCache.keys.forEach { pubkey ->
                val cacheKey = "$CACHE_KEY_PREFIX$pubkey"
                storage.remove(cacheKey)
            }
            
            // Clear memory cache and pubkey list
            memoryCache.clear()
            storage.remove("${CACHE_KEY_PREFIX}pubkeys_list")
            
            SecureLog.d("ProfileMetadataCache: Cleared all cached profiles")
        } catch (e: Exception) {
            SecureLog.e("ProfileMetadataCache: Error clearing cache: ${e.message}")
        }
    }
    
    /**
     * Get memory cache size
     */
    fun size(): Int = memoryCache.size
    
    /**
     * Get all cached pubkeys
     */
    fun getAllCachedPubkeys(): List<String> = memoryCache.keys.toList()
    
    private fun updatePubkeyList(pubkey: String) {
        try {
            val storage = SecureStorage.getInstance()
            val pubkeyListKey = "${CACHE_KEY_PREFIX}pubkeys_list"
            val currentListJson = storage.getString(pubkeyListKey) ?: ""
            val currentList = if (currentListJson.isEmpty()) emptyList() else currentListJson.split(",")
            
            if (!currentList.contains(pubkey)) {
                val newList = (currentList + pubkey).joinToString(",")
                storage.putString(pubkeyListKey, newList)
            }
        } catch (e: Exception) {
            SecureLog.w("ProfileMetadataCache: Error updating pubkey list: ${e.message}")
        }
    }
    
    private fun removePubkeyFromList(pubkey: String) {
        try {
            val storage = SecureStorage.getInstance()
            val pubkeyListKey = "${CACHE_KEY_PREFIX}pubkeys_list"
            val currentListJson = storage.getString(pubkeyListKey) ?: ""
            val currentList = if (currentListJson.isEmpty()) emptyList() else currentListJson.split(",")
            
            val newList = (currentList - pubkey).joinToString(",")
            storage.putString(pubkeyListKey, newList)
        } catch (e: Exception) {
            SecureLog.w("ProfileMetadataCache: Error removing from pubkey list: ${e.message}")
        }
    }
}
