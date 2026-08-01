package com.xjtu.toolbox.cmps.data

import com.russhwolf.settings.Settings

private const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val SHORT_TTL_MS = 30L * 60 * 1000

data class CachedString(
    val value: String,
    val ageMs: Long,
    val stale: Boolean,
)

data class CredentialSnapshot(
    val accountId: String,
    val username: String,
    val password: String,
)

class CampusLocalStore(
    private val settings: Settings = Settings(),
) {
    var activeAccountId: String
        get() = settings.getString("account.active", "default")
        set(value) {
            settings.putString("account.active", value.ifBlank { "default" })
        }

    fun scopedKey(key: String, accountScoped: Boolean = true): String {
        val scope = if (accountScoped) activeAccountId else "global"
        return "cache.$scope.${key.sanitizeKey()}"
    }

    fun putCache(
        key: String,
        value: String,
        accountScoped: Boolean = true,
        nowMs: Long = currentEpochMillis(),
    ) {
        val scoped = scopedKey(key, accountScoped)
        settings.putString("$scoped.value", value)
        settings.putLong("$scoped.updatedAt", nowMs)
    }

    fun getCache(
        key: String,
        ttlMs: Long = DEFAULT_TTL_MS,
        accountScoped: Boolean = true,
        allowStale: Boolean = false,
        nowMs: Long = currentEpochMillis(),
    ): CachedString? {
        val scoped = scopedKey(key, accountScoped)
        val value = settings.getStringOrNull("$scoped.value") ?: return null
        val updatedAt = settings.getLong("$scoped.updatedAt", 0L)
        val age = (nowMs - updatedAt).coerceAtLeast(0)
        val stale = updatedAt <= 0 || age > ttlMs
        if (stale && !allowStale) return null
        return CachedString(value, age, stale)
    }

    fun cacheInfo(key: String, ttlMs: Long = DEFAULT_TTL_MS, accountScoped: Boolean = true): CacheEntryInfo {
        val cached = getCache(key, ttlMs, accountScoped, allowStale = true)
        return CacheEntryInfo(
            key = key,
            accountScope = if (accountScoped) activeAccountId else "global",
            ttlMs = ttlMs,
            ageMs = cached?.ageMs,
            staleAvailable = cached != null,
        )
    }

    fun removeCache(key: String, accountScoped: Boolean = true) {
        val scoped = scopedKey(key, accountScoped)
        settings.remove("$scoped.value")
        settings.remove("$scoped.updatedAt")
    }

    fun getSetting(key: String, defaultValue: String): String =
        settings.getString("setting.${key.sanitizeKey()}", defaultValue)

    fun putSetting(key: String, value: String) {
        settings.putString("setting.${key.sanitizeKey()}", value)
    }

    fun getFlag(key: String, defaultValue: Boolean): Boolean =
        settings.getBoolean("setting.${key.sanitizeKey()}", defaultValue)

    fun putFlag(key: String, value: Boolean) {
        settings.putBoolean("setting.${key.sanitizeKey()}", value)
    }

    fun putCredential(accountId: String, username: String, password: String) {
        val scoped = "credential.${accountId.sanitizeKey()}"
        settings.putString("$scoped.username", username)
        settings.putString("$scoped.password", password)
    }

    fun getCredential(accountId: String): CredentialSnapshot? {
        val scoped = "credential.${accountId.sanitizeKey()}"
        val username = settings.getStringOrNull("$scoped.username") ?: return null
        val password = settings.getStringOrNull("$scoped.password") ?: return null
        return CredentialSnapshot(accountId, username, password)
    }

    fun removeCredential(accountId: String) {
        val scoped = "credential.${accountId.sanitizeKey()}"
        settings.remove("$scoped.username")
        settings.remove("$scoped.password")
    }

    fun defaultCacheEntries(): List<CacheEntryInfo> = listOf(
        cacheInfo("schedule_2025-2026-2"),
        cacheInfo("yellow_page", 24L * 60 * 60 * 1000, accountScoped = false),
        cacheInfo("library_seats", SHORT_TTL_MS),
    )
}

private fun String.sanitizeKey(): String =
    replace(Regex("[^A-Za-z0-9_.-]"), "_")
