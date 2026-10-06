package com.memely.blossom

import com.memely.nostr.AmberSignerManager
import com.memely.nostr.KeyStoreManager
import com.memely.nostr.KeyUtils
import com.memely.nostr.NostrEventSigner
import org.json.JSONObject

/** Signs a Blossom authorization event with the currently configured Nostr identity. */
suspend fun signBlossomAuthorization(eventJson: String, pubkey: String): String {
    require(pubkey.matches(Regex("[0-9a-fA-F]{64}"))) {
        "Active identity has an invalid public key"
    }
    val activePubkey = KeyStoreManager.getPubkeyHex()
        ?: throw IllegalStateException("No active Nostr identity is available")
    require(activePubkey.equals(pubkey, ignoreCase = true)) {
        "Blossom authorization identity does not match the active account"
    }

    val json = JSONObject(eventJson)
    require(json.optString("pubkey").equals(pubkey, ignoreCase = true)) {
        "Blossom authorization event has a different public key"
    }
    val kind = json.getInt("kind")
    val createdAt = json.getLong("created_at")
    val tags = json.getJSONArray("tags").let { array ->
        (0 until array.length()).map { i ->
            val tag = array.getJSONArray(i)
            (0 until tag.length()).map { j -> tag.getString(j) }
        }
    }

    val eventId = NostrEventSigner.calculateEventId(eventJson)
    val eventWithId = JSONObject(eventJson).apply { put("id", eventId) }.toString()
    if (KeyStoreManager.isUsingAmber()) {
        val packageName = KeyStoreManager.getAmberPackageName()
            ?: throw IllegalStateException("No external signer is configured")
        AmberSignerManager.configure(pubkey, packageName)
        return AmberSignerManager.signEvent(eventWithId, eventId).event
            ?: throw IllegalStateException("External signer did not return a signed event")
    }

    val privateKeyHex = KeyStoreManager.exportNsecHex()
        ?: throw IllegalStateException("No local signing key is available")
    val privateKey = privateKeyHex.hexToBytes()
    val derivedPubkey = KeyUtils.publicKeyXOnlyHexFromPrivate(privateKey)
    require(derivedPubkey.equals(pubkey, ignoreCase = true)) {
        "Stored local signing key does not match the active account"
    }
    return NostrEventSigner.signEvent(
        kind = kind,
        content = json.optString("content"),
        tags = tags,
        pubkeyHex = pubkey,
        privKeyBytes = privateKey,
        createdAt = createdAt
    )
}

private fun String.hexToBytes(): ByteArray {
    val clean = trim().removePrefix("0x")
    require(clean.length % 2 == 0) { "Private key hex must have an even number of characters" }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
