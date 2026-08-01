package com.xjtu.toolbox.classreplay

import com.xjtu.toolbox.auth.XJTULogin
import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClassLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var sessionValid: Boolean = false
        private set

    companion object {
        private const val TAG = "ClassLogin"
        const val BASE_URL = "https://class.xjtu.edu.cn"
        const val CLASS_LOGIN_URL = "https://class.xjtu.edu.cn/login"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): ClassLogin {
            val base = XJTULogin.create(CLASS_LOGIN_URL, session, visitorId, cachedRsaKey)
            val login = ClassLogin(base)
            if (base.hasLogin) {
                login.handlePostLogin()
            }
            return login
        }
    }

    private suspend fun handlePostLogin() {
        try {
            val indexResp = client.get("$BASE_URL/user/index")
            val finalUrl = indexResp.call.request.url.toString()
            sessionValid = finalUrl.contains("class.xjtu.edu.cn") &&
                !finalUrl.contains("login.xjtu.edu.cn")
            Logger.d(TAG, "handlePostLogin: finalUrl=$finalUrl, valid=$sessionValid")
        } catch (e: Exception) {
            Logger.e(TAG, "handlePostLogin failed", e)
        }
        if (!sessionValid) throw RuntimeException("登录失败：无法建立课程平台会话")
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val checkResp = client.get("$BASE_URL/api/user/recently-visited-courses") {
                header("Accept", "application/json")
            }
            val finalUrl = checkResp.call.request.url.toString()
            val code = checkResp.status.value

            if (code == 200 && finalUrl.contains("class.xjtu.edu.cn") &&
                !finalUrl.contains("login.xjtu.edu.cn")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: session still valid")
                return true
            }

            Logger.d(TAG, "reAuthenticate: session expired, trying SSO")
            val ssoResp = client.get(CLASS_LOGIN_URL)
            val ssoFinalUrl = ssoResp.call.request.url.toString()
            if (ssoFinalUrl.contains("class.xjtu.edu.cn") &&
                !ssoFinalUrl.contains("login.xjtu.edu.cn")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: SSO success")
                return true
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            val casResult = base.reAuthViaCas(CLASS_LOGIN_URL) ?: return false
            if (casResult.second.contains("class.xjtu.edu.cn") &&
                !casResult.second.contains("login.xjtu.edu.cn")) {
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

    suspend fun <T> executeWithReAuth(block: suspend () -> Pair<Int, T>): T {
        val (code, result) = block()
        val needReAuth = code in listOf(401, 403)
        if (needReAuth) {
            Logger.d(TAG, "executeWithReAuth: got $code, attempting reAuth")
            if (reAuthenticate()) {
                return block().second
            }
        }
        return result
    }
}
