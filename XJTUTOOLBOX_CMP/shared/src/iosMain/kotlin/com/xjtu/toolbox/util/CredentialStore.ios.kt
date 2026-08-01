package com.xjtu.toolbox.util

import platform.Foundation.NSUserDefaults
import platform.Security.*
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

actual class CredentialStore actual constructor(context: PlatformContext) {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val service = "com.xjtu.toolbox"

    // ── Keychain helpers ──

    @OptIn(ExperimentalForeignApi::class)
    private fun keychainSave(key: String, value: String) {
        keychainDelete(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to key,
            kSecValueData to data
        )
        SecItemAdd(query as kotlinx.cinterop.CFDictionaryRef, null)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun keychainLoad(key: String): String? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to key,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne
        )
        memScoped {
            val result = alloc<kotlinx.cinterop.COpaquePointerVar>()
            val status = SecItemCopyMatching(query as kotlinx.cinterop.CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return null
            val data = result.value as? NSData ?: return null
            return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun keychainDelete(key: String) {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to key
        )
        SecItemDelete(query as kotlinx.cinterop.CFDictionaryRef)
    }

    // ── Credential operations ──

    actual fun save(username: String, password: String) {
        keychainSave("username", username)
        keychainSave("password", password)
    }

    actual fun load(): Pair<String, String>? {
        val username = keychainLoad("username") ?: return null
        val password = keychainLoad("password") ?: return null
        if (username.isEmpty() || password.isEmpty()) return null
        return username to password
    }

    actual fun clear() {
        keychainDelete("username")
        keychainDelete("password")
    }

    actual fun saveFpVisitorId(id: String) { defaults.setObject(id, forKey = "fp_visitor_id") }
    actual fun loadFpVisitorId(): String? = defaults.stringForKey("fp_visitor_id")

    actual fun saveRsaPublicKey(key: String) {
        defaults.setObject(key, forKey = "rsa_public_key")
        defaults.setDouble(currentTimeMillis().toDouble(), forKey = "rsa_key_time")
    }
    actual fun loadRsaPublicKey(): String? {
        val time = defaults.doubleForKey("rsa_key_time").toLong()
        if (currentTimeMillis() - time > 24 * 3600 * 1000L) return null
        return defaults.stringForKey("rsa_public_key")
    }

    actual fun saveNickname(name: String) { defaults.setObject(name, forKey = "cached_nickname") }
    actual fun loadNickname(): String? = defaults.stringForKey("cached_nickname")

    actual fun saveNsaProfile(json: String) { defaults.setObject(json, forKey = "nsa_profile_json") }
    actual fun loadNsaProfile(): String? = defaults.stringForKey("nsa_profile_json")

    actual fun saveNsaPhoto(bytes: ByteArray) {
        val path = PlatformContext().filesDir() + "/nsa_photo.jpg"
        FileSystem.writeBytes(path, bytes)
    }
    actual fun loadNsaPhoto(): ByteArray? {
        val path = PlatformContext().filesDir() + "/nsa_photo.jpg"
        return FileSystem.readBytes(path)
    }
    actual fun clearNsaCache() {
        defaults.removeObjectForKey("nsa_profile_json")
        FileSystem.delete(PlatformContext().filesDir() + "/nsa_photo.jpg")
    }

    actual fun isEulaAccepted(): Boolean = defaults.integerForKey("eula_accepted_version").toInt() >= CURRENT_EULA_VERSION
    actual fun acceptEula() { defaults.setInteger(CURRENT_EULA_VERSION.toLong(), forKey = "eula_accepted_version") }
    actual fun isUpdateNoticeSeen(versionName: String): Boolean = defaults.boolForKey("update_notice_$versionName")
    actual fun markUpdateNoticeSeen(versionName: String) { defaults.setBool(true, forKey = "update_notice_$versionName") }

    actual companion object {
        actual val CURRENT_EULA_VERSION: Int = 2
    }
}
