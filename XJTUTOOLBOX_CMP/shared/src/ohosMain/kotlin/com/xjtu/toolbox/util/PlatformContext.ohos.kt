package com.xjtu.toolbox.util

actual class PlatformContext(
    val cacheDirectory: String = "/data/storage/el2/base/cache",
    val filesDirectory: String = "/data/storage/el2/base/files"
)

actual fun PlatformContext.cacheDir(): String = cacheDirectory

actual fun PlatformContext.filesDir(): String = filesDirectory
