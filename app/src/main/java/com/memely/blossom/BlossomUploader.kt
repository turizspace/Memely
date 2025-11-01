package com.memely.blossom

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.memely.network.SecureHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Minimal Blossom client for authenticated media uploads using NIP-98 (Blossom Upload Descriptor).
 * Supports SHA-256 file hashing and signed authorization events.
 * 
 * Security: Uses SecureHttpClient for secure uploads.
 */
class BlossomClient(
    val baseUrl: String = BlossomConfig.baseUrl,
    private val http: OkHttpClient = SecureHttpClient.createDownloadClient()
) {

    data class UploadResult(val ok: Boolean, val statusCode: Int, val body: String?)

    private fun sha256Hex(bytes: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(bytes)
        return h.joinToString("") { "%02x".format(it) }
    }

    private fun sha256HexFromFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (true) {
                read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash from a content:// URI using ContentResolver.
     * Handles scoped storage correctly on Android Q+.
     */
    private fun sha256HexFromUri(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            var read: Int
            while (true) {
                read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Build a BUD-compliant authorization event (kind 24242) and return the raw JSON string.
     * This requires an external signer function that signs the event.
     */
    private suspend fun buildAuthEventJson(
        pubkey: String,
        signEventFunc: suspend (eventJson: String) -> String,
        verb: String,
        fileSha256Hex: String? = null,
        content: String = ""
    ): String {
        val kind = 24242
        val tags = mutableListOf<List<String>>()
        tags.add(listOf("t", verb))
        // expiration: 5 minutes from now
        val expiration = ((System.currentTimeMillis() / 1000L) + 300L).toString()
        tags.add(listOf("expiration", expiration))
        if (!fileSha256Hex.isNullOrBlank()) {
            tags.add(listOf("x", fileSha256Hex))
        }

        // Build unsigned event JSON
        val createdAt = System.currentTimeMillis() / 1000L
        val unsignedEvent = JSONObject().apply {
            put("kind", kind)
            put("created_at", createdAt)
            put("tags", JSONArray(tags.map { JSONArray(it) }))
            put("content", content)
            put("pubkey", pubkey)
        }

        // Sign the event using provided function
        return signEventFunc(unsignedEvent.toString())
    }

    // Base64-encode the event JSON and format as Authorization: Nostr <base64>
    private fun buildAuthorizationHeaderValue(eventJson: String): String {
        return "Nostr ${Base64.encodeToString(eventJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
    }

    /**
     * Upload a file using streaming RequestBody and report progress via [onProgress].
     * endpoint can be "upload", "media", or "mirror" depending on Blossom API.
     * signEventFunc should sign the provided event JSON and return the complete signed event.
     */
    suspend fun uploadFile(
        file: File,
        contentType: String,
        pubkeyHex: String,
        signEventFunc: suspend (eventJson: String) -> String,
        endpoint: String = "upload",
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        val totalBytes = file.length()
        val sha = sha256HexFromFile(file)
        
        val authEventJson = if (pubkeyHex.isNotBlank()) {
            buildAuthEventJson(
                pubkey = pubkeyHex,
                signEventFunc = signEventFunc,
                verb = "upload",
                fileSha256Hex = sha,
                content = "Upload ${file.name}"
            )
        } else ""
        
        val authHeaderValue = if (authEventJson.isNotBlank()) {
            buildAuthorizationHeaderValue(authEventJson)
        } else ""

        val url = "$baseUrl/${endpoint.trimStart('/')}"

        val mediaType = contentType.toMediaTypeOrNull() 
            ?: "application/octet-stream".toMediaTypeOrNull()

        val requestBody = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = totalBytes

            override fun writeTo(sink: BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var sent = 0L
                    while (true) {
                        read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        sent += read
                        onProgress?.invoke(sent, totalBytes)
                    }
                }
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .put(requestBody)
            .addHeader("Content-Type", contentType)
            
        if (authHeaderValue.isNotBlank()) {
            // Primary BUD header
            reqBuilder.addHeader("Authorization", authHeaderValue)
            // Compatibility headers
            reqBuilder.addHeader("Blossom-Authorization", authHeaderValue)
            reqBuilder.addHeader("BlossomAuthorization", authHeaderValue)
        }
        
        val req = reqBuilder.build()

        try {
            http.newCall(req).execute().use { resp ->
                val b = resp.body?.string()
                return@withContext UploadResult(resp.isSuccessful, resp.code, b)
            }
        } catch (e: Exception) {
            return@withContext UploadResult(false, 0, e.message)
        }
    }

    /**
     * Upload from a content:// URI using streaming RequestBody and report progress via [onProgress].
     * Handles scoped storage correctly on Android Q+.
     * endpoint can be "upload", "media", or "mirror" depending on Blossom API.
     * signEventFunc should sign the provided event JSON and return the complete signed event.
     */
    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        contentType: String,
        pubkeyHex: String,
        signEventFunc: suspend (eventJson: String) -> String,
        endpoint: String = "upload",
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        // Get file size from ContentResolver
        val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            fd.statSize
        } ?: throw IllegalArgumentException("Cannot determine file size for URI: $uri")
        
        val sha = sha256HexFromUri(context, uri)
        
        // Get filename from URI if available
        val filename = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else null
        } ?: "upload"
        
        val authEventJson = if (pubkeyHex.isNotBlank()) {
            buildAuthEventJson(
                pubkey = pubkeyHex,
                signEventFunc = signEventFunc,
                verb = "upload",
                fileSha256Hex = sha,
                content = "Upload $filename"
            )
        } else ""
        
        val authHeaderValue = if (authEventJson.isNotBlank()) {
            buildAuthorizationHeaderValue(authEventJson)
        } else ""

        val url = "$baseUrl/${endpoint.trimStart('/')}"

        val mediaType = contentType.toMediaTypeOrNull() 
            ?: "application/octet-stream".toMediaTypeOrNull()

        val requestBody = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = totalBytes

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var sent = 0L
                    while (true) {
                        read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        sent += read
                        onProgress?.invoke(sent, totalBytes)
                    }
                } ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
            }
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .put(requestBody)
            .addHeader("Content-Type", contentType)
            
        if (authHeaderValue.isNotBlank()) {
            // Primary BUD header
            reqBuilder.addHeader("Authorization", authHeaderValue)
            // Compatibility headers
            reqBuilder.addHeader("Blossom-Authorization", authHeaderValue)
            reqBuilder.addHeader("BlossomAuthorization", authHeaderValue)
        }
        
        val req = reqBuilder.build()

        try {
            http.newCall(req).execute().use { resp ->
                val b = resp.body?.string()
                return@withContext UploadResult(resp.isSuccessful, resp.code, b)
            }
        } catch (e: Exception) {
            return@withContext UploadResult(false, 0, e.message)
        }
    }

    /**
     * HEAD /<sha> to check existence. Returns pair(ok, statusCode)
     */
    suspend fun headFile(shaHex: String, endpointPrefix: String = ""): Pair<Boolean, Int> = 
        withContext(Dispatchers.IO) {
            val url = if (endpointPrefix.isBlank()) {
                "$baseUrl/$shaHex"
            } else {
                "$baseUrl/${endpointPrefix.trimStart('/')}/$shaHex"
            }
            val req = Request.Builder().url(url).head().build()
            try {
                http.newCall(req).execute().use { resp ->
                    return@withContext Pair(resp.isSuccessful, resp.code)
                }
            } catch (e: Exception) {
                return@withContext Pair(false, 0)
            }
        }

    /**
     * Parse blossom upload response body and prefer NIP-94 nested url, fallback to top-level url.
     */
    fun parseUploadUrl(responseBody: String?): String? {
        if (responseBody.isNullOrBlank()) return null
        try {
            val root = JSONObject(responseBody)
            if (root.has("nip94")) {
                val nip94 = root.getJSONArray("nip94")
                for (i in 0 until nip94.length()) {
                    val entry = nip94.get(i)
                    if (entry is JSONArray && entry.length() >= 2) {
                        val key = entry.getString(0)
                        if (key == "url") return entry.getString(1)
                    }
                }
            }
            if (root.has("url")) return root.getString("url")
        } catch (e: Exception) {
            // ignore
        }
        // Last-ditch regex fallback
        try {
            val regex = Regex("\\[\\s*\"url\"\\s*,\\s*\"([^\"]+)\"")
            val m = regex.find(responseBody)
            if (m != null && m.groups.size > 1) return m.groups[1]!!.value
        } catch (e: Exception) {}
        return null
    }
}
