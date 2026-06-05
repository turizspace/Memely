package com.memely.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.memely.util.SecureLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe metadata cache with atomic operations and timestamp tracking.
 * Ensures only the latest metadata (highest created_at) is stored.
 * Optimized for poor connections with batch requests and deduplication.
 */
object UserMetadataCache {
    // Use ConcurrentHashMap for thread-safe operations
    private val _cachedMetadata = ConcurrentHashMap<String, MetadataParser.UserMetadata>()
    private val _loadingStates = ConcurrentHashMap<String, Boolean>()
    private val _lastFetchAttempt = ConcurrentHashMap<String, Long>() // Debounce requests
    private val _pendingRequests = mutableSetOf<String>() // Batch requests for poor connections
    private val _batchingTimer = AtomicBoolean(false)
    private val _DEBOUNCE_MS = 3000L // Don't fetch same pubkey more than once per 3 seconds
    private val _BATCH_WINDOW_MS = 500L // Collect requests for 500ms before sending batch
    
    // StateFlow for observing cache updates - triggers recompositions when metadata is cached
    private val _cacheUpdateFlow = MutableStateFlow<Pair<String, MetadataParser.UserMetadata>?>(null)
    val cacheUpdateFlow: StateFlow<Pair<String, MetadataParser.UserMetadata>?> = _cacheUpdateFlow
    
    // Separate scope for background fetches that won't be cancelled
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    suspend fun getMetadata(pubkey: String): MetadataParser.UserMetadata? {
        // Return cached metadata if available
        _cachedMetadata[pubkey]?.let { 
            SecureLog.d("UserMetadataCache: Cache hit for ${pubkey.take(8)}")
            return it 
        }
        
        // Prevent duplicate requests
        if (_loadingStates[pubkey] == true) {
            SecureLog.d("UserMetadataCache: Already loading metadata for ${pubkey.take(8)}")
            return null
        }
        
        _loadingStates[pubkey] = true
        try {
            SecureLog.d("UserMetadataCache: Fetching metadata for ${pubkey.take(8)}...")
            val metadata = NostrRepository.fetchProfileMetadata(pubkey)
            if (metadata != null) {
                // Only cache if it's newer than what we have
                updateMetadataIfNewer(pubkey, metadata)
                SecureLog.d("UserMetadataCache: Cached metadata for ${pubkey.take(8)} - ${metadata.name}")
            } else {
                SecureLog.w("UserMetadataCache: No metadata found for ${pubkey.take(8)}")
                // Cache anonymous user to prevent repeated failed requests
                val anonymous = MetadataParser.UserMetadata(
                    name = "Anonymous",
                    about = null,
                    picture = null,
                    nip05 = null,
                    createdAt = System.currentTimeMillis() / 1000 // Use current time as fallback
                )
                _cachedMetadata[pubkey] = anonymous
            }
            return metadata
        } finally {
            _loadingStates[pubkey] = false
        }
    }
    
    fun getCachedMetadata(pubkey: String): MetadataParser.UserMetadata? {
        return _cachedMetadata[pubkey]
    }
    
    /**
     * Request metadata with debouncing for poor connections.
     * Rapid requests for the same pubkey within DEBOUNCE_MS are deduplicated.
     */
    fun requestMetadataAsync(pubkey: String) {
        // Check debounce: don't fetch if we tried recently
        val lastAttempt = _lastFetchAttempt[pubkey] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastAttempt < _DEBOUNCE_MS) {
            SecureLog.d("UserMetadataCache: Debouncing request for ${pubkey.take(8)} (requested ${now - lastAttempt}ms ago)")
            return
        }
        
        // If already cached with real metadata, skip entirely
        val cached = _cachedMetadata[pubkey]
        if (cached != null && cached.name != "Anonymous") {
            SecureLog.d("UserMetadataCache: Already have real metadata for ${pubkey.take(8)}, skipping fetch")
            return
        }
        
        // If currently loading, skip
        if (_loadingStates[pubkey] == true) {
            SecureLog.d("UserMetadataCache: Already loading for ${pubkey.take(8)}, skipping")
            return
        }
        
        // Add to pending batch
        _lastFetchAttempt[pubkey] = now
        synchronized(_pendingRequests) {
            _pendingRequests.add(pubkey)
        }
        
        // Start batch timer if not running
        if (_batchingTimer.compareAndSet(false, true)) {
            backgroundScope.launch {
                try {
                    kotlinx.coroutines.delay(_BATCH_WINDOW_MS)
                    
                    val requestsToFetch = synchronized(_pendingRequests) {
                        val batch = _pendingRequests.toList()
                        _pendingRequests.clear()
                        batch
                    }
                    
                    // Fetch all pending requests
                    for (key in requestsToFetch) {
                        try {
                            if (!_loadingStates.getOrDefault(key, false)) {
                                _loadingStates[key] = true
                                SecureLog.d("UserMetadataCache: Background fetch for ${key.take(8)}...")
                                val metadata = NostrRepository.fetchProfileMetadata(key)
                                if (metadata != null && metadata.name != "Anonymous") {
                                    updateMetadataIfNewer(key, metadata)
                                    SecureLog.d("UserMetadataCache: Background cached metadata for ${key.take(8)} - ${metadata.name}")
                                } else {
                                    SecureLog.w("UserMetadataCache: No real metadata found for ${key.take(8)}, not caching Anonymous")
                                }
                            }
                        } catch (e: Exception) {
                            SecureLog.e("UserMetadataCache: Background fetch error for ${key.take(8)}: ${e.message}")
                        } finally {
                            _loadingStates[key] = false
                        }
                    }
                } finally {
                    _batchingTimer.set(false)
                }
            }
        }
    }
    
    /**
     * Atomically cache metadata if it's newer than what we have.
     * This ensures only the latest metadata (by created_at) is stored.
     */
    fun updateMetadataIfNewer(pubkey: String, newMetadata: MetadataParser.UserMetadata) {
        val current = _cachedMetadata[pubkey]
        
        // Always cache if we don't have anything yet
        if (current == null) {
            _cachedMetadata[pubkey] = newMetadata
            _cacheUpdateFlow.value = Pair(pubkey, newMetadata)
            SecureLog.d("UserMetadataCache: Cached new metadata for ${pubkey.take(8)} (no previous version)")
            return
        }
        
        // Only update if new metadata is more recent (higher created_at)
        if (newMetadata.createdAt > current.createdAt) {
            _cachedMetadata[pubkey] = newMetadata
            _cacheUpdateFlow.value = Pair(pubkey, newMetadata)
            SecureLog.d("UserMetadataCache: Updated metadata for ${pubkey.take(8)} (newer: ${newMetadata.createdAt} vs ${current.createdAt})")
        } else {
            SecureLog.d("UserMetadataCache: Keeping existing metadata for ${pubkey.take(8)} (newer: ${current.createdAt} vs ${newMetadata.createdAt})")
        }
    }
    
    /**
     * Atomic metadata cache operation called from NostrRepository when metadata is received.
     * Ensures only the latest version is cached.
     */
    fun cacheMetadataIfNewer(pubkey: String, metadata: MetadataParser.UserMetadata) {
        updateMetadataIfNewer(pubkey, metadata)
        SecureLog.d("UserMetadataCache: Manually cached metadata for ${pubkey.take(8)} - ${metadata.name}")
    }
    
    fun clearCache() {
        _cachedMetadata.clear()
        _loadingStates.clear()
        _lastFetchAttempt.clear()
        SecureLog.d("UserMetadataCache: Cleared all cached metadata")
    }
    
    fun getCacheStats(): String {
        return "Cached: ${_cachedMetadata.size}, Loading: ${_loadingStates.count { it.value }}, Pending: ${_pendingRequests.size}"
    }
}