package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VenueLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var sessionValid: Boolean = false
        private set

    companion object {
        private const val TAG = "VenueLogin"
        const val BASE_URL = "http://202.117.17.144"
        const val VENUE_OAUTH_URL =
            "https://login.xjtu.edu.cn/cas/oauth2.0/authorize?" +
            "response_type=code&client_id=1439&" +
            "redirect_uri=https%3A%2F%2Forg.xjtu.edu.cn%2Fopenplatform%2Foauth%2Fauthorizesw" +
            "%3Fredirect_uri%3Dhttp%3A%2F%2F202.117.17.144%2Fxjtu%2Fcas%2Foauth2url.html&" +
            "state=1"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): VenueLogin {
            val base = XJTULogin.create(VENUE_OAUTH_URL, session, visitorId, cachedRsaKey)
            val login = VenueLogin(base)
            if (base.hasLogin) {
                login.handlePostLogin()
            }
            return login
        }
    }

    private suspend fun handlePostLogin() {
        try {
            val indexResp = client.get("$BASE_URL/index.html")
            val finalUrl = indexResp.call.request.url.toString()
            sessionValid = finalUrl.contains("202.117.17.144") && !finalUrl.contains("login.xjtu.edu.cn")
            Logger.d(TAG, "handlePostLogin: finalUrl=$finalUrl, valid=$sessionValid")
        } catch (e: Exception) {
            Logger.e(TAG, "handlePostLogin failed", e)
        }
        if (!sessionValid) throw RuntimeException("登录失败：无法建立场馆系统会话")
    }

    suspend fun <T> executeWithReAuth(block: suspend () -> Pair<Int, T>): T {
        val (code, result) = block()
        if (code in listOf(401, 403) || !sessionValid) {
            Logger.d(TAG, "executeWithReAuth: got $code, attempting reAuth")
            if (reAuthenticate()) return block().second
        }
        return result
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val checkResp = client.get("$BASE_URL/product/index.html")
            val finalUrl = checkResp.call.request.url.toString()
            val body = checkResp.bodyAsText()

            if (finalUrl.contains("202.117.17.144") && !finalUrl.contains("login.xjtu.edu.cn") && body.contains("product/show.html")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: session still valid")
                return true
            }

            Logger.d(TAG, "reAuthenticate: session expired, trying SSO")
            val ssoResp = client.get(VENUE_OAUTH_URL)
            val ssoFinalUrl = ssoResp.call.request.url.toString()
            if (ssoFinalUrl.contains("202.117.17.144") && !ssoFinalUrl.contains("login.xjtu.edu.cn")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: SSO success")
                return true
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            val casResult = base.reAuthViaCas(VENUE_OAUTH_URL) ?: return false
            if (casResult.second.contains("202.117.17.144") && !casResult.second.contains("login.xjtu.edu.cn")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: casAuthenticate success")
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
        }
        sessionValid = false
        return false
    }
}
