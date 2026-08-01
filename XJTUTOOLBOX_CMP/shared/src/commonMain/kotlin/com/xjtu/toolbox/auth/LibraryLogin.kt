package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibraryLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var seatSystemReady: Boolean = false
    var diagnosticInfo: String = ""
        private set

    companion object {
        private const val TAG = "LibraryLogin"
        const val SEAT_BASE_URL = "http://rg.lib.xjtu.edu.cn:8086"

        suspend fun create(
            existingClient: HttpClient? = null,
            visitorId: String? = null
        ): LibraryLogin {
            val base = XJTULogin.create("$SEAT_BASE_URL/seat/", existingClient, visitorId)
            val login = LibraryLogin(base)
            if (base.hasLogin) {
                login.handlePostLogin(base.lastResponseBody)
            }
            return login
        }
    }

    private suspend fun handlePostLogin(body: String) {
        if (body.contains("btn-group") || body.contains("tab-select") || body.contains("seat")) {
            seatSystemReady = true
            diagnosticInfo = "座位系统已就绪"
            Logger.d(TAG, "postLogin: Seat system ready")
            return
        }

        Logger.d(TAG, "postLogin: init response not seat page, retrying...")
        try {
            val seatResponse = client.get("$SEAT_BASE_URL/seat/")
            val seatBody = seatResponse.bodyAsText()
            val seatFinalUrl = seatResponse.call.request.url.toString()

            if (seatBody.contains("btn-group") || seatBody.contains("tab-select") || seatBody.contains("seat")) {
                seatSystemReady = true
                diagnosticInfo = "座位系统已就绪"
            } else if (seatFinalUrl.contains("login.xjtu.edu.cn")) {
                diagnosticInfo = "CAS 认证未完成，请确认已连接校园网或 VPN"
            } else {
                diagnosticInfo = "座位系统返回异常页面\nURL: $seatFinalUrl"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "postLogin retry failed", e)
            diagnosticInfo = "座位系统访问失败: ${e.message}"
        }
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val seatResponse = client.get("$SEAT_BASE_URL/seat/")
            val seatBody = seatResponse.bodyAsText()
            if (seatBody.contains("btn-group") || seatBody.contains("tab-select") || seatBody.contains("seat")) {
                seatSystemReady = true
                diagnosticInfo = "座位系统已就绪"
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
            diagnosticInfo = "重新认证失败: ${e.message}"
        }
        return false
    }
}
