package com.memely.nostr

import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * AmberSignerManager — handles communication with external Nostr signer apps (Amber/Quartz).
 * Supports getting pubkey and signing events (NIP-46 compatible).
 */
object AmberSignerManager {
    private var externalPubkey: String? = null
    private var externalPackage: String? = null
    private var launcher: ((Intent) -> Unit)? = null

    // Keep public for coroutine waiting
    val pending = ConcurrentHashMap<String, CompletableDeferred<IntentResult>>()

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
            val id = intent.getStringExtra("id")
            val result = intent.getStringExtra("result")
            val event = intent.getStringExtra("event")
            val pkg = intent.getStringExtra("package")
            val res = IntentResult(id, result, event, pkg)

            if (!id.isNullOrBlank()) {
                pending.remove(id)?.complete(res)
            }
        } catch (e: Exception) {
            // Ignore malformed responses
        }
    }

    fun configure(pubkeyHex: String, packageName: String) {
        externalPubkey = pubkeyHex
        externalPackage = packageName
    }

    fun getConfiguredPubkey(): String? = externalPubkey
    fun getConfiguredPackage(): String? = externalPackage

    suspend fun requestPublicKey(timeoutMs: Long = 60_000): IntentResult {
        val pkg = "com.greenart7c3.nostrsigner" // Amber package
        val l = launcher ?: throw IllegalStateException("No launcher registered")

        val callId = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        val deferred = CompletableDeferred<IntentResult>()
        pending[callId] = deferred

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
        }
    }

    suspend fun signEvent(eventJson: String, eventId: String, timeoutMs: Long = 60_000): IntentResult {
        val pkg = externalPackage ?: throw IllegalStateException("Amber package not set")
        val pub = externalPubkey ?: throw IllegalStateException("Amber pubkey not set")
        val l = launcher ?: throw IllegalStateException("No launcher registered")

        val callId = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        val deferred = CompletableDeferred<IntentResult>()
        pending[callId] = deferred

        println("📤 AmberSignerManager: Sending event to Amber")
        println("   Event JSON: $eventJson")
        println("   Event ID: $eventId")
        println("   Call ID: $callId")
        println("   Package: $pkg")
        println("   Current user: $pub")

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

        println("📱 Launching Amber with intent URI: ${intent.data}")
        println("   Intent action: ${intent.action}")
        println("   Intent package: ${intent.`package`}")
        println("   Intent extras: type=${intent.getStringExtra("type")}, id=$callId, current_user=$pub")
        println("   Intent flags: ${intent.flags}")
        println("   Pending requests count: ${pending.size}")

        l(intent)

        return try {
            println("⏳ AmberSignerManager: Waiting for response (timeout: ${timeoutMs}ms)...")
            val response = withTimeout(timeoutMs) { deferred.await() }
            println("✅ AmberSignerManager: Received response - event: ${response.event?.take(50)}")
            response
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            println("❌ AmberSignerManager: Timeout waiting for response")
            println("   Pending requests still waiting: ${pending.keys.joinToString()}")
            throw e
        } finally {
            pending.remove(callId)
        }
    }
}
