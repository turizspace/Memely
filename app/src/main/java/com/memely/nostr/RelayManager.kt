package com.memely.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RelayManager {
    // Fallback relays in case user has no NIP-65 data
    val FALLBACK_RELAYS = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol", 
        "wss://relay.snort.social",
        "wss://nostr.wine",
        "wss://eden.nostr.land"
    )

    // Store user's preferred relays
    private val _userRelays = MutableStateFlow<List<String>>(emptyList())
    val userRelays: StateFlow<List<String>> = _userRelays

    // Store effective relays in a state flow for reactivity
    private val _effectiveRelays = MutableStateFlow<List<String>>(FALLBACK_RELAYS)
    val effectiveRelays: StateFlow<List<String>> = _effectiveRelays

    init {
        // Initialize effective relays
        updateEffectiveRelays()
    }

    // Get effective relays count for easy access
    val effectiveRelaysCount: Int
        get() = _effectiveRelays.value.size

    val userRelaysCount: Int
        get() = _userRelays.value.size

    private fun updateEffectiveRelays() {
        val newEffectiveRelays = if (_userRelays.value.isNotEmpty()) {
            _userRelays.value
        } else {
            FALLBACK_RELAYS
        }
        _effectiveRelays.value = newEffectiveRelays
        println("🌐 RelayManager: Effective relays updated to ${newEffectiveRelays.size} relays")
    }

    fun updateUserRelays(relays: List<String>) {
        val validRelays = relays.distinct().filter { it.startsWith("wss://") || it.startsWith("ws://") }
        _userRelays.value = validRelays
        updateEffectiveRelays()
        println("🔄 RelayManager: Updated user relays to ${validRelays.size} relays: ${validRelays.take(3)}...")
    }

    fun clearUserRelays() {
        _userRelays.value = emptyList()
        updateEffectiveRelays()
        println("🗑️ RelayManager: Cleared user relays")
    }
    
    fun hasUserRelays(): Boolean {
        return _userRelays.value.isNotEmpty()
    }
}