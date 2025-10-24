package com.memely.nostr

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object KeyStoreManager {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("memely_keystore", Context.MODE_PRIVATE)
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
        saveKeysToPrefs(inMemoryPrivHex, inMemoryPubHex)
    }

    fun importNpub(npub: String) {
        require(npub.lowercase(Locale.ROOT).startsWith("npub")) { "Invalid npub prefix" }
        val pubHex = Nip19.decodeToHex(npub)
        inMemoryPubHex = pubHex
        saveKeysToPrefs(null, pubHex)
    }

    private fun saveKeysToPrefs(privHex: String?, pubHex: String?) {
        prefs.edit().apply {
            if (privHex != null) putString("priv_hex", privHex)
            if (pubHex != null) putString("pub_hex", pubHex)
        }.apply()
    }

    fun saveExternalPubkey(npub: String) {
        val pubHex = Nip19.decodeToHex(npub)
        prefs.edit().putString("external_pubkey", pubHex).apply()
        println("✅ Saved external pubkey from Amber (hex): $pubHex")
    }

    fun saveAmberPackageName(packageName: String) {
        prefs.edit().putString("amber_package", packageName).apply()
        println("✅ Saved Amber package name: $packageName")
    }

    fun getAmberPackageName(): String? {
        return prefs.getString("amber_package", null)
    }

    fun getPubkeyHex(): String? =
        prefs.getString("pub_hex", null)
            ?: prefs.getString("external_pubkey", null)
            ?: inMemoryPubHex

    fun exportNpubBech32(): String? = getPubkeyHex()?.let { Nip19.encode("npub", it) }

    fun exportNsecHex(): String? = inMemoryPrivHex ?: prefs.getString("priv_hex", null)
    fun exportNsecBech32(): String? = exportNsecHex()?.let { Nip19.encode("nsec", it) }

    /**
     * Check if user is authenticated via Amber (external signer).
     * Returns true if we have a pubkey but no private key.
     */
    fun isUsingAmber(): Boolean {
        val hasExternal = prefs.getString("external_pubkey", null) != null
        val hasPrivKey = exportNsecHex() != null
        return hasExternal && !hasPrivKey
    }

    fun clear() {
        prefs.edit().clear().apply()
        inMemoryPrivHex = null
        inMemoryPubHex = null
    }

    fun hasKey(): Boolean = getPubkeyHex() != null
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
