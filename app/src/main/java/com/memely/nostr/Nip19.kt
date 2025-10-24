package com.memely.nostr

import org.bitcoinj.core.Bech32
import java.util.Locale

object Nip19 {

    /** Decode npub/nsec -> payload bytes (handles 0x00 prefix) */
    @Throws(IllegalArgumentException::class)
    fun decodeToBytes(bech32: String): ByteArray {
        try {
            val data = Bech32.decode(bech32.lowercase(Locale.ROOT))
            val bytes = convertBits(data.data, 5, 8, false)
            // NIP-19 payloads for npub/nsec are normally 32 bytes.
            // Some encoders include a leading 0x00 version byte -> 33 bytes.
            return when {
                bytes.size == 33 && bytes[0].toInt() == 0 -> bytes.copyOfRange(1, bytes.size)
                else -> bytes
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid bech32: ${e.message}", e)
        }
    }

    /** Convenience: decode and return hex string */
    fun decodeToHex(bech32: String): String = decodeToBytes(bech32).toHex()

    /** Encode hex -> bech32 (npub/nsec) */
    fun encode(hrp: String, hex: String): String {
        val bytes = hex.hexToBytes()
        val converted = convertBits(bytes, 8, 5, true)
        return Bech32.encode(hrp, converted)
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val ret = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            val value = b.toInt() and 0xFF
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                ret.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad && bits > 0) {
            ret.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else if (!pad && (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0)) {
            throw IllegalArgumentException("Could not convert bits properly")
        }
        return ret.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        require(len % 2 == 0) { "Hex string must have even length" }
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((this[i].digitToInt(16) shl 4) + this[i + 1].digitToInt(16)).toByte()
        }
        return data
    }
}
