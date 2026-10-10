package dev.harness.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persists LLM configuration. The API key is encrypted at rest. */
@Suppress("DEPRECATION") // EncryptedSharedPreferences is the agreed storage; the replacement API is not yet stable.
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    /** Write tools the user chose "Always allow" for. Revocable from Settings. */
    var alwaysAllowedTools: Set<String>
        get() = prefs.getStringSet(KEY_ALWAYS_ALLOWED, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_ALWAYS_ALLOWED, value.toSet()).apply()

    companion object {
        const val DEFAULT_BASE_URL = "https://opencode.ai/zen/go/v1"

        /** A chat-completions model; change it in Settings to match your provider. */
        const val DEFAULT_MODEL = "deepseek-v4.1-flash"

        private const val FILE_NAME = "harness_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ALWAYS_ALLOWED = "always_allowed_tools"
    }
}
