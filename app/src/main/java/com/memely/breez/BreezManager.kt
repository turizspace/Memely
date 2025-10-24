package com.memely.breez

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Breez SDK integration stub (nodeless). Replace with actual Breez SDK usage.
 */
object BreezManager {
    val balance = MutableStateFlow(0L)

    suspend fun initializeFor(pubkey: String) {
        // TODO: initialize breez nodeless account tied to the Nostr key/lud16
        balance.value = 0L
    }

    suspend fun sendZap(toLud16: String, amountMsat: Long): Boolean {
        // TODO: perform zap via Breez
        return true
    }
}
