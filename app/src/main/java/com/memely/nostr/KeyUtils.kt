package com.memely.nostr

import org.bitcoinj.core.ECKey
import java.math.BigInteger
import java.security.SecureRandom

object KeyUtils {

    /**
     * Takes a 32-byte private key and returns the 32-byte x-only public key as a 64-hex string.
     * Pads/trims the x coordinate to 32 bytes as needed.
     */
    fun publicKeyXOnlyHexFromPrivate(privateKey: ByteArray): String {
        require(privateKey.size == 32) { "privateKey must be 32 bytes" }

        // Create ECKey from private bytes (bitcoinj)
        val privBigInt = BigInteger(1, privateKey)
        val ecKey = ECKey.fromPrivate(privBigInt, false) // false => uncompressed point access

        // Get raw EC point and extract x coordinate (as 32 bytes)
        val point = ecKey.pubKeyPoint.normalize()
        val xByteArray = point.xCoord.encoded // big-endian

        // Ensure exactly 32 bytes (pad left with zeros if necessary)
        val x32 = ByteArray(32)
        val srcStart = if (xByteArray.size > 32) xByteArray.size - 32 else 0
        val srcLen = xByteArray.size - srcStart
        System.arraycopy(xByteArray, srcStart, x32, 32 - srcLen, srcLen)

        return x32.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
