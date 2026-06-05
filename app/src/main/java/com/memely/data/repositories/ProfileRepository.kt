package com.memely.data.repositories

import com.memely.nostr.MetadataParser
import com.memely.nostr.NostrRepository
import com.memely.nostr.RelayManager
import com.memely.util.SecureLog
import com.memely.data.metadata.ProfileMetadataCache
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

interface ProfileRepository {
    val connectedRelays: StateFlow<Int>
    val effectiveRelays: StateFlow<List<String>>
    val userMetadata: StateFlow<MetadataParser.UserMetadata?>

    suspend fun connectAll()

    fun startProfileListener(pubkey: String)

    suspend fun fetchUserMetadata(pubkey: String): MetadataParser.UserMetadata?

    suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>>
    
    fun clearProfileCache(pubkey: String)
    
    fun clearAllProfileCache()
    
    suspend fun refreshUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>>
}

class DefaultProfileRepository : ProfileRepository {
    override val connectedRelays: StateFlow<Int> = NostrRepository.connectedRelaysFlow
    override val effectiveRelays: StateFlow<List<String>> = RelayManager.effectiveRelays
    override val userMetadata: StateFlow<MetadataParser.UserMetadata?> = NostrRepository.metadataState

    // In-memory profile cache - backed by persistent disk storage
    private val profileCache = ConcurrentHashMap<String, CachedProfile>()
    private val cacheLock = Mutex()
    
    // Track profiles being fetched to avoid duplicate concurrent fetches
    private val fetchingProfiles = ConcurrentHashMap<String, Boolean>()
    
    data class CachedProfile(
        val metadata: MetadataParser.UserMetadata?,
        val relays: List<String>,
        val cachedAt: Long = System.currentTimeMillis(),
        val createdAtTimestamp: Long = metadata?.createdAt ?: 0L // Track metadata's creation timestamp
    ) {
        /**
         * Check if incoming metadata is newer than cached data
         * Uses Nostr event's created_at timestamp for comparison
         */
        fun isNewerMetadataAvailable(newMetadata: MetadataParser.UserMetadata?): Boolean {
            if (newMetadata == null) return false
            if (metadata == null) return true
            return newMetadata.createdAt > createdAtTimestamp
        }
    }
    
    init {
        // Load cached profiles from disk on initialization
        SecureLog.d("DefaultProfileRepository: Initializing with disk cache")
        ProfileMetadataCache.loadFromDisk()
    }

    override suspend fun connectAll() {
        NostrRepository.connectAll()
    }

    override fun startProfileListener(pubkey: String) {
        NostrRepository.startProfileListener(pubkey)
    }

    override suspend fun fetchUserMetadata(pubkey: String): MetadataParser.UserMetadata? {
        return NostrRepository.fetchSignedInUserMetadata(pubkey)
    }

    override suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>> {
        // Check memory cache first
        val cached = profileCache[pubkey]
        if (cached != null) {
            SecureLog.d("ProfileRepository: Using cached profile for ${pubkey.take(8)} (cached ${System.currentTimeMillis() - cached.cachedAt}ms ago)")
            return cached.metadata to cached.relays
        }
        
        // Check disk cache if not in memory
        val diskCached = ProfileMetadataCache.get(pubkey)
        if (diskCached != null) {
            SecureLog.d("ProfileRepository: Loading profile for ${pubkey.take(8)} from disk cache")
            val profile = CachedProfile(
                metadata = diskCached.metadata,
                relays = diskCached.relays,
                createdAtTimestamp = diskCached.createdAt
            )
            profileCache[pubkey] = profile
            return diskCached.metadata to diskCached.relays
        }
        
        // Prevent duplicate concurrent fetches for same pubkey
        cacheLock.withLock {
            if (fetchingProfiles[pubkey] == true) {
                SecureLog.d("ProfileRepository: Profile already being fetched for ${pubkey.take(8)}, waiting for result...")
                // Wait and return from cache if it was populated while we were locked
                val result = profileCache[pubkey]
                if (result != null) {
                    return result.metadata to result.relays
                }
            }
            fetchingProfiles[pubkey] = true
        }
        
        return try {
            // Fetch fresh data
            SecureLog.d("ProfileRepository: Fetching fresh profile for ${pubkey.take(8)}")
            val result = NostrRepository.fetchUserProfile(pubkey)
            
            // Cache the result with metadata's created_at timestamp
            val newCreatedAt = result.first?.createdAt ?: 0L
            cacheLock.withLock {
                val cached = profileCache[pubkey]
                if (cached == null || newCreatedAt > cached.createdAtTimestamp) {
                    val profile = CachedProfile(
                        metadata = result.first,
                        relays = result.second,
                        createdAtTimestamp = newCreatedAt
                    )
                    profileCache[pubkey] = profile
                    
                    // Also persist to disk
                    ProfileMetadataCache.cache(pubkey, result.first, result.second)
                    
                    SecureLog.d("ProfileRepository: Cached fresh profile for ${pubkey.take(8)}")
                } else {
                    SecureLog.d("ProfileRepository: Existing cached profile is newer, keeping it")
                }
            }
            
            result
        } finally {
            fetchingProfiles[pubkey] = false
        }
    }
    
    override fun clearProfileCache(pubkey: String) {
        profileCache.remove(pubkey)
        ProfileMetadataCache.remove(pubkey)
        SecureLog.d("ProfileRepository: Cleared cache for ${pubkey.take(8)}")
    }
    
    override fun clearAllProfileCache() {
        profileCache.clear()
        ProfileMetadataCache.clear()
        SecureLog.d("ProfileRepository: Cleared all profile cache")
    }
    
    /**
     * Force refresh a profile by fetching fresh data from relays
     * Useful for explicit user refresh or when new metadata arrives
     */
    override suspend fun refreshUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>> {
        SecureLog.d("ProfileRepository: Force refreshing profile for ${pubkey.take(8)}")
        
        // Don't clear cache, just fetch fresh and let comparison decide if it's newer
        val result = NostrRepository.fetchUserProfile(pubkey)
        
        // Update cache with new metadata if it's newer
        val newCreatedAt = result.first?.createdAt ?: 0L
        cacheLock.withLock {
            val cached = profileCache[pubkey]
            if (cached == null || newCreatedAt > cached.createdAtTimestamp) {
                val profile = CachedProfile(
                    metadata = result.first,
                    relays = result.second,
                    createdAtTimestamp = newCreatedAt
                )
                profileCache[pubkey] = profile
                
                // Also persist to disk
                ProfileMetadataCache.cache(pubkey, result.first, result.second)
                
                SecureLog.d("ProfileRepository: Updated cache with refreshed profile for ${pubkey.take(8)}")
            } else {
                SecureLog.d("ProfileRepository: Cached profile is newer, keeping it after refresh")
            }
        }
        
        return result
    }
}
