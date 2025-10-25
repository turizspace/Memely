package com.memely.data

import com.memely.nostr.MemeNote
import com.memely.nostr.NostrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * Repository for fetching replies, reactions, and reposts for a given event
 */
object InteractionRepository {
    
    // Cache for interaction counts: Map<eventId, InteractionCounts>
    private val interactionCounts = mutableMapOf<String, InteractionCounts>()
    // Track active subscriptions so we can close them
    private val activeSubscriptions = mutableSetOf<String>()
    
    data class InteractionCounts(
        val eventId: String,
        val replyCount: Int = 0,
        val likeCount: Int = 0,
        val dislikeCount: Int = 0,
        val emojiReactions: Map<String, Int> = emptyMap(),
        val repostCount: Int = 0,
        val replies: List<MemeNote> = emptyList()
    )
    
    /**
     * Fetch interaction counts and replies for an event
     * Waits briefly for subscription events before returning to ensure data is loaded
     */
    suspend fun fetchInteractions(eventId: String): InteractionCounts? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                interactionCounts[eventId]?.let { 
                    println("💾 InteractionRepository: Using cached interactions for $eventId")
                    return@withContext it
                }
                
                val subscriptionId = "interactions-${eventId.take(8)}-${System.currentTimeMillis()}"
                
                // Use Sets to deduplicate by event ID (same event from multiple relays)
                val repliesById = mutableMapOf<String, MemeNote>()
                val reactionsById = mutableSetOf<String>()
                val repostsById = mutableSetOf<String>()
                val emojiReactions = mutableMapOf<String, Int>()
                
                // Subscribe to get replies (kind 1 with e-tag matching eventId)
                NostrRepository.subscribeToReplies(eventId, subscriptionId) { event ->
                    try {
                        val json = org.json.JSONObject(event)
                        val kind = json.optInt("kind", 0)
                        val noteId = json.optString("id", "")
                        
                        // Skip if we've already seen this event ID
                        if (noteId.isBlank()) return@subscribeToReplies
                        
                        when (kind) {
                            1 -> {
                                // Reply (kind 1) - verify it's actually replying to this eventId
                                val tags = json.optJSONArray("tags") ?: org.json.JSONArray()
                                var isReplyToThisEvent = false
                                for (i in 0 until tags.length()) {
                                    val tag = tags.optJSONArray(i)
                                    if (tag?.optString(0) == "e" && tag.optString(1) == eventId) {
                                        isReplyToThisEvent = true
                                        break
                                    }
                                }
                                if (isReplyToThisEvent && !repliesById.containsKey(noteId)) {
                                    repliesById[noteId] = parseMemeNoteFromJson(json)
                                    println("📝 InteractionRepository: Added reply ${noteId.take(8)} from ${json.optString("pubkey").take(8)}")
                                }
                            }
                            7 -> {
                                // Reaction (kind 7) - deduplicate by event ID
                                if (!reactionsById.contains(noteId)) {
                                    reactionsById.add(noteId)
                                    val content = json.optString("content", "+")
                                    emojiReactions[content] = (emojiReactions[content] ?: 0) + 1
                                }
                            }
                            6 -> {
                                // Repost (kind 6) - deduplicate by event ID
                                if (!repostsById.contains(noteId)) {
                                    repostsById.add(noteId)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("❌ InteractionRepository: Error parsing interaction event: ${e.message}")
                    }
                }
                
                // Wait for subscription to receive events (3 second timeout)
                println("⏳ InteractionRepository: Waiting for subscription events for $eventId...")
                for (i in 0..30) { // 3 seconds with 100ms checks
                    delay(100)
                }
                
                // Close subscription after we're done
                NostrRepository.closeSubscription(subscriptionId)
                activeSubscriptions.remove(subscriptionId)
                println("🔚 InteractionRepository: Closed subscription $subscriptionId")
                
                // Calculate counts from deduplicated data
                val replies = repliesById.values.toList()
                val likeCount = emojiReactions["+"] ?: 0
                val dislikeCount = emojiReactions["-"] ?: 0
                val repostCount = repostsById.size
                
                val counts = InteractionCounts(
                    eventId = eventId,
                    replyCount = replies.size,
                    likeCount = likeCount,
                    dislikeCount = dislikeCount,
                    emojiReactions = emojiReactions,
                    repostCount = repostCount,
                    replies = replies
                )
                
                // Cache the results
                interactionCounts[eventId] = counts
                println("✅ InteractionRepository: Fetched interactions for $eventId - ${replies.size} replies, $likeCount likes, $repostCount reposts (deduplicated)")
                
                counts
            } catch (e: Exception) {
                println("❌ InteractionRepository: Failed to fetch interactions: ${e.message}")
                null
            }
        }
    }
    
    private fun parseMemeNoteFromJson(json: JSONObject): MemeNote {
        val tagsArray = json.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<List<String>>()
        for (i in 0 until tagsArray.length()) {
            val tagArray = tagsArray.optJSONArray(i)
            val tag = mutableListOf<String>()
            if (tagArray != null) {
                for (j in 0 until tagArray.length()) {
                    tag.add(tagArray.optString(j, ""))
                }
            }
            tags.add(tag)
        }
        
        return MemeNote(
            id = json.optString("id", ""),
            pubkey = json.optString("pubkey", ""),
            content = json.optString("content", ""),
            createdAt = json.optLong("created_at", 0),
            tags = tags
        )
    }
    
    /**
     * Clear cache for an event (useful after user posts new reaction/reply)
     */
    fun invalidateCache(eventId: String) {
        interactionCounts.remove(eventId)
        println("🔄 InteractionRepository: Invalidated cache for $eventId")
    }
    
    /**
     * Get cached interaction counts without refetching
     */
    fun getCachedInteractions(eventId: String): InteractionCounts? {
        return interactionCounts[eventId]
    }
}
