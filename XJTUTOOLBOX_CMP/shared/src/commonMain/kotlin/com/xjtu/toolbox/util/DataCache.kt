package com.xjtu.toolbox.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "DataCache"

/**
 * 轻量级 JSON 文件缓存（协程安全 + 原子写入）
 * 跨平台实现：使用 expect/actual 获取缓存目录
 */
class DataCache(context: PlatformContext) {
    private val cachePath = context.cacheDir() + "/data_cache"

    private val locks = mutableMapOf<String, Mutex>()
    private val globalLock = Mutex()

    companion object {
        const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000L
        const val SHORT_TTL_MS = 30L * 60 * 1000L
    }

    init {
        FileSystem.mkdirs(cachePath)
    }

    private suspend fun lockFor(key: String): Mutex {
        globalLock.withLock {
            return locks.getOrPut(key) { Mutex() }
        }
    }

    suspend fun get(key: String, ttlMs: Long = DEFAULT_TTL_MS): String? {
        val mutex = lockFor(key)
        mutex.withLock {
            val filePath = "$cachePath/${key.sanitize()}.json"
            if (!FileSystem.exists(filePath)) return null
            val age = currentTimeMillis() - FileSystem.lastModified(filePath)
            if (age > ttlMs) {
                FileSystem.delete(filePath)
                return null
            }
            return FileSystem.readText(filePath)
        }
    }

    suspend fun put(key: String, json: String) {
        val mutex = lockFor(key)
        mutex.withLock {
            val sanitized = key.sanitize()
            val filePath = "$cachePath/${sanitized}.json"
            val tmpPath = "$cachePath/${sanitized}.json.tmp"
            FileSystem.writeText(tmpPath, json)
            if (!FileSystem.rename(tmpPath, filePath)) {
                FileSystem.writeText(filePath, json)
                FileSystem.delete(tmpPath)
            }
        }
    }

    suspend fun invalidate(key: String) {
        val mutex = lockFor(key)
        mutex.withLock {
            val filePath = "$cachePath/${key.sanitize()}.json"
            FileSystem.delete(filePath)
        }
    }

    suspend fun clearAll() {
        globalLock.withLock {
            FileSystem.deleteContents(cachePath)
            locks.clear()
        }
    }

    private fun String.sanitize(): String = this.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
