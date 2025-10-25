package com.memely.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Handles creation of interaction events (reactions, replies, reposts) per Nostr specs
 * - NIP-25: Reactions (kind 7)
 * - NIP-18: Reposts (kind 6)
 * - NIP-1: Text notes (kind 1) for replies
 */
object InteractionController {
    
    /**
     * Create a reaction event (kind 7) per NIP-25
     * @param targetEventId The event ID being reacted to
     * @param targetPubkey The pubkey of the event creator
     * @param content Reaction content: "+", "-", "", or emoji (defaults to "+" for like)
     * @param userPubkey The pubkey of the current user (signer)
     * @param relayUrl Optional relay hint where original event can be found
     */
    fun createReactionEvent(
        targetEventId: String,
        targetPubkey: String,
        content: String = "+",
        userPubkey: String,
        relayUrl: String? = null
    ): NostrEvent {
        val now = Instant.now().epochSecond
        
        // Build tags array
        val tags = JSONArray()
        
        // e tag (MUST include target event id) with optional relay hint
        val eTag = JSONArray()
        eTag.put(0, "e")
        eTag.put(1, targetEventId)
        if (!relayUrl.isNullOrBlank()) {
            eTag.put(2, relayUrl)
        }
        tags.put(eTag)
        
        // p tag (SHOULD include target pubkey) with optional relay hint
        val pTag = JSONArray()
        pTag.put(0, "p")
        pTag.put(1, targetPubkey)
        if (!relayUrl.isNullOrBlank()) {
            pTag.put(2, relayUrl)
        }
        tags.put(pTag)
        
        return NostrEvent(
            kind = 7,
            content = content,
            tags = tags,
            createdAt = now,
            pubkey = userPubkey
        )
    }
    
    /**
     * Create a repost event (kind 6) per NIP-18
     * @param targetEventId The event ID being reposted
     * @param targetPubkey The pubkey of the event creator
     * @param userPubkey The pubkey of the current user (signer)
     * @param originalEventJson Stringified JSON of the original event (optional but recommended)
     * @param relayUrl Relay hint where original event can be found
     */
    fun createRepostEvent(
        targetEventId: String,
        targetPubkey: String,
        userPubkey: String,
        originalEventJson: String? = null,
        relayUrl: String? = null
    ): NostrEvent {
        val now = Instant.now().epochSecond
        
        // Build tags array
        val tags = JSONArray()
        
        // e tag (MUST include target event id with relay url)
        val eTag = JSONArray()
        eTag.put(0, "e")
        eTag.put(1, targetEventId)
        if (!relayUrl.isNullOrBlank()) {
            eTag.put(2, relayUrl)
        }
        tags.put(eTag)
        
        // p tag (SHOULD include target pubkey)
        val pTag = JSONArray()
        pTag.put(0, "p")
        pTag.put(1, targetPubkey)
        tags.put(pTag)
        
        return NostrEvent(
            kind = 6,
            content = originalEventJson ?: "",
            tags = tags,
            createdAt = now,
            pubkey = userPubkey
        )
    }
    
    /**
     * Create a reply event (kind 1) per NIP-1 with reply threading per NIP-10
     * @param content The reply text content
     * @param replyToEventId The event ID being replied to
     * @param replyToPubkey The pubkey of the event author
     * @param userPubkey The pubkey of the current user (signer)
     * @param relayUrl Relay hint where original event can be found
     */
    fun createReplyEvent(
        content: String,
        replyToEventId: String,
        replyToPubkey: String,
        userPubkey: String,
        relayUrl: String? = null
    ): NostrEvent {
        val now = Instant.now().epochSecond
        
        // Build tags array
        val tags = JSONArray()
        
        // e tag for reply threading (mark last e tag as reply-to per NIP-10)
        val eTag = mutableListOf<String>()
        eTag.add("e")
        eTag.add(replyToEventId)
        if (!relayUrl.isNullOrBlank()) {
            eTag.add(relayUrl)
        }
        eTag.add("reply") // Mark as reply
        tags.put(JSONArray(eTag))
        
        // p tag for mentions (mark as mentions)
        val pTag = JSONArray()
        pTag.put(0, "p")
        pTag.put(1, replyToPubkey)
        if (!relayUrl.isNullOrBlank()) {
            pTag.put(2, relayUrl)
        }
        tags.put(pTag)
        
        return NostrEvent(
            kind = 1,
            content = content,
            tags = tags,
            createdAt = now,
            pubkey = userPubkey
        )
    }
}

/**
 * Simple NostrEvent wrapper for building events
 */
data class NostrEvent(
    val kind: Int,
    val content: String,
    val tags: JSONArray,
    val createdAt: Long,
    var pubkey: String = "",
    var id: String = "",
    var sig: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("kind", kind)
            put("content", content)
            put("tags", tags)
            put("created_at", createdAt)
            if (pubkey.isNotBlank()) put("pubkey", pubkey)
            if (id.isNotBlank()) put("id", id)
            if (sig.isNotBlank()) put("sig", sig)
        }
    }
}
