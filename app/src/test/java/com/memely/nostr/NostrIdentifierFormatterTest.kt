package com.memely.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrIdentifierFormatterTest {
    private val sampleHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `toNpub encodes valid hex and round-trips through Nip19`() {
        val encoded = NostrIdentifierFormatter.toNpub(sampleHex)

        assertTrue(encoded.startsWith("npub1"))
        assertEquals(sampleHex, Nip19.decodeToHex(encoded))
    }

    @Test
    fun `toNoteId encodes valid hex and round-trips through Nip19`() {
        val encoded = NostrIdentifierFormatter.toNoteId(sampleHex)

        assertTrue(encoded.startsWith("note1"))
        assertEquals(sampleHex, Nip19.decodeToHex(encoded))
    }

    @Test
    fun `formatter returns original value for invalid hex input`() {
        val invalid = "not-a-valid-hex-id"

        assertEquals(invalid, NostrIdentifierFormatter.toNpub(invalid))
        assertEquals(invalid, NostrIdentifierFormatter.toNoteId(invalid))
    }

    @Test
    fun `formatter normalizes uppercase hex before encoding`() {
        val uppercase = sampleHex.uppercase()

        assertEquals(sampleHex, Nip19.decodeToHex(NostrIdentifierFormatter.toNpub(uppercase)))
    }
}
