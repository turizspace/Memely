package com.memely.workflow

import com.memely.nostr.NostrRepository
import com.memely.nostr.RelayManager
import com.memely.util.SecureLog
import kotlinx.coroutines.delay

/**
 * Validates relay connection status before posting.
 * Ensures users aren't attempting to post without connected relays.
 */
object RelayConnectionValidator {
    
    data class RelayStatus(
        val isConnected: Boolean,
        val connectedRelayCount: Int = 0,
        val totalRelayCount: Int = 0,
        val message: String = ""
    )
    
    /**
     * Check current relay connection status
     */
    fun getCurrentStatus(): RelayStatus {
        val connectedCount = NostrRepository.connectedRelaysFlow.value
        val totalRelays = RelayManager.effectiveRelays.value.size
        
        return RelayStatus(
            isConnected = connectedCount > 0,
            connectedRelayCount = connectedCount,
            totalRelayCount = totalRelays,
            message = when {
                connectedCount == 0 && totalRelays == 0 -> "No relays configured"
                connectedCount == 0 -> "Connecting to relays... ($totalRelays configured)"
                connectedCount < totalRelays -> "Connected to $connectedCount/$totalRelays relays"
                else -> "Connected to all $totalRelays relays"
            }
        )
    }
    
    /**
     * Wait for relay connections with timeout
     * Returns true if at least one relay is connected, false if timeout
     */
    suspend fun waitForRelayConnection(
        timeoutMs: Long = 10000L,
        minRelays: Int = 1
    ): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val status = getCurrentStatus()
            
            if (status.connectedRelayCount >= minRelays) {
                SecureLog.d("RelayConnectionValidator: Relay connection established - ${status.connectedRelayCount} relays connected")
                return true
            }
            
            // Wait a bit before checking again
            delay(500)
        }
        
        SecureLog.w("RelayConnectionValidator: Relay connection timeout - only ${getCurrentStatus().connectedRelayCount} relays connected")
        return false
    }
    
    /**
     * Check if user should wait for relay connection or proceed anyway
     */
    fun shouldProceed(): Boolean {
        val status = getCurrentStatus()
        return status.isConnected
    }
    
    /**
     * Get user-friendly message about relay status
     */
    fun getStatusMessage(): String {
        return getCurrentStatus().message
    }
}
