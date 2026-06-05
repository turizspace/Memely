package com.memely.data.metadata

import android.content.Context
import com.memely.data.repositories.ProfileRepository
import com.memely.nostr.MetadataParser
import com.memely.nostr.NostrRepository
import com.memely.util.SecureLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject

/**
 * Listens for new kind 0 (metadata) events from relays
 * and updates the profile cache with fresh metadata.
 * 
 * Enables automatic background refresh when new user metadata
 * is published to the network, without requiring explicit refetch.
 */
object MetadataUpdateListener {
    private var listenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start listening for metadata updates
     * This should be called once during app initialization
     */
    fun startListening(profileRepository: ProfileRepository) {
        if (listenerJob?.isActive == true) {
            SecureLog.d("MetadataUpdateListener: Already listening")
            return
        }
        
        listenerJob = scope.launch {
            try {
                SecureLog.d("MetadataUpdateListener: Starting to listen for metadata updates")
                
                NostrRepository.incomingMessagesFlow.collect { msg ->
                    try {
                        // Check if this is a metadata event (kind 0)
                        if (msg.contains("\"kind\":0") && msg.contains("\"content\"")) {
                            handleMetadataEvent(msg, profileRepository)
                        }
                    } catch (e: Exception) {
                        SecureLog.w("MetadataUpdateListener: Error processing message: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                SecureLog.d("MetadataUpdateListener: Listener cancelled")
            } catch (e: Exception) {
                SecureLog.e("MetadataUpdateListener: Error in listener: ${e.message}")
            }
        }
    }
    
    /**
     * Stop listening for metadata updates
     */
    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
        SecureLog.d("MetadataUpdateListener: Stopped listening")
    }
    
    /**
     * Process a metadata event and update the cache
     */
    private suspend fun handleMetadataEvent(
        message: String,
        profileRepository: ProfileRepository
    ) {
        try {
            // Parse the message to extract pubkey
            val pubkey = extractPubkeyFromMessage(message) ?: return
            val createdAt = extractCreatedAtFromMessage(message)
            
            // Parse the metadata content
            val parsedMetadata = parseMetadataContent(message) ?: return
            
            // Log the update
            SecureLog.d(
                "MetadataUpdateListener: Received metadata update for ${pubkey.take(8)} - " +
                "name: ${parsedMetadata.name}, created_at: $createdAt"
            )
            
            // Force refresh the profile with new metadata
            // The refreshUserProfile function will compare timestamps and only update if newer
            profileRepository.refreshUserProfile(pubkey)
            
            SecureLog.d("MetadataUpdateListener: Updated cache for ${pubkey.take(8)}")
        } catch (e: Exception) {
            SecureLog.w("MetadataUpdateListener: Error handling metadata event: ${e.message}")
        }
    }
    
    /**
     * Extract pubkey from Nostr event message
     */
    private fun extractPubkeyFromMessage(msg: String): String? {
        return try {
            when {
                msg.trim().startsWith("[") -> {
                    // MESSAGE format: ["EVENT", <subscription_id>, <event_object>]
                    val arr = JSONArray(msg)
                    if (arr.length() >= 3) {
                        arr.getJSONObject(2).optString("pubkey", null)
                    } else null
                }
                msg.trim().startsWith("{") -> {
                    // Direct event object format
                    JSONObject(msg).optString("pubkey", null)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract created_at timestamp from Nostr event message
     */
    private fun extractCreatedAtFromMessage(msg: String): Long {
        return try {
            when {
                msg.trim().startsWith("[") -> {
                    val arr = JSONArray(msg)
                    if (arr.length() >= 3) {
                        arr.getJSONObject(2).optLong("created_at", System.currentTimeMillis() / 1000)
                    } else System.currentTimeMillis() / 1000
                }
                msg.trim().startsWith("{") -> {
                    JSONObject(msg).optLong("created_at", System.currentTimeMillis() / 1000)
                }
                else -> System.currentTimeMillis() / 1000
            }
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000
        }
    }
    
    /**
     * Parse metadata content from message
     */
    private fun parseMetadataContent(msg: String): MetadataParser.UserMetadata? {
        return try {
            val content = when {
                msg.trim().startsWith("[") -> {
                    val arr = JSONArray(msg)
                    if (arr.length() >= 3) {
                        arr.getJSONObject(2).optString("content", "{}")
                    } else "{}"
                }
                msg.trim().startsWith("{") -> {
                    JSONObject(msg).optString("content", "{}")
                }
                else -> "{}"
            }
            
            MetadataParser.parseMetadata(content)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Manual refresh for a specific profile
     * Useful for pull-to-refresh or explicit user action
     */
    suspend fun refreshProfile(
        pubkey: String,
        profileRepository: ProfileRepository
    ) {
        SecureLog.d("MetadataUpdateListener: Manual refresh requested for ${pubkey.take(8)}")
        try {
            val result = profileRepository.refreshUserProfile(pubkey)
            SecureLog.d(
                "MetadataUpdateListener: Manual refresh completed - " +
                "name: ${result.first?.name ?: "unknown"}"
            )
        } catch (e: Exception) {
            SecureLog.e("MetadataUpdateListener: Error during manual refresh: ${e.message}")
        }
    }
    
    /**
     * Manual refresh for multiple profiles
     * Useful for refreshing entire feed/explore tab
     */
    suspend fun refreshProfiles(
        pubkeys: List<String>,
        profileRepository: ProfileRepository
    ) {
        SecureLog.d("MetadataUpdateListener: Manual refresh requested for ${pubkeys.size} profiles")
        
        // Refresh in parallel for efficiency
        withContext(Dispatchers.IO) {
            pubkeys.map { pubkey ->
                async {
                    try {
                        profileRepository.refreshUserProfile(pubkey)
                    } catch (e: Exception) {
                        SecureLog.w("MetadataUpdateListener: Error refreshing ${pubkey.take(8)}: ${e.message}")
                    }
                }
            }.awaitAll()
        }
        
        SecureLog.d("MetadataUpdateListener: Manual refresh completed for ${pubkeys.size} profiles")
    }
}