package com.memely.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memely.data.repositories.ProfileRepository
import com.memely.data.repositories.SessionRepository
import com.memely.nostr.MetadataParser
import com.memely.util.SecureLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthenticatedRootUiState(
    val pubkeyHex: String? = null,
    val connectedRelays: Int = 0,
    val totalRelays: Int = 0,
    val userMetadata: MetadataParser.UserMetadata? = null
)

class AuthenticatedRootViewModel(
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {
    private var lastStartedPubkey: String? = null
    private var lastFetchedPubkey: String? = null
    private var lastFetchedRelayCount: Int = -1

    val uiState: StateFlow<AuthenticatedRootUiState> = combine(
        sessionRepository.sessionState,
        profileRepository.connectedRelays,
        profileRepository.effectiveRelays,
        profileRepository.userMetadata
    ) { sessionState, connectedRelays, effectiveRelays, userMetadata ->
        AuthenticatedRootUiState(
            pubkeyHex = sessionState.pubkeyHex,
            connectedRelays = connectedRelays,
            totalRelays = effectiveRelays.size,
            userMetadata = userMetadata
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthenticatedRootUiState(
            pubkeyHex = sessionRepository.sessionState.value.pubkeyHex,
            connectedRelays = profileRepository.connectedRelays.value,
            totalRelays = profileRepository.effectiveRelays.value.size,
            userMetadata = profileRepository.userMetadata.value
        )
    )

    fun startSession(pubkeyHex: String?) {
        if (pubkeyHex.isNullOrBlank()) {
            lastStartedPubkey = null
            lastFetchedPubkey = null
            lastFetchedRelayCount = -1
            return
        }

        if (lastStartedPubkey == pubkeyHex) {
            return
        }

        lastStartedPubkey = pubkeyHex
        viewModelScope.launch {
            SecureLog.d("AuthenticatedRootViewModel: Starting session for ${SecureLog.truncateHex(pubkeyHex)}")
            profileRepository.connectAll()
            profileRepository.startProfileListener(pubkeyHex)
        }
    }

    fun refreshProfileIfNeeded(pubkeyHex: String?, connectedRelays: Int) {
        if (pubkeyHex.isNullOrBlank() || connectedRelays <= 0) {
            return
        }

        if (lastFetchedPubkey == pubkeyHex && lastFetchedRelayCount == connectedRelays) {
            return
        }

        lastFetchedPubkey = pubkeyHex
        lastFetchedRelayCount = connectedRelays

        viewModelScope.launch {
            SecureLog.d(
                "AuthenticatedRootViewModel: Refreshing profile for ${SecureLog.truncateHex(pubkeyHex)} " +
                    "with $connectedRelays relays"
            )
            profileRepository.fetchUserProfile(pubkeyHex)
        }
    }
}
