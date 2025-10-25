package com.memely.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * Handles signing and publishing interaction events (reactions, replies, reposts)
 * Supports both Amber signer and nsec-based signing
 */
object InteractionPublisher {
    
    /**
     * Sign and publish a reaction event
     */
    suspend fun publishReaction(
        targetEventId: String,
        targetPubkey: String,
        content: String = "+",
        relayUrl: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Get user's pubkey
                val userPubkey = KeyStoreManager.getPubkeyHex()
                    ?: throw Exception("No pubkey available")
                
                val reactionEvent = InteractionController.createReactionEvent(
                    targetEventId = targetEventId,
                    targetPubkey = targetPubkey,
                    content = content,
                    userPubkey = userPubkey,
                    relayUrl = relayUrl
                )
                
                signAndPublish(reactionEvent.toJson().toString())
            } catch (e: Exception) {
                println("❌ InteractionPublisher: Failed to publish reaction: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Sign and publish a reply event
     */
    suspend fun publishReply(
        content: String,
        replyToEventId: String,
        replyToPubkey: String,
        relayUrl: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Get user's pubkey
                val userPubkey = KeyStoreManager.getPubkeyHex()
                    ?: throw Exception("No pubkey available")
                
                val replyEvent = InteractionController.createReplyEvent(
                    content = content,
                    replyToEventId = replyToEventId,
                    replyToPubkey = replyToPubkey,
                    userPubkey = userPubkey,
                    relayUrl = relayUrl
                )
                
                signAndPublish(replyEvent.toJson().toString())
            } catch (e: Exception) {
                println("❌ InteractionPublisher: Failed to publish reply: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Sign and publish a repost event
     */
    suspend fun publishRepost(
        targetEventId: String,
        targetPubkey: String,
        originalEventJson: String? = null,
        relayUrl: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Get user's pubkey
                val userPubkey = KeyStoreManager.getPubkeyHex()
                    ?: throw Exception("No pubkey available")
                
                val repostEvent = InteractionController.createRepostEvent(
                    targetEventId = targetEventId,
                    targetPubkey = targetPubkey,
                    userPubkey = userPubkey,
                    originalEventJson = originalEventJson,
                    relayUrl = relayUrl
                )
                
                signAndPublish(repostEvent.toJson().toString())
            } catch (e: Exception) {
                println("❌ InteractionPublisher: Failed to publish repost: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Generic sign and publish for unsigned event JSON string
     * Handles both Amber and nsec signing
     */
    private suspend fun signAndPublish(unsignedEventJsonStr: String) {
        try {
            val unsignedEventJson = JSONObject(unsignedEventJsonStr)
            val eventId = NostrEventSigner.calculateEventId(unsignedEventJsonStr)
            val userPubkey = unsignedEventJson.optString("pubkey")
            
            if (userPubkey.isBlank()) {
                throw Exception("Event missing pubkey field")
            }
            
            println("📝 InteractionPublisher: Signing event - Kind: ${unsignedEventJson.optInt("kind")}, ID: $eventId")
            
            val isUsingAmber = KeyStoreManager.isUsingAmber()
            
            if (isUsingAmber) {
                // Sign with Amber (NIP-55)
                try {
                    // Add ID to event for Amber
                    unsignedEventJson.put("id", eventId)
                    
                    val signResult = AmberSignerManager.signEvent(
                        eventJson = unsignedEventJson.toString(),
                        eventId = eventId,
                        timeoutMs = 30_000
                    )
                    
                    // Parse the result - Amber returns the signature directly as hex string (not JSON)
                    if (!signResult.result.isNullOrBlank()) {
                        val signature = signResult.result!!  // Direct hex string from Amber
                        
                        if (signature.isNotBlank()) {
                            println("✅ InteractionPublisher: Signed with Amber - Sig: ${signature.take(20)}")
                            
                            // Build signed event JSON string using the modified unsignedEventJson that has the ID
                            val signedJson = unsignedEventJson.apply {
                                put("sig", signature)
                            }
                            
                            // Publish to relays as JSON event
                            val eventMessage = """["EVENT",${signedJson}]"""
                            NostrRepository.publishEvent(eventMessage)
                            println("📤 InteractionPublisher: Published event to relays via Amber")
                        } else {
                            println("⚠️ InteractionPublisher: Amber returned empty signature")
                            throw Exception("Empty signature from Amber")
                        }
                    } else {
                        println("⚠️ InteractionPublisher: Amber returned empty result")
                        throw Exception("Empty result from Amber")
                    }
                } catch (amberError: Exception) {
                    println("⚠️ InteractionPublisher: Amber signing failed: ${amberError.message}")
                    throw amberError
                }
            } else {
                // Sign with local nsec
                try {
                    val privKeyHex = KeyStoreManager.exportNsecHex()
                        ?: throw Exception("No private key available")
                    
                    val privKeyBytes = privKeyHex.hexToBytes()
                    
                    // Extract event fields
                    val kind = unsignedEventJson.optInt("kind")
                    val content = unsignedEventJson.optString("content", "")
                    val tagsJson = unsignedEventJson.optJSONArray("tags")
                    val tags = if (tagsJson != null) {
                        (0 until tagsJson.length()).map { i ->
                            val tagArr = tagsJson.getJSONArray(i)
                            (0 until tagArr.length()).map { j ->
                                tagArr.getString(j)
                            }
                        }
                    } else {
                        emptyList()
                    }
                    
                    // Sign with nsec
                    val signedEventJson = NostrEventSigner.signEvent(
                        kind = kind,
                        content = content,
                        tags = tags,
                        pubkeyHex = userPubkey,
                        privKeyBytes = privKeyBytes
                    )
                    
                    println("✅ InteractionPublisher: Signed with nsec")
                    
                    // Publish to relays
                    val eventMessage = """["EVENT",$signedEventJson]"""
                    NostrRepository.publishEvent(eventMessage)
                    println("📤 InteractionPublisher: Published event to relays via nsec")
                } catch (nsecError: Exception) {
                    println("⚠️ InteractionPublisher: nsec signing failed: ${nsecError.message}")
                    throw nsecError
                }
            }
        } catch (e: Exception) {
            println("❌ InteractionPublisher: Error signing/publishing event: ${e.message}")
            throw e
        }
    }
}

// Helper extension to convert hex string to bytes
private fun String.hexToBytes(): ByteArray {
    val clean = this.trim().removePrefix("0x")
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        val idx = i * 2
        out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
    }
    return out
}
