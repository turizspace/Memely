package com.memely.data.repositories

import com.memely.nostr.MetadataParser
import com.memely.nostr.NostrRepository
import com.memely.nostr.RelayManager
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val connectedRelays: StateFlow<Int>
    val effectiveRelays: StateFlow<List<String>>
    val userMetadata: StateFlow<MetadataParser.UserMetadata?>

    suspend fun connectAll()

    fun startProfileListener(pubkey: String)

    suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>>
}

class DefaultProfileRepository : ProfileRepository {
    override val connectedRelays: StateFlow<Int> = NostrRepository.connectedRelaysFlow
    override val effectiveRelays: StateFlow<List<String>> = RelayManager.effectiveRelays
    override val userMetadata: StateFlow<MetadataParser.UserMetadata?> = NostrRepository.metadataState

    override suspend fun connectAll() {
        NostrRepository.connectAll()
    }

    override fun startProfileListener(pubkey: String) {
        NostrRepository.startProfileListener(pubkey)
    }

    override suspend fun fetchUserProfile(pubkey: String): Pair<MetadataParser.UserMetadata?, List<String>> {
        return NostrRepository.fetchUserProfile(pubkey)
    }
}
