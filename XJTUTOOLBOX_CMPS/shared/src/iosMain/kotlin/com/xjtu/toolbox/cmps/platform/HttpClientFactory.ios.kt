package com.xjtu.toolbox.cmps.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) {
    installCampusDefaults(this)
}
