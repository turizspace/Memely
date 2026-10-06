package com.memely.blossom

import android.util.Base64
import com.memely.network.SecureHttpClient
import com.memely.nostr.NostrEventSigner
import com.memely.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Read-only client for the authenticated Blossom file-list endpoint. */
class BlossomListingClient {
    data class BlobDescriptor(
        val sha256: String,
        val url: String,
        val mimeType: String,
        val size: Long,
        val uploadedAt: Long? = null
    )

    private val http = SecureHttpClient.createDownloadClient()

    suspend fun listFiles(
        serverUrl: String,
        pubkey: String,
        signEvent: suspend (String) -> String
    ): List<BlobDescriptor> {
        require(pubkey.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid public key" }
        val normalizedPubkey = pubkey.lowercase()
        val base = serverUrl.trimEnd('/')
        require(base.toHttpUrlOrNull()?.isHttps == true) { "Blossom server must use HTTPS" }
        SecureLog.d(
            "BlossomListingClient: Preparing authenticated GET $base/list/${normalizedPubkey.take(8)}…"
        )
        val now = System.currentTimeMillis() / 1000L

        val unsignedEvent = JSONObject().apply {
            put("kind", 24242)
            put("created_at", now)
            put("pubkey", normalizedPubkey)
            put("content", "")
            put("tags", JSONArray().put(JSONArray(listOf("t", "list"))).put(
                JSONArray(listOf("expiration", (now + 300L).toString()))
            ))
        }.toString()
        SecureLog.d("BlossomListingClient: Requesting kind-24242 list authorization signature")
        val signedEvent = signEvent(unsignedEvent)
        val signed = JSONObject(signedEvent)
        require(signed.optInt("kind") == 24242) { "Signer returned an event with the wrong kind" }
        require(signed.optString("pubkey").equals(normalizedPubkey, ignoreCase = true)) {
            "Signer returned an event for a different public key"
        }
        require(signed.optString("sig").matches(Regex("[0-9a-fA-F]{128}"))) {
            "Signer returned an event without a valid signature shape"
        }
        val signedId = signed.optString("id")
        require(signedId.matches(Regex("[0-9a-fA-F]{64}")) &&
            signedId.equals(NostrEventSigner.calculateEventId(signedEvent), ignoreCase = true)
        ) {
            "Signer returned an event with an invalid ID"
        }
        val signedTags = signed.optJSONArray("tags")
            ?: throw IllegalArgumentException("Signer returned an event without authorization tags")
        val hasListPermission = (0 until signedTags.length()).any { index ->
            val tag = signedTags.optJSONArray(index)
            tag != null && tag.length() >= 2 && tag.optString(0) == "t" && tag.optString(1) == "list"
        }
        require(hasListPermission) { "Signer returned an event without list permission" }
        val expiry = (0 until signedTags.length()).mapNotNull { index ->
            val tag = signedTags.optJSONArray(index)
            if (tag != null && tag.length() >= 2 && tag.optString(0) == "expiration") {
                tag.optString(1).toLongOrNull()
            } else null
        }.minOrNull()
        require(expiry != null && expiry > System.currentTimeMillis() / 1000L) {
            "Signer returned an expired or non-expiring authorization"
        }
        SecureLog.d("BlossomListingClient: Authorization signed for ${normalizedPubkey.take(8)}")
        val authorization = "Nostr " + Base64.encodeToString(
            signedEvent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )
        val request = Request.Builder()
            .url("$base/list/$normalizedPubkey")
            .header("Authorization", authorization)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                SecureLog.i(
                    "BlossomListingClient: GET /list/${normalizedPubkey.take(8)}… returned HTTP ${response.code}"
                )
                if (!response.isSuccessful) {
                    throw IllegalStateException("Blossom server returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val descriptors = parseDescriptors(body)
                SecureLog.i(
                    "BlossomListingClient: Parsed ${descriptors.size} valid unique descriptors " +
                        "from ${body.length} response characters"
                )
                descriptors
            }
        }
    }

    private fun parseDescriptors(body: String): List<BlobDescriptor> {
        val array = when {
            body.trimStart().startsWith("[") -> JSONArray(body)
            else -> JSONObject(body).optJSONArray("files")
                ?: JSONObject(body).optJSONArray("blobs")
                ?: JSONObject(body).optJSONArray("data")
                ?: JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val hash = item.optString("sha256", item.optString("hash", "")).lowercase()
                val url = item.optString("url", "")
                val parsedUrl = url.toHttpUrlOrNull()
                if (!hash.matches(Regex("[0-9a-f]{64}")) || parsedUrl?.isHttps != true) continue
                add(
                    BlobDescriptor(
                        sha256 = hash,
                        url = url,
                        mimeType = item.optString("type", item.optString("mime_type", "application/octet-stream")),
                        size = item.optLong("size", 0L).coerceAtLeast(0L),
                        uploadedAt = item.takeIf { it.has("uploaded") }?.optLong("uploaded")
                    )
                )
            }
        }.distinctBy { it.sha256 }
    }
}
