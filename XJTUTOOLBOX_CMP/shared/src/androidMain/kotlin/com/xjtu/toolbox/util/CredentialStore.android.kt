package com.xjtu.toolbox.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

actual class CredentialStore actual constructor(context: PlatformContext) {

    private val appContext = context.androidContext.applicationContext

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                "xjtu_credentials",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            try {
                val prefsDir = java.io.File(appContext.applicationInfo.dataDir, "shared_prefs")
                prefsDir.listFiles()?.filter { it.name.startsWith("xjtu_credentials") }?.forEach { it.delete() }
                EncryptedSharedPreferences.create(
                    "xjtu_credentials",
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    appContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                appContext.getSharedPreferences("xjtu_credentials_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    actual fun save(username: String, password: String) {
        prefs.edit().putString(KEY_USERNAME, username).putString(KEY_PASSWORD, password).apply()
    }

    actual fun load(): Pair<String, String>? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        if (username.isEmpty() || password.isEmpty()) return null
        return username to password
    }

    actual fun clear() { prefs.edit().clear().apply() }

    actual fun saveFpVisitorId(id: String) { prefs.edit().putString(KEY_FP_VISITOR_ID, id).apply() }
    actual fun loadFpVisitorId(): String? = prefs.getString(KEY_FP_VISITOR_ID, null)

    actual fun saveRsaPublicKey(key: String) {
        prefs.edit().putString(KEY_RSA_PUBLIC_KEY, key).putLong(KEY_RSA_KEY_TIME, System.currentTimeMillis()).apply()
    }
    actual fun loadRsaPublicKey(): String? {
        val time = prefs.getLong(KEY_RSA_KEY_TIME, 0L)
        if (System.currentTimeMillis() - time > 24 * 3600 * 1000L) return null
        return prefs.getString(KEY_RSA_PUBLIC_KEY, null)
    }

    actual fun saveNickname(name: String) { prefs.edit().putString(KEY_NICKNAME, name).apply() }
    actual fun loadNickname(): String? = prefs.getString(KEY_NICKNAME, null)

    actual fun saveNsaProfile(json: String) { prefs.edit().putString(KEY_NSA_PROFILE, json).apply() }
    actual fun loadNsaProfile(): String? = prefs.getString(KEY_NSA_PROFILE, null)

    actual fun saveNsaPhoto(bytes: ByteArray) {
        try { appContext.openFileOutput(NSA_PHOTO_FILE, Context.MODE_PRIVATE).use { it.write(bytes) } } catch (_: Exception) {}
    }
    actual fun loadNsaPhoto(): ByteArray? = try { appContext.openFileInput(NSA_PHOTO_FILE).use { it.readBytes() } } catch (_: Exception) { null }

    actual fun clearNsaCache() {
        prefs.edit().remove(KEY_NSA_PROFILE).apply()
        try { appContext.deleteFile(NSA_PHOTO_FILE) } catch (_: Exception) {}
    }

    private val appPrefs: SharedPreferences =
        appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    actual fun isEulaAccepted(): Boolean = appPrefs.getInt(KEY_EULA_VERSION, 0) >= CURRENT_EULA_VERSION
    actual fun acceptEula() { appPrefs.edit().putInt(KEY_EULA_VERSION, CURRENT_EULA_VERSION).apply() }
    actual fun isUpdateNoticeSeen(versionName: String): Boolean = appPrefs.getBoolean("update_notice_$versionName", false)
    actual fun markUpdateNoticeSeen(versionName: String) { appPrefs.edit().putBoolean("update_notice_$versionName", true).apply() }

    actual companion object {
        actual val CURRENT_EULA_VERSION: Int = 2
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_FP_VISITOR_ID = "fp_visitor_id"
        private const val KEY_RSA_PUBLIC_KEY = "rsa_public_key"
        private const val KEY_RSA_KEY_TIME = "rsa_key_time"
        private const val KEY_NICKNAME = "cached_nickname"
        private const val KEY_NSA_PROFILE = "nsa_profile_json"
        private const val NSA_PHOTO_FILE = "nsa_photo.jpg"
        private const val KEY_EULA_VERSION = "eula_accepted_version"
    }
}
