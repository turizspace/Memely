package com.memely.nostr

import com.memely.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PublishedNote(
    val eventId: String
)

interface NostrNotePublisher {
    suspend fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): PublishedNote

    suspend fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        signEvent: suspend (String) -> String
    ): PublishedNote
}

class DefaultNostrNotePublisher : NostrNotePublisher {
    override suspend fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): PublishedNote = publishNote(content, imageUrl, pubkeyHex) { eventJson ->
        NostrEventSigner.signEventJson(eventJson, privKeyBytes)
    }

    override suspend fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        signEvent: suspend (String) -> String
    ): PublishedNote {
        val fullContent = if (content.isNotBlank()) {
            "$content\n\n$imageUrl"
        } else {
            imageUrl
        }

        val tags = listOf(
            listOf("imeta", "url $imageUrl"),
            listOf("url", imageUrl),
            listOf("client", "Memely"),
            listOf("t", "meme"),
            listOf("t", "memely")
        )

        val unsignedEvent = JSONObject().apply {
            put("kind", 1)
            put("created_at", System.currentTimeMillis() / 1000L)
            put("tags", org.json.JSONArray(tags.map { org.json.JSONArray(it) }))
            put("content", fullContent)
            put("pubkey", pubkeyHex)
        }.toString()
        val signedEventJson = signEvent(unsignedEvent)
        val signedEvent = JSONObject(signedEventJson)
        require(signedEvent.optInt("kind") == 1) { "Signer returned an event with the wrong kind" }
        require(signedEvent.optString("pubkey").equals(pubkeyHex, ignoreCase = true)) {
            "Signer returned an event for a different public key"
        }
        require(signedEvent.optString("id").equals(
            NostrEventSigner.calculateEventId(signedEventJson),
            ignoreCase = true
        )) { "Signer returned an event with an invalid ID" }
        require(signedEvent.optString("sig").matches(Regex("[0-9a-fA-F]{128}"))) {
            "Signer returned an event without a valid signature"
        }

        val eventId = signedEvent.getString("id")
        withContext(Dispatchers.IO) {
            RelayEventTracker.initializeEventTracking(eventId, NostrRepository.relayPool.getCurrentRelays())

            SecureLog.d("NostrNotePublisher: Publishing note ${SecureLog.truncateHex(eventId)} using optimized posting manager")

            NostrRepository.publishEvent("""["EVENT",$signedEventJson]""")
        }
        return PublishedNote(eventId = eventId)
    }
}
