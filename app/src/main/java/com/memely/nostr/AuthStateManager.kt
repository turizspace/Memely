package com.memely.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AuthStateManager {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    fun refresh() {
        _isLoggedIn.value = com.memely.nostr.KeyStoreManager.hasKey()
    }

    init {
        refresh()
    }
}
