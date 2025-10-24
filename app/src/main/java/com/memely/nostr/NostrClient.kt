package com.memely.nostr

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.ByteString
import kotlin.coroutines.resume

class NostrClient(private val relayUrl: String) {
    private var webSocket: WebSocket? = null
    val incoming = Channel<String>(Channel.BUFFERED)

    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        val client = OkHttpClient()
        val request = Request.Builder().url(relayUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                println("✅ Connected to relay: $relayUrl")
                if (cont.isActive) cont.resume(true)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                incoming.trySend(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                incoming.trySend(bytes.utf8())
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

    suspend fun publish(rawEvent: String): Boolean {
        return webSocket?.send(rawEvent) ?: false
    }

    fun close() {
        webSocket?.close(1000, "Closed")
        incoming.close()
    }
}
