package com.xjtu.toolbox.auth

import com.fleeksoft.ksoup.Ksoup
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 校园卡系统登录 (card.xjtu.edu.cn)
 * 使用组合模式包装 XJTULogin，处理旧 CAS (cas.xjtu.edu.cn) 认证
 */
class CampusCardLogin private constructor(val base: XJTULogin) {

    val client: HttpClient get() = base.client

    var hallticket: String? = null
        private set
    var cardAccount: String? = null
        internal set
    var systemReady: Boolean = false
        private set

    companion object {
        private const val TAG = "CampusCardLogin"
        const val LOGIN_URL = "http://card.xjtu.edu.cn/Category/ContechFirstPage"
        const val BASE_URL = "http://card.xjtu.edu.cn"
        private const val OLD_CAS_URL = "https://cas.xjtu.edu.cn"
        private const val CARD_SERVICE_URL = "http://card.xjtu.edu.cn/Category/ContechFirstPage"

        suspend fun create(
            existingClient: HttpClient? = null,
            visitorId: String? = null
        ): CampusCardLogin {
            val base = XJTULogin.create(LOGIN_URL, existingClient, visitorId)
            val login = CampusCardLogin(base)
            if (base.hasLogin) {
                login.handlePostLogin(base.lastResponseBody)
            }
            return login
        }
    }

    private fun handlePostLogin(body: String) {
        Logger.d(TAG, "handlePostLogin: bodyLen=${body.length}")
        if (tryExtractInfo(body)) return
    }

    private suspend fun authenticateViaOldCas(): Boolean {
        val username = base.storedUsername ?: return false
        val password = base.rawPassword ?: return false

        val serviceUrl = urlEncode(CARD_SERVICE_URL)
        val casLoginUrl = "$OLD_CAS_URL/login?service=$serviceUrl"
        Logger.d(TAG, "oldCasAuth: GET $casLoginUrl")

        val casResp = client.get(casLoginUrl)
        val casBody = casResp.bodyAsText()
        Logger.d(TAG, "oldCasAuth: GET → code=${casResp.status.value}, bodyLen=${casBody.length}")

        if (tryExtractInfo(casBody)) return true

        val doc = Ksoup.parse(casBody)
        val execution = doc.select("input[name=execution]").first()?.attr("value") ?: ""
        val lt = doc.select("input[name=lt]").first()?.attr("value") ?: ""

        if (execution.isEmpty() && lt.isEmpty()) {
            Logger.w(TAG, "oldCasAuth: no execution/lt found")
            return false
        }

        val hiddenFields = mutableMapOf<String, String>()
        doc.select("input[type=hidden]").forEach { input ->
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotEmpty() && name !in listOf("username", "password", "execution", "lt", "_eventId")) {
                hiddenFields[name] = value
            }
        }

        val postUrl = casResp.call.request.url.toString()
        val loginResp = client.submitForm(
            url = postUrl,
            formParameters = Parameters.build {
                append("username", username)
                append("password", password)
                append("_eventId", "submit")
                if (execution.isNotEmpty()) append("execution", execution)
                if (lt.isNotEmpty()) append("lt", lt)
                hiddenFields.forEach { (k, v) -> append(k, v) }
            }
        )
        val loginBody = loginResp.bodyAsText()
        if (tryExtractInfo(loginBody)) return true

        val pageResp = client.get("$BASE_URL/Page/Page")
        val pageBody = pageResp.bodyAsText()
        return tryExtractInfo(pageBody)
    }

    private fun tryExtractInfo(html: String): Boolean {
        // 从 HTML 提取 cardAccount
        val accountPattern = Regex("""toinitInfos\(\s*'(\d+)'\s*\)""")
        accountPattern.find(html)?.let {
            cardAccount = it.groupValues[1]
            Logger.d(TAG, "Found cardAccount from HTML: $cardAccount")
        }

        // 从 HTML 提取 hallticket（可能在 cookie 信息中或隐藏字段中）
        val htPattern = Regex("""hallticket[=:]\s*["']?([a-zA-Z0-9]+)""")
        htPattern.find(html)?.let {
            hallticket = it.groupValues[1]
        }

        if (hallticket != null) {
            systemReady = true
            Logger.d(TAG, "Campus card system ready (hallticket found)")
            return true
        }

        if (html.length > 500 && !html.contains("404")) {
            systemReady = true
            Logger.d(TAG, "Campus card page reached, assuming system ready")
            return true
        }

        return false
    }

    private val reAuthMutex = Mutex()

    suspend fun reAuthenticate(): Boolean = reAuthMutex.withLock {
        Logger.d(TAG, "reAuthenticate: refreshing hallticket...")
        hallticket = null
        systemReady = false
        try {
            val resp1 = client.get(CARD_SERVICE_URL)
            val body1 = resp1.bodyAsText()
            if (tryExtractInfo(body1)) return true

            if (authenticateViaOldCas()) return true

            val pageResp = client.get("$BASE_URL/Page/Page")
            val pageBody = pageResp.bodyAsText()
            if (tryExtractInfo(pageBody)) return true

            Logger.w(TAG, "reAuthenticate: all methods failed")
            return false
        } catch (e: Exception) {
            Logger.e(TAG, "reAuthenticate failed", e)
            return false
        }
    }
}
