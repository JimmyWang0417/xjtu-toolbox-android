package com.xjtu.toolbox.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json

actual fun createPlatformHttpClient(cookiesStorage: CookiesStorage?): HttpClient {
    return HttpClient(Darwin) {
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        if (cookiesStorage != null) {
            install(HttpCookies) { storage = cookiesStorage }
        } else {
            install(HttpCookies)
        }
        followRedirects = true
    }
}
