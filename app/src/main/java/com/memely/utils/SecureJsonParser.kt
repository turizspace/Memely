package com.memely.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Secure JSON parsing with validation, size limits, and DoS protection
 * 
 * Prevents:
 * - Billion laughs attack (deeply nested structures)
 * - Memory exhaustion (oversized payloads)
 * - Malformed message handling
 */
object SecureJsonParser {
    
    // Configuration
    private const val MAX_MESSAGE_SIZE = 1_000_000  // 1MB max
    private const val MAX_ARRAY_LENGTH = 10_000     // Max array elements
    private const val MAX_OBJECT_KEYS = 1_000       // Max object keys
    private const val MAX_NESTING_DEPTH = 50        // Max nested levels
    
    /**
     * Safely parse Nostr relay messages with validation
     * Expected format: [TYPE, SUB_ID, EVENT_OBJECT] or [TYPE, ...]
     */
    fun parseNostrMessage(text: String): JSONArray? {
        return try {
            // Size check first
            if (text.length > MAX_MESSAGE_SIZE) {
                println("⚠️ JSON message exceeds max size: ${text.length} > $MAX_MESSAGE_SIZE bytes")
                return null
            }
            
            // Verify it looks like JSON
            val trimmed = text.trim()
            if (!trimmed.startsWith("[")) {
                println("⚠️ Invalid message format: doesn't start with [")
                return null
            }
            
            // Parse with size validation
            val arr = JSONArray(text)
            
            // Validate structure
            if (arr.length() < 1) {
                println("⚠️ Empty message array")
                return null
            }
            
            // Validate message type (first element must be string)
            val messageType = arr.optString(0, "")
            if (messageType !in listOf("EVENT", "OK", "EOSE", "NOTICE", "CLOSED", "AUTH")) {
                println("⚠️ Unknown message type: $messageType")
                return null
            }
            
            // For EVENT messages, validate the event object exists and is reasonable
            if (messageType == "EVENT" && arr.length() >= 3) {
                val eventObj = arr.optJSONObject(2) ?: run {
                    println("⚠️ EVENT message missing event object at index 2")
                    return null
                }
                
                // Validate event size
                if (eventObj.toString().length > MAX_MESSAGE_SIZE / 2) {
                    println("⚠️ Event object too large: ${eventObj.toString().length} bytes")
                    return null
                }
                
                // Validate content field doesn't exceed reasonable size
                val content = eventObj.optString("content", "")
                if (content.length > 500_000) {  // 500KB max for content
                    println("⚠️ Event content exceeds max size: ${content.length} bytes")
                    return null
                }
            }
            
            // Validate nesting depth
            if (!validateNestingDepth(arr, 0)) {
                println("⚠️ Message exceeds max nesting depth of $MAX_NESTING_DEPTH")
                return null
            }
            
            arr
        } catch (e: Exception) {
            println("⚠️ JSON parse error: ${e.javaClass.simpleName} - ${e.message?.take(100)}")
            null
        }
    }
    
    /**
     * Safely get JSON object from array with bounds checking
     */
    fun getJSONObject(arr: JSONArray, index: Int): JSONObject? {
        return try {
            if (index < 0 || index >= arr.length()) {
                println("⚠️ Array index out of bounds: $index >= ${arr.length()}")
                return null
            }
            arr.optJSONObject(index)
        } catch (e: Exception) {
            println("⚠️ Error getting JSONObject: ${e.message?.take(50)}")
            null
        }
    }
    
    /**
     * Validate nesting depth to prevent billion laughs attack
     */
    private fun validateNestingDepth(obj: Any, currentDepth: Int): Boolean {
        if (currentDepth > MAX_NESTING_DEPTH) {
            return false
        }
        
        return when (obj) {
            is JSONArray -> {
                (0 until obj.length()).all { i ->
                    val elem = obj.opt(i)
                    elem == null || elem is String || elem is Number || elem is Boolean ||
                    validateNestingDepth(elem, currentDepth + 1)
                }
            }
            is JSONObject -> {
                if (obj.length() > MAX_OBJECT_KEYS) {
                    return false
                }
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value != null && value !is String && value !is Number && value !is Boolean) {
                        if (!validateNestingDepth(value, currentDepth + 1)) {
                            return false
                        }
                    }
                }
                true
            }
            else -> true
        }
    }
    
    /**
     * Safely get string value with length validation
     */
    fun getString(obj: JSONObject, key: String, maxLength: Int = 100_000): String? {
        return try {
            val value = obj.optString(key, null) ?: return null
            if (value.length > maxLength) {
                println("⚠️ String field '$key' exceeds max length: ${value.length} > $maxLength")
                return null
            }
            value
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Safely get array with length validation
     */
    fun getArray(obj: JSONObject, key: String): JSONArray? {
        return try {
            val arr = obj.optJSONArray(key) ?: return null
            if (arr.length() > MAX_ARRAY_LENGTH) {
                println("⚠️ Array field '$key' exceeds max length: ${arr.length()} > $MAX_ARRAY_LENGTH")
                return null
            }
            arr
        } catch (e: Exception) {
            null
        }
    }
}
