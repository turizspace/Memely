package com.memely.data.repositories

import com.memely.nostr.AuthStateManager
import com.memely.nostr.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionState(
    val isLoggedIn: Boolean = false,
    val pubkeyHex: String? = null
)

interface SessionRepository {
    val sessionState: StateFlow<SessionState>

    fun refresh()

    fun logout()
}

class DefaultSessionRepository(
    private val appScope: CoroutineScope
) : SessionRepository {
    private val _sessionState = MutableStateFlow(currentSessionState())
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        appScope.launch {
            AuthStateManager.isLoggedIn.collect {
                _sessionState.value = currentSessionState()
            }
        }
    }

    override fun refresh() {
        AuthStateManager.refresh()
        _sessionState.value = currentSessionState()
    }

    override fun logout() {
        KeyStoreManager.clear()
        refresh()
    }

    private fun currentSessionState(): SessionState {
        val pubkeyHex = KeyStoreManager.getPubkeyHex()
        return SessionState(
            isLoggedIn = pubkeyHex != null,
            pubkeyHex = pubkeyHex
        )
    }
}
