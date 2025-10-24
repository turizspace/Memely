package com.memely.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage wrapper using EncryptedSharedPreferences.
 * This provides at-rest encryption for sensitive data like nsec.
 */
class SecureStorage private constructor(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_memely_prefs",
        masterKeyAlias,
        context,
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