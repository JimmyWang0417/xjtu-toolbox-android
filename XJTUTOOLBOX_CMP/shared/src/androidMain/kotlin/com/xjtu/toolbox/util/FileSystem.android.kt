package com.xjtu.toolbox.util

import java.io.File

actual object FileSystem {
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun mkdirs(path: String): Boolean = File(path).mkdirs()
    actual fun delete(path: String): Boolean = File(path).delete()
    actual fun deleteContents(dirPath: String) {
        File(dirPath).listFiles()?.forEach { it.delete() }
    }
    actual fun rename(from: String, to: String): Boolean = File(from).renameTo(File(to))
    actual fun readText(path: String): String? = try { File(path).readText() } catch (_: Exception) { null }
    actual fun writeText(path: String, content: String) { File(path).writeText(content) }
    actual fun readBytes(path: String): ByteArray? = try { File(path).readBytes() } catch (_: Exception) { null }
    actual fun writeBytes(path: String, bytes: ByteArray) { File(path).writeBytes(bytes) }
    actual fun lastModified(path: String): Long = File(path).lastModified()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
