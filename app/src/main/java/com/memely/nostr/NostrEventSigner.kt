package com.memely.nostr

import fr.acinq.secp256k1.Secp256k1
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * NostrEventSigner — builds and signs Nostr events (NIP-01) using BIP-340 Schnorr signatures.
 */
object NostrEventSigner {

    /**
     * Sign a Nostr event with the provided private key using BIP-340 Schnorr signature.
     * Returns a complete signed event JSON string.
     */
    fun signEvent(
        kind: Int,
        content: String,
        tags: List<List<String>>,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): String {
        require(privKeyBytes.size == 32) { "Private key must be 32 bytes" }
        
        val createdAt = System.currentTimeMillis() / 1000L
        
        // Build event serialization for hashing according to NIP-01
        // Format: [0, pubkey, created_at, kind, tags, content]
        // Must be compact JSON with no extra whitespace
        val tagsJson = buildCompactTagsJson(tags)
        val contentEscaped = escapeJsonString(content)
        
        val serialized = """[0,"$pubkeyHex",$createdAt,$kind,$tagsJson,"$contentEscaped"]"""
        
        // Note: Logging removed for security - event serialization contains sensitive data
        
        val eventBytes = serialized.toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(eventBytes)
        val eventId = hash.toHex()
        
        // Note: Logging removed for security - event IDs can be correlated to users
        
        // Sign the hash using BIP-340 Schnorr
        val signature = schnorrSign(hash, privKeyBytes)
        
        // Build complete signed event
        return JSONObject().apply {
            put("id", eventId)
            put("pubkey", pubkeyHex)
            put("created_at", createdAt)
            put("kind", kind)
            put("tags", JSONArray(tags.map { JSONArray(it) }))
            put("content", content)
            put("sig", signature)
        }.toString()
    }

    /**
     * Build compact JSON representation of tags array.
     */
    private fun buildCompactTagsJson(tags: List<List<String>>): String {
        if (tags.isEmpty()) return "[]"
        
        val tagStrings = tags.map { tag ->
            val escapedElements = tag.map { escapeJsonString(it) }
            "[" + escapedElements.joinToString(",") { "\"$it\"" } + "]"
        }
        
        return "[" + tagStrings.joinToString(",") + "]"
    }

    /**
     * Escape string for JSON (handle quotes, backslashes, newlines, etc.)
     */
    private fun escapeJsonString(str: String): String {
        return str
            .replace("\\", "\\\\")  // Backslash must be first
            .replace("\"", "\\\"")  // Quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t")   // Tab
            .replace("\b", "\\b")   // Backspace
            .replace("\u000C", "\\f") // Form feed
    }

    /**
     * Sign with BIP-340 Schnorr signature using secp256k1-kmp.
     */
    private fun schnorrSign(hash: ByteArray, privKeyBytes: ByteArray): String {
        require(privKeyBytes.size == 32) { "Private key must be 32 bytes" }
        require(hash.size == 32) { "Hash must be 32 bytes" }
        
        // Generate 32 bytes of auxiliary random data for Schnorr signing
        val auxRand = ByteArray(32)
        SecureRandom().nextBytes(auxRand)
        
        // Sign using BIP-340 Schnorr
        val signature = Secp256k1.signSchnorr(hash, privKeyBytes, auxRand)
        
        return signature.toHex()
    }

    /**
     * Calculate event ID from unsigned event JSON.
     * This is needed before sending to Amber for signing.
     */
    fun calculateEventId(eventJson: String): String {
        val jsonObj = JSONObject(eventJson)
        val pubkey = jsonObj.getString("pubkey")
        val createdAt = jsonObj.getLong("created_at")
        val kind = jsonObj.getInt("kind")
        val content = jsonObj.optString("content", "")
        val tagsArray = jsonObj.optJSONArray("tags")
        
        val tags = mutableListOf<List<String>>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tagArray = tagsArray.getJSONArray(i)
                val tag = mutableListOf<String>()
                for (j in 0 until tagArray.length()) {
                    tag.add(tagArray.getString(j))
                }
                tags.add(tag)
            }
        }
        
        // Build event serialization for hashing according to NIP-01
        val tagsJson = buildCompactTagsJson(tags)
        val contentEscaped = escapeJsonString(content)
        val serialized = """[0,"$pubkey",$createdAt,$kind,$tagsJson,"$contentEscaped"]"""
        
        val eventBytes = serialized.toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(eventBytes)
        return hash.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
