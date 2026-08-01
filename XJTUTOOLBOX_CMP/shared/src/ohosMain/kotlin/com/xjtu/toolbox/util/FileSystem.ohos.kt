package com.xjtu.toolbox.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual object FileSystem {

    actual fun exists(path: String): Boolean = access(path, F_OK) == 0

    actual fun mkdirs(path: String): Boolean {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = if (path.startsWith("/")) "/" else ""
        for (part in parts) {
            current += "$part/"
            mkdir(current, 0x1FFu) // 0777
        }
        return exists(path)
    }

    actual fun delete(path: String): Boolean = remove(path) == 0

    actual fun deleteContents(dirPath: String) {
        val dir = opendir(dirPath) ?: return
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                remove("$dirPath/$name")
            }
        } finally {
            closedir(dir)
        }
    }

    actual fun rename(from: String, to: String): Boolean {
        delete(to)
        return platform.posix.rename(from, to) == 0
    }

    actual fun readText(path: String): String? {
        val file = fopen(path, "r") ?: return null
        try {
            val sb = StringBuilder()
            val buf = ByteArray(4096)
            while (true) {
                val read = fread(buf.refTo(0), 1u, buf.size.toULong(), file)
                if (read == 0uL) break
                sb.append(buf.decodeToString(0, read.toInt()))
            }
            return sb.toString()
        } finally {
            fclose(file)
        }
    }

    actual fun writeText(path: String, content: String) {
        val file = fopen(path, "w") ?: return
        try {
            val bytes = content.encodeToByteArray()
            fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), file)
        } finally {
            fclose(file)
        }
    }

    actual fun readBytes(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            if (size <= 0) return ByteArray(0)
            val buf = ByteArray(size)
            fread(buf.refTo(0), 1u, size.toULong(), file)
            return buf
        } finally {
            fclose(file)
        }
    }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = fopen(path, "wb") ?: return
        try {
            fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), file)
        } finally {
            fclose(file)
        }
    }

    actual fun lastModified(path: String): Long {
        return kotlinx.cinterop.memScoped {
            val st = kotlinx.cinterop.alloc<stat>()
            if (stat(path, st.ptr) != 0) 0L
            else st.st_mtime * 1000L
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    return kotlinx.cinterop.memScoped {
        val tv = kotlinx.cinterop.alloc<timeval>()
        gettimeofday(tv.ptr, null)
        tv.tv_sec * 1000L + tv.tv_usec / 1000L
    }
}
