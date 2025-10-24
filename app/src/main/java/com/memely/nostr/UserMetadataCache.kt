package com.memely.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object UserMetadataCache {
    private val _cachedMetadata = mutableMapOf<String, MetadataParser.UserMetadata>()
    private val _loadingStates = mutableMapOf<String, Boolean>()
    
    // StateFlow for observing cache updates - triggers recompositions when metadata is cached
    private val _cacheUpdateFlow = MutableStateFlow<Pair<String, MetadataParser.UserMetadata>?>(null)
    val cacheUpdateFlow: StateFlow<Pair<String, MetadataParser.UserMetadata>?> = _cacheUpdateFlow
    
    // Separate scope for background fetches that won't be cancelled
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    suspend fun getMetadata(pubkey: String): MetadataParser.UserMetadata? {
        // Return cached metadata if available
        _cachedMetadata[pubkey]?.let { 
            println("💾 UserMetadataCache: Cache hit for ${pubkey.take(8)}")
            return it 
        }
        
        // Prevent duplicate requests
        if (_loadingStates[pubkey] == true) {
            println("⏭️ UserMetadataCache: Already loading metadata for ${pubkey.take(8)}")
            return null
        }
        
        _loadingStates[pubkey] = true
        try {
            println("🔍 UserMetadataCache: Fetching metadata for ${pubkey.take(8)}...")
            val metadata = NostrRepository.fetchProfileMetadata(pubkey)
            if (metadata != null) {
                _cachedMetadata[pubkey] = metadata
                println("✅ UserMetadataCache: Cached metadata for ${pubkey.take(8)} - ${metadata.name}")
            } else {
                println("❌ UserMetadataCache: No metadata found for ${pubkey.take(8)}")
                // Cache anonymous user to prevent repeated failed requests
                _cachedMetadata[pubkey] = MetadataParser.UserMetadata(
                    name = "Anonymous",
                    about = null,
                    picture = null,
                    nip05 = null
                )
            }
            return metadata
        } finally {
            _loadingStates[pubkey] = false
        }
    }
    
    fun getCachedMetadata(pubkey: String): MetadataParser.UserMetadata? {
        return _cachedMetadata[pubkey]
    }
    
    fun requestMetadataAsync(pubkey: String) {
        // If already cached, skip entirely - NEVER overwrite real metadata with Anonymous
        val cached = _cachedMetadata[pubkey]
        if (cached != null) {
            // Already have data, don't fetch again
            if (cached.name != "Anonymous") {
                println("💾 UserMetadataCache: Already have real metadata for ${pubkey.take(8)}, skipping fetch")
                return
            }
        }
        
        // If loading, skip
        if (_loadingStates[pubkey] == true) {
            println("⏭️ UserMetadataCache: Already loading for ${pubkey.take(8)}, skipping")
            return
        }
        
        // Launch background fetch in independent scope without blocking
        _loadingStates[pubkey] = true
        backgroundScope.launch {
            try {
                println("🔄 UserMetadataCache: Background fetch for ${pubkey.take(8)}...")
                val metadata = NostrRepository.fetchProfileMetadata(pubkey)
                if (metadata != null && metadata.name != "Anonymous") {
                    _cachedMetadata[pubkey] = metadata
                    _cacheUpdateFlow.value = Pair(pubkey, metadata)
                    println("✅ UserMetadataCache: Background cached metadata for ${pubkey.take(8)} - ${metadata.name}")
                } else {
                    println("⚠️ UserMetadataCache: No real metadata found for ${pubkey.take(8)}, not caching Anonymous")
                    // Don't cache Anonymous - leave it null so next fetch might try again
                }
            } catch (e: Exception) {
                println("❌ UserMetadataCache: Background fetch error: ${e.message}")
                // On error, DON'T cache Anonymous - just skip
                println("⚠️ UserMetadataCache: Skipping cache update on error to preserve existing data")
            } finally {
                _loadingStates[pubkey] = false
            }
        }
    }
    
    fun cacheMetadata(pubkey: String, metadata: MetadataParser.UserMetadata) {
        _cachedMetadata[pubkey] = metadata
        // Emit update so observing composables recompose
        _cacheUpdateFlow.value = Pair(pubkey, metadata)
        println("💾 UserMetadataCache: Manually cached metadata for ${pubkey.take(8)} - ${metadata.name}")
    }
    
    fun clearCache() {
        _cachedMetadata.clear()
        _loadingStates.clear()
        println("🗑️ UserMetadataCache: Cleared all cached metadata")
    }
    
    fun getCacheStats(): String {
        return "Cached: ${_cachedMetadata.size}, Loading: ${_loadingStates.count { it.value }}"
    }
}