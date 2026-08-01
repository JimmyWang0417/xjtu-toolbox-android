package com.xjtu.toolbox.cmps.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun platformHttpClient(): HttpClient

fun campusJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun installCampusDefaults(config: HttpClientConfig<*>) {
    config.install(ContentNegotiation) { json(campusJson()) }
    config.install(HttpCookies) { storage = AcceptAllCookiesStorage() }
    config.install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 12_000
        socketTimeoutMillis = 20_000
    }
    config.install(UserAgent) {
        agent = "XJTUToolBox-CMPS/0.1.0"
    }
}
