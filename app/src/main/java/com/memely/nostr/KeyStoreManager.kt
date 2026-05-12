package com.memely.nostr

import android.content.Context
import com.memely.util.SecureLog
import com.memely.util.SecureStorage
import java.util.Locale

/**
 * Secure key storage manager using EncryptedSharedPreferences.
 * All private keys are stored encrypted at rest.
 */
object KeyStoreManager {
    private lateinit var secureStorage: SecureStorage

    private fun storageOrNull(): SecureStorage? {
        if (::secureStorage.isInitialized) {
            return secureStorage
        }

        return runCatching { SecureStorage.getInstance() }
            .onSuccess { secureStorage = it }
            .getOrNull()
    }

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
        val storage = requireNotNull(storageOrNull()) {
            "KeyStoreManager is not initialized"
        }
        if (privHex != null) {
            storage.putString("priv_hex", privHex)
        }
        if (pubHex != null) {
            storage.putString("pub_hex", pubHex)
        }
    }

    fun saveExternalPubkey(pubkeyInput: String) {
        val storage = requireNotNull(storageOrNull()) {
            "KeyStoreManager is not initialized"
        }
        val pubHex = if (pubkeyInput.lowercase(Locale.ROOT).startsWith("npub")) {
            // It's a bech32 npub, decode it
            Nip19.decodeToHex(pubkeyInput)
        } else {
            // It's already hex, use it directly
            pubkeyInput
        }
        storage.putString("external_pubkey", pubHex)
        SecureLog.d("KeyStore: Saved external pubkey ${SecureLog.truncateHex(pubHex)}")
    }

    fun saveAmberPackageName(packageName: String) {
        storageOrNull()?.putString("amber_package", packageName)
        // Note: Logging removed for security
    }

    fun getAmberPackageName(): String? {
        return storageOrNull()?.getString("amber_package")
    }

    fun getPubkeyHex(): String? {
        val storage = storageOrNull() ?: return inMemoryPubHex
        val pubHex = storage.getString("pub_hex")
        val externalPubkey = storage.getString("external_pubkey")
        val inMemory = inMemoryPubHex
        
        val source = when {
            pubHex != null -> "pub_hex"
            externalPubkey != null -> "external_pubkey"
            inMemory != null -> "inMemory"
            else -> "NONE"
        }
        val result = pubHex ?: externalPubkey ?: inMemory
        SecureLog.d("KeyStore: getPubkeyHex() source=$source pubkey=${result?.let { SecureLog.truncateHex(it) } ?: "null"}")
        
        return result
    }

    fun exportNpubBech32(): String? = getPubkeyHex()?.let { Nip19.encode("npub", it) }

    fun exportNsecHex(): String? = inMemoryPrivHex ?: storageOrNull()?.getString("priv_hex")
    fun exportNsecBech32(): String? = exportNsecHex()?.let { Nip19.encode("nsec", it) }

    /**
     * Check if user is authenticated via Amber (external signer).
     * Returns true if we have a pubkey but no private key.
     */
    fun isUsingAmber(): Boolean {
        val hasExternal = storageOrNull()?.getString("external_pubkey") != null
        val hasPrivKey = exportNsecHex() != null
        return hasExternal && !hasPrivKey
    }

    fun clear() {
        storageOrNull()?.remove("priv_hex")
        storageOrNull()?.remove("pub_hex")
        storageOrNull()?.remove("external_pubkey")
        storageOrNull()?.remove("amber_package")
        inMemoryPrivHex = null
        inMemoryPubHex = null
    }

    fun hasKey(): Boolean = getPubkeyHex() != null
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
