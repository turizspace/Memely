package com.memely.nostr

import com.memely.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles signing and publishing interaction events (reactions, replies, reposts)
 * Supports both Amber signer and nsec-based signing.
 */
interface InteractionPublisher {
    suspend fun publishReaction(
        targetEventId: String,
        targetPubkey: String,
        content: String = "+",
        relayUrl: String? = null
    )

    suspend fun publishReply(
        content: String,
        replyToEventId: String,
        replyToPubkey: String,
        relayUrl: String? = null
    )

    suspend fun publishRepost(
        targetEventId: String,
        targetPubkey: String,
        originalEventJson: String? = null,
        relayUrl: String? = null
    )
}

class DefaultInteractionPublisher : InteractionPublisher {
    override suspend fun publishReaction(
        targetEventId: String,
        targetPubkey: String,
        content: String,
        relayUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            val userPubkey = KeyStoreManager.getPubkeyHex()
                ?: throw Exception("No pubkey available")

            SecureLog.d(
                "InteractionPublisher: Creating reaction for ${SecureLog.truncateHex(targetEventId)} " +
                    "as ${SecureLog.truncateHex(userPubkey)}"
            )

            val reactionEvent = InteractionController.createReactionEvent(
                targetEventId = targetEventId,
                targetPubkey = targetPubkey,
                content = content,
                userPubkey = userPubkey,
                relayUrl = relayUrl
            )

            signAndPublish(reactionEvent.toJson().toString())
        }
    }

    override suspend fun publishReply(
        content: String,
        replyToEventId: String,
        replyToPubkey: String,
        relayUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            val userPubkey = KeyStoreManager.getPubkeyHex()
                ?: throw Exception("No pubkey available")

            SecureLog.d(
                "InteractionPublisher: Creating reply for ${SecureLog.truncateHex(replyToEventId)} " +
                    "as ${SecureLog.truncateHex(userPubkey)}"
            )

            val replyEvent = InteractionController.createReplyEvent(
                content = content,
                replyToEventId = replyToEventId,
                replyToPubkey = replyToPubkey,
                userPubkey = userPubkey,
                relayUrl = relayUrl
            )

            signAndPublish(replyEvent.toJson().toString())
        }
    }

    override suspend fun publishRepost(
        targetEventId: String,
        targetPubkey: String,
        originalEventJson: String?,
        relayUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            val userPubkey = KeyStoreManager.getPubkeyHex()
                ?: throw Exception("No pubkey available")

            SecureLog.d(
                "InteractionPublisher: Creating repost for ${SecureLog.truncateHex(targetEventId)} " +
                    "as ${SecureLog.truncateHex(userPubkey)}"
            )

            val repostEvent = InteractionController.createRepostEvent(
                targetEventId = targetEventId,
                targetPubkey = targetPubkey,
                userPubkey = userPubkey,
                originalEventJson = originalEventJson,
                relayUrl = relayUrl
            )

            signAndPublish(repostEvent.toJson().toString())
        }
    }

    private suspend fun signAndPublish(unsignedEventJsonStr: String) {
        val unsignedEventJson = JSONObject(unsignedEventJsonStr)
        val eventId = NostrEventSigner.calculateEventId(unsignedEventJsonStr)
        val userPubkey = unsignedEventJson.optString("pubkey")

        if (userPubkey.isBlank()) {
            throw Exception("Event missing pubkey field")
        }

        val isUsingAmber = KeyStoreManager.isUsingAmber()
        SecureLog.d(
            "InteractionPublisher: Signing event ${SecureLog.truncateHex(eventId)} " +
                "using ${if (isUsingAmber) "Amber" else "local key"}"
        )

        if (isUsingAmber) {
            signAndPublishWithAmber(unsignedEventJson, eventId, userPubkey)
        } else {
            signAndPublishWithLocalKey(unsignedEventJson, userPubkey)
        }
    }

    private suspend fun signAndPublishWithAmber(
        unsignedEventJson: JSONObject,
        eventId: String,
        userPubkey: String
    ) {
        val configuredPubkey = AmberSignerManager.getConfiguredPubkey()
        if (configuredPubkey != null && configuredPubkey != userPubkey) {
            SecureLog.w(
                "InteractionPublisher: Amber pubkey mismatch configured=${SecureLog.truncateHex(configuredPubkey)} " +
                    "event=${SecureLog.truncateHex(userPubkey)}"
            )
        }

        unsignedEventJson.put("id", eventId)
        val signResult = AmberSignerManager.signEvent(
            eventJson = unsignedEventJson.toString(),
            eventId = eventId,
            timeoutMs = 30_000
        )

        val signature = signResult.result
        if (signature.isNullOrBlank()) {
            throw Exception("Empty result from Amber")
        }

        val signedJson = unsignedEventJson.apply {
            put("sig", signature)
        }

        SecureLog.d("InteractionPublisher: Publishing Amber-signed interaction ${SecureLog.truncateHex(eventId)}")
        NostrRepository.publishEvent("""["EVENT",${signedJson}]""")
    }

    private fun signAndPublishWithLocalKey(
        unsignedEventJson: JSONObject,
        userPubkey: String
    ) {
        val privKeyHex = KeyStoreManager.exportNsecHex()
            ?: throw Exception("No private key available")

        val privKeyBytes = privKeyHex.hexToBytes()
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

        val signedEventJson = NostrEventSigner.signEvent(
            kind = kind,
            content = content,
            tags = tags,
            pubkeyHex = userPubkey,
            privKeyBytes = privKeyBytes
        )

        SecureLog.d("InteractionPublisher: Publishing local-key interaction")
        NostrRepository.publishEvent("""["EVENT",$signedEventJson]""")
    }
}

private fun String.hexToBytes(): ByteArray {
    val clean = this.trim().removePrefix("0x")
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        val idx = i * 2
        out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
    }
    return out
}
