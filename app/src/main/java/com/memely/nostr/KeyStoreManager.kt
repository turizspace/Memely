package com.memely.nostr

import android.content.Context
import com.memely.util.SecureStorage
import java.util.Locale

/**
 * Secure key storage manager using EncryptedSharedPreferences.
 * All private keys are stored encrypted at rest.
 */
object KeyStoreManager {
    private lateinit var secureStorage: SecureStorage

    fun init(context: Context) {
        SecureStorage.init(context)
        secureStorage = SecureStorage.getInstance()
    }

    private var inMemoryPrivHex: String? = null
    private var inMemoryPubHex: String? = null

    fun importNsec(nsec: String) {
        require(nsec.lowercase(Locale.ROOT).startsWith("nsec")) { "Invalid nsec prefix" }
        val privBytes = Nip19.decodeToBytes(nsec)
        require(privBytes.size == 32) { "Invalid private key length" }
        val pubHex = KeyUtils.publicKeyXOnlyHexFromPrivate(privBytes)
        inMemoryPrivHex = privBytes.toHex()
        inMemoryPubHex = pubHex
        saveKeysToSecureStorage(inMemoryPrivHex, inMemoryPubHex)
    }

    fun importNpub(npub: String) {
        require(npub.lowercase(Locale.ROOT).startsWith("npub")) { "Invalid npub prefix" }
        val pubHex = Nip19.decodeToHex(npub)
        inMemoryPubHex = pubHex
        saveKeysToSecureStorage(null, pubHex)
    }

    private fun saveKeysToSecureStorage(privHex: String?, pubHex: String?) {
        if (privHex != null) {
            secureStorage.putString("priv_hex", privHex)
        }
        if (pubHex != null) {
            secureStorage.putString("pub_hex", pubHex)
        }
    }

    fun saveExternalPubkey(pubkeyInput: String) {
        val pubHex = if (pubkeyInput.lowercase(Locale.ROOT).startsWith("npub")) {
            // It's a bech32 npub, decode it
            Nip19.decodeToHex(pubkeyInput)
        } else {
            // It's already hex, use it directly
            pubkeyInput
        }
        secureStorage.putString("external_pubkey", pubHex)
        // Note: Logging removed for security - pubkeys stored securely
    }

    fun saveAmberPackageName(packageName: String) {
        secureStorage.putString("amber_package", packageName)
        // Note: Logging removed for security
    }

    fun getAmberPackageName(): String? {
        return secureStorage.getString("amber_package")
    }

    fun getPubkeyHex(): String? =
        secureStorage.getString("pub_hex")
            ?: secureStorage.getString("external_pubkey")
            ?: inMemoryPubHex

    fun exportNpubBech32(): String? = getPubkeyHex()?.let { Nip19.encode("npub", it) }

    fun exportNsecHex(): String? = inMemoryPrivHex ?: secureStorage.getString("priv_hex")
    fun exportNsecBech32(): String? = exportNsecHex()?.let { Nip19.encode("nsec", it) }

    /**
     * Check if user is authenticated via Amber (external signer).
     * Returns true if we have a pubkey but no private key.
     */
    fun isUsingAmber(): Boolean {
        val hasExternal = secureStorage.getString("external_pubkey") != null
        val hasPrivKey = exportNsecHex() != null
        return hasExternal && !hasPrivKey
    }

    fun clear() {
        secureStorage.remove("priv_hex")
        secureStorage.remove("pub_hex")
        secureStorage.remove("external_pubkey")
        secureStorage.remove("amber_package")
        inMemoryPrivHex = null
        inMemoryPubHex = null
    }

    fun hasKey(): Boolean = getPubkeyHex() != null
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
