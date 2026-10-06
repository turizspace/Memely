package com.memely.blossom

import android.util.Base64
import com.memely.network.SecureHttpClient
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
    ): List<BlobDescriptor> = withContext(Dispatchers.IO) {
        require(pubkey.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid public key" }
        val base = serverUrl.trimEnd('/')
        require(base.toHttpUrlOrNull()?.isHttps == true) { "Blossom server must use HTTPS" }

        val unsignedEvent = JSONObject().apply {
            put("kind", 24242)
            put("created_at", System.currentTimeMillis() / 1000L)
            put("pubkey", pubkey)
            put("content", "")
            put("tags", JSONArray().put(JSONArray(listOf("t", "list"))).put(
                JSONArray(listOf("expiration", ((System.currentTimeMillis() / 1000L) + 300L).toString()))
            ))
        }.toString()
        val signedEvent = signEvent(unsignedEvent)
        val authorization = "Nostr " + Base64.encodeToString(
            signedEvent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )
        val request = Request.Builder()
            .url("$base/list/$pubkey")
            .header("Authorization", authorization)
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Server returned HTTP ${response.code}")
            parseDescriptors(response.body?.string().orEmpty())
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
