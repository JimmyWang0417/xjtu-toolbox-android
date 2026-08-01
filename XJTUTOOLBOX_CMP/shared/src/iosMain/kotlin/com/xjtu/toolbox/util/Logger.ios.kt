package com.xjtu.toolbox.util

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, message: String) { NSLog("D/$tag: $message") }
    actual fun w(tag: String, message: String) { NSLog("W/$tag: $message") }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("E/$tag: $message${if (throwable != null) " - ${throwable.message}" else ""}")
    }
}
