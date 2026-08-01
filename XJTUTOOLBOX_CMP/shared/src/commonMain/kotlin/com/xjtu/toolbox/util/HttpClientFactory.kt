package com.xjtu.toolbox.util

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 平台特定的 Ktor HttpClient engine */
expect fun createPlatformHttpClient(cookiesStorage: CookiesStorage? = null): HttpClient

/** 共享的宽松 JSON 解析配置 */
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** 创建标准 HttpClient（带 Cookie、JSON、超时） */
fun createHttpClient(cookiesStorage: CookiesStorage? = null): HttpClient {
    return createPlatformHttpClient(cookiesStorage)
}
