package com.memely.nostr

import org.json.JSONObject

/**
 * Parses Nostr metadata (kind=0) and relay lists (kind=10002).
 */
object MetadataParser {

    data class UserMetadata(
        val name: String? = null,
        val about: String? = null,
        val picture: String? = null,
        val nip05: String? = null,
        val lud16: String? = null,
        val banner: String? = null,
        val website: String? = null // Added website field for completeness
    )

    /**
     * Parse Nostr user metadata JSON into [UserMetadata].
     */
    fun parseMetadata(json: String): UserMetadata? {
        return try {
            val obj = JSONObject(json)
            UserMetadata(
                name = obj.optString("name", null),
                about = obj.optString("about", null),
                picture = obj.optString("picture", null),
                nip05 = obj.optString("nip05", null),
                lud16 = obj.optString("lud16", null),
                banner = obj.optString("banner", null),
                website = obj.optString("website", null)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse NIP-65 relay list (kind=10002).
     * Handles both JSON and plain text fallback formats.
     */
    fun parseRelayList(eventContent: String): List<String> {
        val relays = mutableListOf<String>()
        
        if (eventContent.isBlank()) {
            println("⚠️ MetadataParser: Relay list content is empty")
            return emptyList()
        }
        
        println("🔧 MetadataParser: Parsing relay list from: $eventContent")
        
        try {
            val obj = JSONObject(eventContent)
            val keys = obj.keys()
            println("📋 MetadataParser: Found JSON object with keys")
            
            var keyCount = 0
            while (keys.hasNext()) {
                val key = keys.next()
                keyCount++
                println("🔑 MetadataParser: Processing key #$keyCount: '$key'")
                
                // Check if this looks like a relay URL
                if (key.startsWith("wss://") || key.startsWith("ws://")) {
                    relays.add(key)
                    println("✅ MetadataParser: Added relay: $key")
                } else {
                    println("⚠️ MetadataParser: Key '$key' doesn't look like a relay URL")
                    
                    // Try to get the value
                    val value = obj.opt(key)
                    println("📊 MetadataParser: Value for key '$key': $value")
                    
                    // If value is an object, it might contain relay info
                    if (value is JSONObject) {
                        println("🔄 MetadataParser: Value is JSONObject, checking for relay URL")
                        if (key.startsWith("wss://") || key.startsWith("ws://")) {
                            relays.add(key)
                            println("✅ MetadataParser: Added relay from object key: $key")
                        }
                    } else if (value is String && (value.startsWith("wss://") || value.startsWith("ws://"))) {
                        // Sometimes the relay URL is the value, not the key
                        relays.add(value)
                        println("✅ MetadataParser: Added relay from string value: $value")
                    }
                }
            }
            println("✅ MetadataParser: Successfully parsed ${relays.size} relays from JSON (found $keyCount total keys)")
        } catch (e: Exception) {
            println("⚠️ MetadataParser: JSON parsing failed, trying fallback: ${e.message}")
            // fallback: split by newline or commas if not JSON
            val fallbackRelays = eventContent
                .split("\n", ",")
                .map { it.trim() }
                .filter { it.startsWith("wss://") || it.startsWith("ws://") }
            
            relays.addAll(fallbackRelays)
            println("✅ MetadataParser: Fallback parsed ${fallbackRelays.size} relays: $fallbackRelays")
        }
        
        val distinctRelays = relays.distinct()
        println("🌐 MetadataParser: Final relay list (${distinctRelays.size} distinct relays): $distinctRelays")
        return distinctRelays
    }
}