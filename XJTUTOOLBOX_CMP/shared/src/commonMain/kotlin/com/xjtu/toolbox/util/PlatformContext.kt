package com.xjtu.toolbox.util

/**
 * 平台上下文抽象 — 包装 Android Context / iOS 无需 Context 的差异
 * 所有需要平台资源（文件、SharedPreferences 等）的类通过此接口获取依赖
 */
expect class PlatformContext

/** 获取平台缓存目录路径 */
expect fun PlatformContext.cacheDir(): String

/** 获取平台内部文件目录路径 */
expect fun PlatformContext.filesDir(): String
