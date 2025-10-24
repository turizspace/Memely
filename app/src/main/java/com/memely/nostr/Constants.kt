package com.memely.nostr

object Constants {
    // Default relays to bootstrap metadata and NIP-65 relay discovery
    val DEFAULT_RELAYS = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.snort.social",
        "wss://nostr.wine",
        "wss://eden.nostr.land"
    )

    const val KIND_METADATA = 0
    const val KIND_RELAY_LIST = 10002
    
    // Meme Templates API
    const val MEME_TEMPLATES_API = "https://turiz.space/memetemplates/index.php"
}
