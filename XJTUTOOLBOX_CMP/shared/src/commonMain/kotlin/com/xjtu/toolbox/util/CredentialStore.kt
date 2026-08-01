package com.xjtu.toolbox.util

/**
 * 凭据安全存储 — 跨平台接口
 * Android: EncryptedSharedPreferences
 * iOS: Keychain Services
 */
expect class CredentialStore(context: PlatformContext) {
    fun save(username: String, password: String)
    fun load(): Pair<String, String>?
    fun clear()

    fun saveFpVisitorId(id: String)
    fun loadFpVisitorId(): String?

    fun saveRsaPublicKey(key: String)
    fun loadRsaPublicKey(): String?

    fun saveNickname(name: String)
    fun loadNickname(): String?

    fun saveNsaProfile(json: String)
    fun loadNsaProfile(): String?

    fun saveNsaPhoto(bytes: ByteArray)
    fun loadNsaPhoto(): ByteArray?
    fun clearNsaCache()

    fun isEulaAccepted(): Boolean
    fun acceptEula()

    fun isUpdateNoticeSeen(versionName: String): Boolean
    fun markUpdateNoticeSeen(versionName: String)

    companion object {
        val CURRENT_EULA_VERSION: Int
    }
}
