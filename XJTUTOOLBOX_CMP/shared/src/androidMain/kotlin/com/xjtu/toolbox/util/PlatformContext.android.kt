package com.xjtu.toolbox.util

import android.content.Context

actual class PlatformContext(val androidContext: Context)

actual fun PlatformContext.cacheDir(): String = androidContext.cacheDir.absolutePath

actual fun PlatformContext.filesDir(): String = androidContext.filesDir.absolutePath
