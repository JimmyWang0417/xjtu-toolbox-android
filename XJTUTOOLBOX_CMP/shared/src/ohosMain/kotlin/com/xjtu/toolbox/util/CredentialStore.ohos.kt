package com.xjtu.toolbox.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

actual class CredentialStore actual constructor(context: PlatformContext) {

    private val storagePath = context.filesDir() + "/credentials"
    private val credFile = "$storagePath/cred.json"
    private val settingsFile = "$storagePath/settings.json"

    init {
        FileSystem.mkdirs(storagePath)
    }

    private fun readJson(path: String): JsonObject? {
        val text = FileSystem.readText(path) ?: return null
        return try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) { null }
    }

    private fun writeJson(path: String, obj: JsonObject) {
        FileSystem.writeText(path, obj.toString())
    }

    actual fun save(username: String, password: String) {
        writeJson(credFile, buildJsonObject {
            put("username", username)
            put("password", password)
        })
    }

    actual fun load(): Pair<String, String>? {
        val obj = readJson(credFile) ?: return null
        val u = obj["username"]?.jsonPrimitive?.content ?: return null
        val p = obj["password"]?.jsonPrimitive?.content ?: return null
        if (u.isEmpty() || p.isEmpty()) return null
        return u to p
    }

    actual fun clear() { FileSystem.delete(credFile) }

    actual fun saveFpVisitorId(id: String) {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("fp_visitor_id", id)
        })
    }
    actual fun loadFpVisitorId(): String? = readJson(settingsFile)?.get("fp_visitor_id")?.jsonPrimitive?.content

    actual fun saveRsaPublicKey(key: String) {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("rsa_public_key", key)
            put("rsa_key_time", currentTimeMillis())
        })
    }
    actual fun loadRsaPublicKey(): String? {
        val obj = readJson(settingsFile) ?: return null
        val time = obj["rsa_key_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        if (currentTimeMillis() - time > 24 * 3600 * 1000L) return null
        return obj["rsa_public_key"]?.jsonPrimitive?.content
    }

    actual fun saveNickname(name: String) {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("cached_nickname", name)
        })
    }
    actual fun loadNickname(): String? = readJson(settingsFile)?.get("cached_nickname")?.jsonPrimitive?.content

    actual fun saveNsaProfile(json: String) {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("nsa_profile_json", json)
        })
    }
    actual fun loadNsaProfile(): String? = readJson(settingsFile)?.get("nsa_profile_json")?.jsonPrimitive?.content

    actual fun saveNsaPhoto(bytes: ByteArray) {
        FileSystem.writeBytes("$storagePath/nsa_photo.jpg", bytes)
    }
    actual fun loadNsaPhoto(): ByteArray? = FileSystem.readBytes("$storagePath/nsa_photo.jpg")
    actual fun clearNsaCache() {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.filter { it.key != "nsa_profile_json" }.forEach { (k, v) -> put(k, v) }
        })
        FileSystem.delete("$storagePath/nsa_photo.jpg")
    }

    actual fun isEulaAccepted(): Boolean {
        val v = readJson(settingsFile)?.get("eula_accepted_version")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return v >= CURRENT_EULA_VERSION
    }
    actual fun acceptEula() {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("eula_accepted_version", CURRENT_EULA_VERSION)
        })
    }
    actual fun isUpdateNoticeSeen(versionName: String): Boolean {
        return readJson(settingsFile)?.get("update_notice_$versionName")?.jsonPrimitive?.content == "true"
    }
    actual fun markUpdateNoticeSeen(versionName: String) {
        val obj = readJson(settingsFile) ?: buildJsonObject {}
        writeJson(settingsFile, buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("update_notice_$versionName", "true")
        })
    }

    actual companion object {
        actual val CURRENT_EULA_VERSION: Int = 2
    }
}
