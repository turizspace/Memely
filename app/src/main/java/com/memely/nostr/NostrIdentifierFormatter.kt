package com.memely.nostr

import java.util.Locale

object NostrIdentifierFormatter {
    private val hexRegex = Regex("^[0-9a-f]{64}$")

    fun toNpub(pubkeyHex: String): String = encodeOrFallback("npub", pubkeyHex)

    fun toNoteId(eventIdHex: String): String = encodeOrFallback("note", eventIdHex)

    private fun encodeOrFallback(hrp: String, hex: String): String {
        val normalizedHex = hex.trim().lowercase(Locale.ROOT)
        if (!hexRegex.matches(normalizedHex)) {
            return hex
        }

        return runCatching {
            Nip19.encode(hrp, normalizedHex)
        }.getOrDefault(hex)
    }
}
