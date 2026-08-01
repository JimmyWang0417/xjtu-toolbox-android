package com.xjtu.toolbox.lms

import com.xjtu.toolbox.auth.XJTULogin
import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 思源学堂 (lms.xjtu.edu.cn) 登录
 *
 * 认证链路 (CAS SSO):
 * 1. 访问 lms.xjtu.edu.cn → 302 → login.xjtu.edu.cn/cas
 * 2. CAS 登录成功 → 回调链 → lms.xjtu.edu.cn/user/index → session cookie
 *
 * 会话凭据: session cookie (on lms.xjtu.edu.cn)
 */
class LmsLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    companion object {
        private const val TAG = "LmsLogin"

        /** 思源学堂基础地址 */
        const val BASE_URL = "https://lms.xjtu.edu.cn"

        /** RMS 回放服务地址 */
        const val RMS_BASE_URL = "https://rms-v5.xjtu.edu.cn"

        /** CAS 登录入口 */
        const val LMS_LOGIN_URL = "https://lms.xjtu.edu.cn"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null
        ): LmsLogin {
            val base = XJTULogin.create(LMS_LOGIN_URL, session, visitorId, cachedRsaKey)
            val login = LmsLogin(base)
            login.postLogin()
            return login
        }
    }

    /** 登录后是否已获取有效 session */
    var sessionValid: Boolean = false
        private set

    private suspend fun postLogin() {
        // 手动访问触发 session
        try {
            val resp = client.get("$BASE_URL/user/index")
            val finalUrl = resp.call.request.url.toString()
            sessionValid = finalUrl.contains("lms.xjtu.edu.cn") &&
                !finalUrl.contains("login.xjtu.edu.cn")
            Logger.d(TAG, "postLogin: finalUrl=$finalUrl, valid=$sessionValid")
        } catch (e: Exception) {
            Logger.e(TAG, "postLogin: manual access failed", e)
        }

        if (!sessionValid) {
            throw RuntimeException("登录失败：无法建立思源学堂会话")
        }
    }

    private val reAuthMutex = Mutex()

    /**
     * 重新认证 (session 过期时调用)
     */
    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        try {
            val checkResp = client.get("$BASE_URL/api/my-courses") {
                header("Accept", "application/json")
            }
            val body = checkResp.bodyAsText()

            if (checkResp.status.value == 200 && body.contains("courses")) {
                sessionValid = true
                Logger.d(TAG, "reAuthenticate: session still valid")
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate: check failed", e)
        }

        // Session 过期，尝试通过 CAS SSO 重新认证
        try {
            val result = base.reAuthViaCas("$BASE_URL/user/index")
            if (result != null) {
                val (_, finalUrl) = result
                sessionValid = finalUrl.contains("lms.xjtu.edu.cn") &&
                    !finalUrl.contains("login.xjtu.edu.cn")
                Logger.d(TAG, "reAuthenticate: CAS re-auth, finalUrl=$finalUrl, valid=$sessionValid")
                return sessionValid
            }
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate: CAS re-auth failed", e)
        }
        return false
    }

    /**
     * 执行请求，自动在会话过期时重新认证并重试
     */
    suspend fun <T> executeWithReAuth(block: suspend (HttpClient) -> T): T {
        try {
            return block(client)
        } catch (_: Exception) {}

        if (reAuthenticate()) {
            return block(client)
        }
        throw RuntimeException("思源学堂会话已过期，重新认证失败")
    }
}
