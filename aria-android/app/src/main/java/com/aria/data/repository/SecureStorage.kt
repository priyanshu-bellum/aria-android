package com.aria.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureStorage(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "aria_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        const val KEY_CLAUDE_API = "claude_api_key"
        const val KEY_MEM0_API = "mem0_api_key"
        const val KEY_MEM0_USER_ID = "mem0_user_id"
        const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val KEY_DEEPGRAM_API = "deepgram_api_key"
        const val KEY_COMPOSIO_API = "composio_api_key"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_PHONE = "user_phone"
        const val KEY_LIVEKIT_URL = "livekit_url"
        const val KEY_LIVEKIT_API_KEY = "livekit_api_key"
        const val KEY_LIVEKIT_API_SECRET = "livekit_api_secret"
        const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    fun saveApiKey(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getApiKey(key: String): String? = prefs.getString(key, null)

    fun hasKey(key: String): Boolean = prefs.contains(key)

    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    fun markSetupComplete() {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
