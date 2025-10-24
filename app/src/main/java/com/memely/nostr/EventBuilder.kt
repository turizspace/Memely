package com.memely.nostr

import com.memely.data.models.NostrEvent

object EventBuilder {
    fun buildKind1(content: String, tags: List<List<String>> = emptyList()): NostrEvent {
        return NostrEvent(
            id = "",
            pubkey = "",
            created_at = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = tags,
            content = content,
            sig = ""
        )
    }
}
