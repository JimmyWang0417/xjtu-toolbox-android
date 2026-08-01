package com.xjtu.toolbox.util

import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

actual object FileSystem {
    private val fm = NSFileManager.defaultManager

    actual fun exists(path: String): Boolean = fm.fileExistsAtPath(path)

    actual fun mkdirs(path: String): Boolean =
        fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

    actual fun delete(path: String): Boolean =
        fm.removeItemAtPath(path, error = null)

    actual fun deleteContents(dirPath: String) {
        val contents = fm.contentsOfDirectoryAtPath(dirPath, error = null) ?: return
        for (item in contents) {
            val name = item as? String ?: continue
            fm.removeItemAtPath("$dirPath/$name", error = null)
        }
    }

    actual fun rename(from: String, to: String): Boolean {
        delete(to)
        return fm.moveItemAtPath(from, toPath = to, error = null)
    }

    actual fun readText(path: String): String? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    actual fun writeText(path: String, content: String) {
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun readBytes(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray()
    }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        data.writeToFile(path, atomically = true)
    }

    actual fun lastModified(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return 0L
        val date = attrs["NSFileModificationDate"] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000).toLong()
    }
}

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

// ── NSData <-> ByteArray 转换 ──

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    kotlinx.cinterop.memcpy(result.refTo(0), this.bytes, this.length)
    return result
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return kotlinx.cinterop.memScoped {
        NSData.create(bytes = this@toNSData.refTo(0).getPointer(this), length = this@toNSData.size.toULong())
    }
}
