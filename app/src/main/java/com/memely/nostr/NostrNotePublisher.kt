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
}

class DefaultNostrNotePublisher : NostrNotePublisher {
    override suspend fun publishNote(
        content: String,
        imageUrl: String,
        pubkeyHex: String,
        privKeyBytes: ByteArray
    ): PublishedNote = withContext(Dispatchers.IO) {
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

        val signedEventJson = NostrEventSigner.signEvent(
            kind = 1,
            content = fullContent,
            tags = tags,
            pubkeyHex = pubkeyHex,
            privKeyBytes = privKeyBytes
        )

        val eventId = JSONObject(signedEventJson).getString("id")
        RelayEventTracker.initializeEventTracking(eventId, NostrRepository.relayPool.getCurrentRelays())

        SecureLog.d("NostrNotePublisher: Publishing note ${SecureLog.truncateHex(eventId)}")
        NostrRepository.publishEvent("""["EVENT",$signedEventJson]""")

        PublishedNote(eventId = eventId)
    }
}
