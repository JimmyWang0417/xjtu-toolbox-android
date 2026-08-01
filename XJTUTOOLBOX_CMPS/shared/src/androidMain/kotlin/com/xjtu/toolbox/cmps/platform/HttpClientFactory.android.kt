package com.xjtu.toolbox.cmps.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    installCampusDefaults(this)
}
