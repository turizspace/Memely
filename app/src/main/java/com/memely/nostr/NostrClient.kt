package com.memely.nostr

import com.memely.network.SecureHttpClient
import com.memely.utils.SecureJsonParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Enhanced NostrClient that tracks relay responses (OK/FAILED)
 * 
 * Parses OK messages from relays and reports acceptance/rejection via RelayEventTracker
 * Format: ["OK", <event_id>, <accepted>, <message>]
 * 
 * Security: Uses SecureHttpClient for WebSocket connections with proper timeouts.
 */
class NostrClient(private val relayUrl: String) {
    private var webSocket: WebSocket? = null
    val incoming = Channel<String>(Channel.BUFFERED)

    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        val client = SecureHttpClient.createWebSocketClient()
        val request = Request.Builder().url(relayUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                println("✅ Connected to relay: $relayUrl")
                if (cont.isActive) cont.resume(true)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // Parse and track OK/FAILED responses
                parseOkResponse(text)
                incoming.trySend(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                parseOkResponse(text)
                incoming.trySend(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                println("❌ Relay failed $relayUrl: ${t.message}")
                if (cont.isActive) cont.resume(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                println("⚠️ Relay closed $relayUrl ($reason)")
            }
        })
    }

    /**
     * Parse and handle OK responses from relay.
     * 
     * Format: ["OK", <event_id>, <accepted: true/false>, <message>]
     * 
     * Example:
     * ["OK", "e91a6c9c-7c7f-4c7f-7c7f-7c7f7c7f7c7f", true, ""]  - Accepted
     * ["OK", "e91a6c9c-7c7f-4c7f-7c7f-7c7f7c7f7c7f", false, "Duplicate event"]  - Rejected
     */
    private fun parseOkResponse(text: String) {
        try {
            if (!text.startsWith("[\"OK\"")) {
                return  // Not an OK response
            }
            
            // Use secure parser with validation
            val array = SecureJsonParser.parseNostrMessage(text) ?: return
            
            if (array.length() < 4) {
                return  // Invalid format
            }
            
            val messageType = array.optString(0)
            if (messageType != "OK") {
                return
            }
            
            val eventId = array.optString(1)
            val accepted = array.optBoolean(2)
            val message = array.optString(3)
            
            if (eventId.isBlank()) {
                return  // Invalid event ID
            }
            
            // Report to tracker
            if (accepted) {
                RelayEventTracker.recordAcceptance(eventId, relayUrl)
                println("✅ OK from $relayUrl: Event $eventId accepted")
            } else {
                RelayEventTracker.recordRejection(eventId, relayUrl, message)
                println("❌ OK from $relayUrl: Event $eventId rejected - $message")
            }
            
        } catch (e: Exception) {
            // Silently ignore parsing errors for non-OK messages
            // println("Debug: Not an OK message: ${e.message}")
        }
    }

    suspend fun publish(rawEvent: String): Boolean {
        return webSocket?.send(rawEvent) ?: false
    }

    fun close() {
        webSocket?.close(1000, "Closed")
        incoming.close()
    }
}
