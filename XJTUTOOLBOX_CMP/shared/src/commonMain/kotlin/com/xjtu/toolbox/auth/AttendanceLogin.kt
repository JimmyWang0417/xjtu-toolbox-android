package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttendanceLogin private constructor(
    val base: XJTULogin,
    private val useWebVpn: Boolean
) {
    val client: HttpClient get() = base.client

    var authToken: String? = null
        private set

    companion object {
        private const val TAG = "AttendanceLogin"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            useWebVpn: Boolean = false
        ): AttendanceLogin {
            val loginUrl = if (useWebVpn) XJTULogin.ATTENDANCE_WEBVPN_URL else XJTULogin.ATTENDANCE_URL
            val base = XJTULogin.create(loginUrl, session, visitorId)
            val login = AttendanceLogin(base, useWebVpn)
            if (base.hasLogin) {
                login.extractTokenFromUrl(base.lastResponseBody)
            }
            return login
        }
    }

    private suspend fun extractTokenFromUrl(responseBody: String) {
        // Token is in the redirect URL, try to re-access to get it
        val loginUrl = if (useWebVpn) XJTULogin.ATTENDANCE_WEBVPN_URL else XJTULogin.ATTENDANCE_URL
        val response = client.get(loginUrl)
        val finalUrl = response.call.request.url.toString()
        authToken = finalUrl.substringAfter("token=", "")
            .substringBefore("&")
            .substringBefore("#")
            .takeIf { it.isNotEmpty() }
    }

    /** Ktor request block helper: adds Synjones-Auth header */
    fun authHeader(): Pair<String, String> = "Synjones-Auth" to "bearer $authToken"

    /**
     * Execute a suspend block; if it returns 401/403, reAuthenticate and retry once.
     */
    suspend fun <T> executeWithReAuth(block: suspend () -> Pair<Int, T>): T {
        val (code, result) = block()
        if (code in listOf(401, 403)) {
            Logger.d(TAG, "executeWithReAuth: got $code, attempting reAuth")
            if (reAuthenticate()) {
                return block().second
            }
        }
        return result
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val loginUrl = if (useWebVpn) XJTULogin.ATTENDANCE_WEBVPN_URL else XJTULogin.ATTENDANCE_URL
            val response = client.get(loginUrl)
            val finalUrl = response.call.request.url.toString()
            val token = finalUrl.substringAfter("token=", "")
                .substringBefore("&")
                .substringBefore("#")
                .takeIf { it.isNotEmpty() }
            if (token != null) {
                authToken = token
                Logger.d(TAG, "reAuthenticate: SSO success")
                return true
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            val casResult = base.reAuthViaCas(loginUrl) ?: return false
            val casToken = casResult.second.substringAfter("token=", "")
                .substringBefore("&")
                .substringBefore("#")
                .takeIf { it.isNotEmpty() }
            if (casToken != null) {
                authToken = casToken
                Logger.d(TAG, "reAuthenticate: casAuthenticate success")
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
        }
        return false
    }
}
