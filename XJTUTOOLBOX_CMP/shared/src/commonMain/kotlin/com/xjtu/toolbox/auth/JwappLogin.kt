package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "JwappLogin"

class JwappLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var authToken: String? = null
        private set
    private var tokenObtainedAt: Long = 0L

    companion object {
        const val JWAPP_URL =
            "https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1370&redirectUri=http://jwapp.xjtu.edu.cn/app/index&responseType=code&scope=user_info&state=1234"
        private const val TOKEN_TTL_MS = 60 * 60 * 1000L

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): JwappLogin {
            val base = XJTULogin.create(JWAPP_URL, session, visitorId, cachedRsaKey)
            val login = JwappLogin(base)
            if (base.hasLogin) {
                login.extractToken(base.lastResponseBody)
            }
            return login
        }
    }

    private fun extractToken(responseBody: String) {
        // Token 通常在最终 URL 的 query parameter 中，但我们这里从 response body 不太行
        // 实际上 token 在重定向 URL 中。Ktor 自动跟随重定向后，需要从最终 URL 提取
        // 这在 postLogin 中处理
    }

    fun handlePostLogin(finalUrl: String) {
        authToken = finalUrl.substringAfter("token=", "")
            .substringBefore("&")
            .takeIf { it.isNotEmpty() }
            ?: throw RuntimeException("登录失败：无法获取教务 Token")
        tokenObtainedAt = currentTimeMillis()
        Logger.d(TAG, "postLogin: token obtained, len=${authToken?.length}")
    }

    fun isTokenValid(): Boolean {
        val token = authToken ?: return false
        if (token.isEmpty()) return false
        if (tokenObtainedAt > 0 && currentTimeMillis() - tokenObtainedAt > TOKEN_TTL_MS) {
            Logger.d(TAG, "isTokenValid: token expired")
            return false
        }
        return true
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            Logger.d(TAG, "reAuthenticate: attempting SSO re-login")
            val response = client.get(JWAPP_URL)
            val finalUrl = response.call.request.url.toString()
            val token = finalUrl.substringAfter("token=", "")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() }
            if (token != null) {
                authToken = token
                tokenObtainedAt = currentTimeMillis()
                Logger.d(TAG, "reAuthenticate: SSO success")
                return true
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            val casResult = base.reAuthViaCas(JWAPP_URL)
            if (casResult != null) {
                val casToken = casResult.second.substringAfter("token=", "")
                    .substringBefore("&")
                    .takeIf { it.isNotEmpty() }
                if (casToken != null) {
                    authToken = casToken
                    tokenObtainedAt = currentTimeMillis()
                    Logger.d(TAG, "reAuthenticate: casAuthenticate success")
                    return true
                }
            }
            Logger.w(TAG, "reAuthenticate: all methods failed")
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
        }
        return false
    }

    suspend fun executeWithReAuth(
        url: String,
        block: suspend HttpClient.(String) -> HttpResponse
    ): HttpResponse {
        val response = client.block(url)
        if (response.status.value in listOf(401, 403)) {
            Logger.d(TAG, "executeWithReAuth: got ${response.status.value}, attempting reAuth")
            if (reAuthenticate()) {
                return client.block(url)
            }
        }
        return response
    }
}

/** 教务系统登录（CAS Cookie-based） */
class JwxtLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    companion object {
        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): JwxtLogin {
            val base = XJTULogin.create(XJTULogin.JWXT_URL, session, visitorId, cachedRsaKey)
            return JwxtLogin(base)
        }
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        return try {
            val result = base.reAuthViaCas(XJTULogin.JWXT_URL)
            if (result != null && !result.second.contains("login.xjtu.edu.cn/cas/login")) {
                Logger.d("JwxtLogin", "reAuthenticate: success")
                true
            } else {
                Logger.w("JwxtLogin", "reAuthenticate: returned login page")
                false
            }
        } catch (e: Exception) {
            Logger.e("JwxtLogin", "reAuthenticate failed", e)
            false
        }
    }
}
