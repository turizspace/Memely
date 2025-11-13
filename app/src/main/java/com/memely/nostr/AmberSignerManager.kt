package com.memely.nostr

import android.content.Intent
import com.memely.util.SecureLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * AmberSignerManager — handles communication with external Nostr signer apps (Amber/Quartz).
 * Supports getting pubkey and signing events (NIP-46 compatible).
 * 
 * Security: All sensitive data logging has been removed or sanitized.
 */
object AmberSignerManager {
    private var externalPubkey: String? = null
    private var externalPackage: String? = null
    private var launcher: ((Intent) -> Unit)? = null

    // Keep public for coroutine waiting
    val pending = ConcurrentHashMap<String, CompletableDeferred<IntentResult>>()
    
    // Track request types to distinguish login from signing
    private val requestTypes = ConcurrentHashMap<String, String>()

    data class IntentResult(
        val id: String? = null,
        val result: String? = null,
        val event: String? = null,
        val packageName: String? = null,
    )

    fun registerActivityLauncher(l: (Intent) -> Unit) {
        launcher = l
    }

    fun unregisterActivityLauncher(l: (Intent) -> Unit) {
        if (launcher == l) launcher = null
    }

    fun handleIntentResponse(intent: Intent) {
        try {
            // Security: Validate intent extras before processing
            val id = intent.getStringExtra("id")
            val result = intent.getStringExtra("result")
            val event = intent.getStringExtra("event")
            val pkg = intent.getStringExtra("package")
            
            println("📨 AmberSignerManager: Received intent response - id=${id?.take(8)}, hasResult=${!result.isNullOrBlank()}, hasEvent=${!event.isNullOrBlank()}, pkg=$pkg")
            
            // Validate ID exists and is expected
            if (id.isNullOrBlank()) {
                SecureLog.w("Received intent with no ID - ignoring")
                return
            }
            
            // Validate result format if present
            if (result != null && result.length > 10000) {
                SecureLog.w("Received intent with suspiciously large result - ignoring")
                return
            }
            
            // Validate event JSON structure if present
            if (event != null) {
                try {
                    org.json.JSONObject(event) // Validate JSON structure
                    if (event.length > 100000) { // Max reasonable event size
                        SecureLog.w("Received intent with suspiciously large event - ignoring")
                        return
                    }
                } catch (e: Exception) {
                    SecureLog.w("Received intent with invalid event JSON - ignoring")
                    return
                }
            }
            
            val res = IntentResult(id, result, event, pkg)

            if (!id.isNullOrBlank()) {
                val deferred = pending.remove(id)
                if (deferred != null) {
                    deferred.complete(res)
                    println("✅ Completed pending request: ${id.take(8)}")
                } else {
                    println("⚠️ Received response for unknown request ID: ${id.take(8)}")
                }
            }
        } catch (e: Exception) {
            println("❌ Error handling intent response: ${e.message}")
        }
    }

    fun configure(pubkeyHex: String, packageName: String) {
        externalPubkey = pubkeyHex
        externalPackage = packageName
        println("🔧 AmberSignerManager: Configured with pubkey=${pubkeyHex.take(8)}..., package=$packageName")
    }

    fun getConfiguredPubkey(): String? = externalPubkey
    fun getConfiguredPackage(): String? = externalPackage
    
    /**
     * Check if a request ID was for login (get_public_key)
     */
    fun isLoginRequest(requestId: String): Boolean {
        return requestTypes[requestId] == "get_public_key"
    }

    suspend fun requestPublicKey(timeoutMs: Long = 60_000): IntentResult {
        val pkg = "com.greenart7c3.nostrsigner" // Amber package
        val l = launcher ?: throw IllegalStateException("No launcher registered")

        val callId = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        val deferred = CompletableDeferred<IntentResult>()
        pending[callId] = deferred
        requestTypes[callId] = "get_public_key"  // Mark as login request

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("nostrsigner:")).apply {
            putExtra("type", "get_public_key")
            putExtra("permissions", "[]")
            `package` = pkg
            putExtra("id", callId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        l(intent)

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(callId)
            requestTypes.remove(callId)  // Clean up
        }
    }

    suspend fun signEvent(eventJson: String, eventId: String, timeoutMs: Long = 60_000): IntentResult {
        val pkg = externalPackage ?: throw IllegalStateException("Amber package not set")
        val pub = externalPubkey ?: throw IllegalStateException("Amber pubkey not set")
        val l = launcher ?: throw IllegalStateException("No launcher registered")

        println("🔏 AmberSignerManager.signEvent() called - configuredPubkey=${pub.take(8)}..., eventId=${eventId.take(8)}...")
        
        // Parse event JSON to check the pubkey in the event
        try {
            val eventObj = org.json.JSONObject(eventJson)
            val eventPubkey = eventObj.optString("pubkey", "")
            println("📋 Event pubkey in JSON: ${eventPubkey.take(8)}... (matches configured: ${eventPubkey == pub})")
            if (eventPubkey != pub) {
                println("⚠️⚠️⚠️ PUBKEY MISMATCH DETECTED! Event: ${eventPubkey.take(8)}, Configured: ${pub.take(8)}")
            }
        } catch (e: Exception) {
            println("⚠️ Could not parse event JSON to check pubkey: ${e.message}")
        }

        val callId = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        val deferred = CompletableDeferred<IntentResult>()
        pending[callId] = deferred
        requestTypes[callId] = "sign_event"  // Mark as signing request

        println("📤 Sending event to external signer (ID: ${eventId.take(8)}...)")

        // Attach unsigned event JSON directly in the URI per NIP-55 and mirror amber login flow
        val uri = android.net.Uri.parse("nostrsigner:$eventJson")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            `package` = pkg
            putExtra("type", "sign_event")
            putExtra("current_user", pub)
            putExtra("id", callId)
            putExtra("event", eventJson) // some signers expect payload in extras as well
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        println("🚀 Launching external signer for event signing")

        l(intent)

        return try {
            println("⏳ Waiting for signer response (timeout: ${timeoutMs}ms)")
            val response = withTimeout(timeoutMs) { deferred.await() }
            println("✅ Received response from signer")
            response
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            println("⏱️ Timeout waiting for signer response")
            throw e
        } finally {
            pending.remove(callId)
            requestTypes.remove(callId)  // Clean up
        }
    }
}
