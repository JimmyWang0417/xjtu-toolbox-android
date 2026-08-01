package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Base64Utils
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val TAG = "YwtbLogin"

class YwtbLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var idToken: String? = null
        private set
    private var tokenExpireAt: Long = 0L
    private var tokenObtainedAt: Long = 0L

    companion object {
        const val YWTB_LOGIN_URL =
            "https://login.xjtu.edu.cn/cas/login?service=https%3A%2F%2Fywtb.xjtu.edu.cn%2F%3Fpath%3Dhttps%253A%252F%252Fywtb.xjtu.edu.cn%252Fmain.html%2523%252FIndex"
        private const val EXPIRY_MARGIN_SEC = 30L
        private const val FALLBACK_TTL_MS = 60 * 60 * 1000L

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): YwtbLogin {
            val base = XJTULogin.create(YWTB_LOGIN_URL, session, visitorId, cachedRsaKey)
            val login = YwtbLogin(base)
            // Token extraction happens on redirect URL, handled separately
            return login
        }
    }

    fun extractTokenFromUrl(ticketJwt: String) {
        val parts = ticketJwt.split(".")
        if (parts.size < 2) throw RuntimeException("无效的 JWT Token")

        val payload64 = parts[1].let { p ->
            when (p.length % 4) {
                2 -> p + "=="
                3 -> p + "="
                else -> p
            }
        }
        // Base64url → Base64 standard
        val standardBase64 = payload64.replace('-', '+').replace('_', '/')
        val payloadJson = Base64Utils.decode(standardBase64).decodeToString()
        val payload = payloadJson.safeParseJsonObject()
        idToken = payload["idToken"]?.jsonPrimitive?.content
            ?: throw RuntimeException("JWT 中未找到 idToken")

        tokenExpireAt = try { payload["exp"]?.jsonPrimitive?.long ?: 0L } catch (_: Exception) { 0L }
        tokenObtainedAt = currentTimeMillis()

        if (tokenExpireAt > 0) {
            val remainSec = tokenExpireAt - currentTimeMillis() / 1000
            Logger.d(TAG, "token obtained, expires in ${remainSec}s")
        } else {
            Logger.d(TAG, "token obtained, no exp field")
        }
    }

    fun isTokenValid(): Boolean {
        val token = idToken ?: return false
        if (token.isEmpty()) return false
        val nowSec = currentTimeMillis() / 1000
        if (tokenExpireAt > 0) return nowSec < tokenExpireAt - EXPIRY_MARGIN_SEC
        if (tokenObtainedAt > 0) return currentTimeMillis() - tokenObtainedAt < FALLBACK_TTL_MS
        return true
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            Logger.d(TAG, "reAuthenticate: attempting SSO re-login")
            val response = client.get(YWTB_LOGIN_URL)
            val finalUrl = response.call.request.url.toString()
            val ticket = io.ktor.http.Url(finalUrl).parameters["ticket"]
            if (ticket != null && ticket.isNotEmpty()) {
                try {
                    extractTokenFromUrl(ticket)
                    Logger.d(TAG, "reAuthenticate: SSO success")
                    return true
                } catch (_: Exception) {}
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            val casResult = base.reAuthViaCas(YWTB_LOGIN_URL)
            if (casResult != null) {
                val retryResponse = client.get(YWTB_LOGIN_URL)
                val retryUrl = retryResponse.call.request.url.toString()
                val retryTicket = io.ktor.http.Url(retryUrl).parameters["ticket"]
                if (retryTicket != null && retryTicket.isNotEmpty()) {
                    try {
                        extractTokenFromUrl(retryTicket)
                        Logger.d(TAG, "reAuthenticate: casAuthenticate success")
                        return true
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
        }
        return false
    }
}
