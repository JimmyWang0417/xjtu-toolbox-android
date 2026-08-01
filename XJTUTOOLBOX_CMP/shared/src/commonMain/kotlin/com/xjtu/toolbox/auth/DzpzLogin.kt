package com.xjtu.toolbox.auth

import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DzpzLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var userId: String? = null
        private set

    companion object {
        private const val TAG = "DzpzLogin"
        const val DZPZ_OAUTH_URL =
            "https://login.xjtu.edu.cn/cas/oauth2.0/authorize?" +
            "response_type=code&client_id=new9940&" +
            "redirect_uri=https%3A%2F%2Fdzpz.xjtu.edu.cn%2Flogin%2FLogin.jsp"
        const val BASE_URL = "https://dzpz.xjtu.edu.cn"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): DzpzLogin {
            val base = XJTULogin.create(DZPZ_OAUTH_URL, session, visitorId, cachedRsaKey)
            val login = DzpzLogin(base)
            if (base.hasLogin) {
                login.handlePostLogin()
            }
            return login
        }
    }

    private suspend fun handlePostLogin() {
        // 尝试访问首页触发 session 初始化，从响应中提取 userId
        try {
            val homeResp = client.get("$BASE_URL/wui/index.html")
            val body = homeResp.bodyAsText()
            // 尝试从 cookie 或页面内容提取 loginidweaver
            val pattern = Regex("""loginidweaver[=:]\s*["']?(\d+)""")
            pattern.find(body)?.let { userId = it.groupValues[1] }
        } catch (e: Exception) {
            Logger.e(TAG, "handlePostLogin failed", e)
        }

        if (userId == null) {
            throw RuntimeException("登录失败：无法获取用户 OA ID (loginidweaver)")
        }
        Logger.d(TAG, "handlePostLogin: userId=$userId")
    }

    suspend fun <T> executeWithReAuth(block: suspend () -> Pair<Int, T>): T {
        val (code, result) = block()
        if (code in listOf(401, 403)) {
            Logger.d(TAG, "executeWithReAuth: got $code, attempting reAuth")
            if (reAuthenticate()) return block().second
        }
        return result
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val checkResp = client.get("$BASE_URL/api/ecode/sync")
            val finalUrl = checkResp.call.request.url.toString()

            if (!finalUrl.contains("login.xjtu.edu.cn")) {
                Logger.d(TAG, "reAuthenticate: session still valid")
                return true
            }

            Logger.d(TAG, "reAuthenticate: session expired, trying SSO")
            val ssoResp = client.get(DZPZ_OAUTH_URL)
            val ssoFinalUrl = ssoResp.call.request.url.toString()

            if (ssoFinalUrl.contains("dzpz.xjtu.edu.cn") && !ssoFinalUrl.contains("login.xjtu.edu.cn")) {
                Logger.d(TAG, "reAuthenticate: SSO success")
                return true
            }

            Logger.d(TAG, "reAuthenticate: SSO failed, trying casAuthenticate")
            base.reAuthViaCas(DZPZ_OAUTH_URL) ?: return false
            Logger.d(TAG, "reAuthenticate: casAuthenticate success")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
        }
        return false
    }
}
