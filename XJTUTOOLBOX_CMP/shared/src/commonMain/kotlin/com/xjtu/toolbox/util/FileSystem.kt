package com.xjtu.toolbox.util

/** 跨平台文件系统操作 */
expect object FileSystem {
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun delete(path: String): Boolean
    fun deleteContents(dirPath: String)
    fun rename(from: String, to: String): Boolean
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray)
    fun lastModified(path: String): Long
}

/** 跨平台当前时间戳 */
expect fun currentTimeMillis(): Long
