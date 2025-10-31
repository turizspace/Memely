package com.memely.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage wrapper using EncryptedSharedPreferences.
 * This provides at-rest encryption for sensitive data like nsec.
 */
class SecureStorage private constructor(context: Context) {
    // Use the modern MasterKey API (replaces deprecated MasterKeys)
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_memely_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private var instance: SecureStorage? = null
        
        fun init(context: Context) {
            if (instance == null) {
                instance = SecureStorage(context.applicationContext)
            }
        }
        
        fun getInstance(): SecureStorage = instance ?: throw IllegalStateException(
            "SecureStorage not initialized. Call init(context) first."
        )
    }
}