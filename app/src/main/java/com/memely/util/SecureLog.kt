package com.memely.util

import android.util.Log

/**
 * Secure logging utility for Memely.
 * 
 * In DEBUG builds: Logs are enabled but sensitive data is sanitized.
 * In RELEASE builds: All logging is removed by ProGuard/R8.
 * 
 * NEVER log:
 * - Private keys (nsec, priv_hex)
 * - Signatures
 * - Full event IDs (truncate to first 8 chars)
 * - Full pubkeys (truncate to first 8 chars)
 * - Authentication tokens
 * - User passwords
 */
object SecureLog {
    private const val TAG = "Memely"
    
    // Check if BuildConfig.DEBUG is available, default to false for safety
    private val isDebug: Boolean by lazy {
        try {
            Class.forName("com.memely.BuildConfig").getDeclaredField("DEBUG").getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    fun d(message: String) {
        if (isDebug) {
            Log.d(TAG, sanitize(message))
        }
    }

    fun i(message: String) {
        if (isDebug) {
            Log.i(TAG, sanitize(message))
        }
    }

    fun w(message: String) {
        if (isDebug) {
            Log.w(TAG, sanitize(message))
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isDebug) {
            if (throwable != null) {
                Log.e(TAG, sanitize(message), throwable)
            } else {
                Log.e(TAG, sanitize(message))
            }
        }
    }

    /**
     * Sanitize log messages to prevent leaking sensitive data.
     * This is a last line of defense - ideally, sensitive data should never be logged.
     */
    private fun sanitize(message: String): String {
        var sanitized = message
        
        // Remove patterns that look like private keys (64 hex chars after nsec markers)
        sanitized = sanitized.replace(Regex("nsec[0-9a-z]{59,}"), "[REDACTED_NSEC]")
        sanitized = sanitized.replace(Regex("priv[_\\s]*hex[_\\s]*[=:][_\\s]*[0-9a-f]{64}"), "[REDACTED_PRIVATE_KEY]")
        
        // Remove patterns that look like signatures (128 hex chars)
        sanitized = sanitized.replace(Regex("sig[_\\s]*[=:][_\\s]*[0-9a-f]{128}"), "[REDACTED_SIGNATURE]")
        
        return sanitized
    }

    /**
     * Truncate hex strings for safe logging.
     * Use this for pubkeys and event IDs.
     */
    fun truncateHex(hex: String, length: Int = 8): String {
        return if (hex.length > length) hex.take(length) + "..." else hex
    }
}
