package com.xjtu.toolbox.auth

import com.fleeksoft.ksoup.Ksoup
import com.xjtu.toolbox.util.Base64Utils
import com.xjtu.toolbox.util.CryptoUtils
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.urlEncode
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "XJTULogin"

enum class LoginState {
    REQUIRE_MFA,
    REQUIRE_CAPTCHA,
    SUCCESS,
    FAIL,
    REQUIRE_ACCOUNT_CHOICE
}

data class LoginResult(
    val state: LoginState,
    val message: String = "",
    val session: HttpClient? = null,
    val mfaContext: MFAContext? = null,
    val accountChoices: List<AccountChoice>? = null
)

data class AccountChoice(
    val name: String,
    val label: String
)

class MFAContext(
    private val login: XJTULogin,
    val state: String,
    val required: Boolean = true
) {
    var gid: String? = null
    private var phoneNumber: String? = null

    suspend fun getPhoneNumber(): String {
        phoneNumber?.let { return it }
        val response = login.client.get("https://login.xjtu.edu.cn/cas/mfa/initByType/securephone?state=$state")
        val json = response.bodyAsText().safeParseJsonObject()
        if (json["code"]?.jsonPrimitive?.int == 0) {
            val data = json["data"]!!.jsonObject
            gid = data["gid"]?.jsonPrimitive?.content
            phoneNumber = data["securePhone"]?.jsonPrimitive?.content
            return phoneNumber!!
        } else {
            throw RuntimeException("获取手机号失败")
        }
    }

    suspend fun sendVerifyCode(): String {
        val phone = getPhoneNumber()
        val body = buildJsonObject { put("gid", gid) }
        val response = login.client.post("https://login.xjtu.edu.cn/attest/api/guard/securephone/send") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val result = response.bodyAsText().safeParseJsonObject()
        if (result["code"]?.jsonPrimitive?.int == 0) {
            return phone
        } else {
            throw RuntimeException(result["message"]?.jsonPrimitive?.content ?: "发送验证码失败")
        }
    }

    suspend fun verifyCode(code: String) {
        if (gid == null) throw RuntimeException("必须先发送验证码")
        val body = buildJsonObject { put("gid", gid); put("code", code) }
        val response = login.client.post("https://login.xjtu.edu.cn/attest/api/guard/securephone/valid") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val result = response.bodyAsText().safeParseJsonObject()
        if (result["code"]?.jsonPrimitive?.int != 0) {
            throw RuntimeException(result["message"]?.jsonPrimitive?.content ?: "验证失败")
        }
    }
}

open class XJTULogin private constructor(
    val client: HttpClient,
    private var postUrl: String,
    private var executionInput: String,
    val fpVisitorId: String,
    private var mfaEnabled: Boolean,
    var hasLogin: Boolean,
    private var rsaPublicKey: String?,
    initialResponseBody: String,
    initialResponse: HttpResponse?
) {
    var mfaContext: MFAContext? = null
        private set

    private var username: String? = null
    private var encryptedPassword: String? = null
    private var jcaptcha: String = ""
    var rawPassword: String? = null
        internal set
    val storedUsername: String? get() = username
    private var failCount: Int = 0
    private var chooseAccountBody: String? = null
    var lastResponseBody: String = initialResponseBody
        internal set

    init {
        if (executionInput.isEmpty() && hasLogin && initialResponse != null) {
            try {
                postLogin(initialResponseBody)
                Logger.d(TAG, "init: SSO postLogin success")
            } catch (e: Exception) {
                hasLogin = false
                Logger.e(TAG, "init: SSO postLogin failed", e)
            }
        }
    }

    companion object {
        const val ATTENDANCE_URL = "http://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1372&redirectUri=http://bkkq.xjtu.edu.cn/berserker-auth/auth/attendance-pc/casReturn&responseType=code&scope=user_info&state=1234"
        const val ATTENDANCE_WEBVPN_URL = "http://bkkq.xjtu.edu.cn"
        const val JWXT_URL = "https://jwxt.xjtu.edu.cn/jwapp/sys/homeapp/index.do"

        suspend fun create(
            loginUrl: String,
            existingClient: HttpClient? = null,
            visitorId: String? = null,
            cachedRsaKey: String? = null,
            clientFactory: () -> HttpClient = { com.xjtu.toolbox.util.createHttpClient() }
        ): XJTULogin {
            val client = existingClient ?: clientFactory()
            val fpId = visitorId ?: generateFpVisitorId()

            Logger.d(TAG, "create: loginUrl=$loginUrl, hasExistingClient=${existingClient != null}")

            val response = client.get(loginUrl)
            val responseBody = response.bodyAsText()
            val finalUrl = response.call.request.url.toString()

            Logger.d(TAG, "create: responseCode=${response.status.value}, postUrl=$finalUrl")

            val execution = extractExecutionValue(responseBody)
            val isSSO = execution.isEmpty() && existingClient != null
            val mfaEnabled = if (!isSSO && execution.isNotEmpty()) extractMfaEnabled(responseBody) else false

            return XJTULogin(
                client = client,
                postUrl = finalUrl,
                executionInput = execution,
                fpVisitorId = fpId,
                mfaEnabled = mfaEnabled,
                hasLogin = isSSO,
                rsaPublicKey = cachedRsaKey,
                initialResponseBody = responseBody,
                initialResponse = if (isSSO) response else null
            )
        }

        private fun generateFpVisitorId(): String {
            val fingerprint = "KMP|${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}|${kotlin.random.Random.nextLong()}"
            return CryptoUtils.sha256(fingerprint).take(32)
        }

        internal fun extractExecutionValue(html: String): String {
            val doc = Ksoup.parse(html)
            val input = doc.select("input[name=execution]").first()
            return input?.attr("value") ?: ""
        }

        internal fun extractMfaEnabled(html: String): Boolean {
            val mfaRegex = """["']?mfaEnabled["']?\s*[:=]\s*["']?(true|false)["']?""".toRegex()
            val match = mfaRegex.find(html)
            if (match != null) return match.groupValues[1] == "true"
            return true
        }

        internal fun extractAlertMessage(html: String): String? {
            val doc = Ksoup.parse(html)
            val elAlert = doc.select("el-alert").first()
            if (elAlert != null) {
                val title = elAlert.attr("title")
                if (title.isNotBlank()) return title
                val text = elAlert.text().takeIf { it.isNotBlank() }
                if (text != null) return text
            }
            val alert = doc.select(".alert-danger, .errors, #errorMessage").first()
            return alert?.text()?.takeIf { it.isNotBlank() }
        }

        internal fun extractAccountChoices(html: String): List<AccountChoice> {
            val doc = Ksoup.parse(html)
            val choices = mutableListOf<AccountChoice>()
            doc.select("div.account-wrap").forEach { wrap ->
                val name = wrap.select("div.name").first()?.text()?.trim() ?: ""
                val label = wrap.select("el-radio.checkbox-radio").first()?.attr("label") ?: ""
                if (label.isNotBlank()) choices.add(AccountChoice(name.ifEmpty { label }, label))
            }
            if (choices.isNotEmpty()) return choices
            doc.select("input[name=username][type=radio], input[name=username][type=hidden]").forEach { input ->
                val label = input.attr("value")
                val name = input.parent()?.text() ?: label
                if (label.isNotBlank()) choices.add(AccountChoice(name, label))
            }
            return choices
        }
    }

    fun isShowCaptcha(): Boolean = failCount >= 3

    suspend fun getCaptchaImage(): ByteArray {
        val response = client.get("https://login.xjtu.edu.cn/cas/captcha.jpg")
        return response.readBytes()
    }

    suspend fun login(
        username: String? = null,
        password: String? = null,
        jcaptcha: String = "",
        accountType: AccountType = AccountType.POSTGRADUATE,
        trustAgent: Boolean = true
    ): LoginResult {
        chooseAccountBody?.let {
            return finishAccountChoice(accountType, trustAgent)
        }

        if (username != null && password != null) {
            this.username = username
            this.encryptedPassword = encryptPassword(password)
            this.rawPassword = password
            this.jcaptcha = jcaptcha
        }

        if (hasLogin) {
            return LoginResult(LoginState.SUCCESS, "SSO 自动认证成功", session = client)
        }

        if (this.username == null || this.encryptedPassword == null) {
            return LoginResult(LoginState.FAIL, "请提供用户名和密码")
        }

        if (isShowCaptcha() && jcaptcha.isEmpty() && this.jcaptcha.isEmpty()) {
            return LoginResult(LoginState.REQUIRE_CAPTCHA)
        }

        // MFA 检测
        if (mfaEnabled && !hasLogin && (mfaContext == null || !mfaContext!!.required)) {
            Logger.d(TAG, "login: MFA detect starting")
            val mfaResponse = client.submitForm(
                url = "https://login.xjtu.edu.cn/cas/mfa/detect",
                formParameters = Parameters.build {
                    append("username", this@XJTULogin.username!!)
                    append("password", this@XJTULogin.encryptedPassword!!)
                    append("fpVisitorId", fpVisitorId)
                }
            ) { header("Referer", postUrl) }

            val responseStr = mfaResponse.bodyAsText()
            Logger.d(TAG, "login: MFA detect response code=${mfaResponse.status.value}")
            val data = try {
                responseStr.safeParseJsonObject()["data"]!!.jsonObject
            } catch (e: Exception) {
                throw RuntimeException("MFA 检测返回数据异常: $responseStr")
            }

            val state = data["state"]?.jsonPrimitive?.content ?: ""
            val need = data["need"]?.jsonPrimitive?.content == "true"
            mfaContext = MFAContext(this, state, need)

            if (need) {
                return LoginResult(LoginState.REQUIRE_MFA, mfaContext = mfaContext)
            }
        }

        // 构造登录表单
        val mfaState = mfaContext?.state ?: ""
        val trustAgentStr = if (mfaContext?.required == true) { if (trustAgent) "true" else "false" } else ""

        Logger.d(TAG, "login: POST to $postUrl")
        val loginResponse = client.submitForm(
            url = postUrl,
            formParameters = Parameters.build {
                append("username", this@XJTULogin.username!!)
                append("password", this@XJTULogin.encryptedPassword!!)
                append("execution", executionInput)
                append("_eventId", "submit")
                append("submit1", "Login1")
                append("fpVisitorId", fpVisitorId)
                append("captcha", this@XJTULogin.jcaptcha)
                append("currentMenu", "1")
                append("failN", failCount.toString())
                append("mfaState", mfaState)
                append("geolocation", "")
                append("trustAgent", trustAgentStr)
            }
        )

        val loginBody = loginResponse.bodyAsText()
        Logger.d(TAG, "login: POST response code=${loginResponse.status.value}, bodyLen=${loginBody.length}")

        if (loginResponse.status.value == 401) {
            failCount++
            return LoginResult(LoginState.FAIL, "用户名或密码错误")
        }

        val alertMessage = extractAlertMessage(loginBody)
        if (alertMessage != null) {
            failCount++
            return LoginResult(LoginState.FAIL, "登录失败: $alertMessage")
        }

        failCount = 0
        hasLogin = true

        val choices = extractAccountChoices(loginBody)
        if (choices.isNotEmpty()) {
            chooseAccountBody = loginBody
            hasLogin = false
            return LoginResult(LoginState.REQUIRE_ACCOUNT_CHOICE, accountChoices = choices)
        }

        lastResponseBody = loginBody
        postLogin(loginBody)
        return LoginResult(LoginState.SUCCESS, session = client)
    }

    private suspend fun finishAccountChoice(
        accountType: AccountType,
        trustAgent: Boolean = true
    ): LoginResult {
        val body = chooseAccountBody ?: throw RuntimeException("不需要选择账户")
        val choices = extractAccountChoices(body)
        val selectedLabel = when (accountType) {
            AccountType.UNDERGRADUATE -> choices.find { "本科" in it.name }?.label
            AccountType.POSTGRADUATE -> choices.find { "研究" in it.name }?.label
        } ?: throw RuntimeException("未找到匹配的账户类型")

        val trustAgentStr = if (mfaContext?.required == true) { if (trustAgent) "true" else "false" } else ""
        val execution = extractExecutionValue(body)

        val response = client.submitForm(
            url = "https://login.xjtu.edu.cn/cas/login",
            formParameters = Parameters.build {
                append("execution", execution)
                append("_eventId", "submit")
                append("geolocation", "")
                append("fpVisitorId", fpVisitorId)
                append("trustAgent", trustAgentStr)
                append("username", selectedLabel)
                append("useDefault", "false")
            }
        )

        lastResponseBody = response.bodyAsText()
        chooseAccountBody = null
        hasLogin = true
        postLogin(lastResponseBody)
        return LoginResult(LoginState.SUCCESS, session = client)
    }

    open fun postLogin(responseBody: String) {}

    /** 暴露 casAuthenticate 供子登录类使用 */
    suspend fun reAuthViaCas(serviceUrl: String): Pair<String, String>? = casAuthenticate(serviceUrl)

    protected suspend fun casAuthenticate(serviceUrl: String): Pair<String, String>? {
        if (username == null || encryptedPassword == null) {
            Logger.w(TAG, "casAuthenticate: no stored credentials")
            return null
        }
        val casUrl = "https://login.xjtu.edu.cn/cas/login?service=${urlEncode(serviceUrl)}"
        val casResp = client.get(casUrl)
        val casBody = casResp.bodyAsText()
        val casFinalUrl = casResp.call.request.url.toString()
        Logger.d(TAG, "casAuthenticate: GET $casUrl → code=${casResp.status.value}, finalUrl=$casFinalUrl")

        val execution = extractExecutionValue(casBody)
        if (execution.isEmpty()) {
            Logger.d(TAG, "casAuthenticate: no execution → SSO redirect OK")
            return Pair(casBody, casFinalUrl)
        }

        Logger.d(TAG, "casAuthenticate: TGC expired, re-posting credentials")
        val loginResp = client.submitForm(
            url = casFinalUrl,
            formParameters = Parameters.build {
                append("username", username!!)
                append("password", encryptedPassword!!)
                append("execution", execution)
                append("_eventId", "submit")
                append("submit1", "Login1")
                append("fpVisitorId", fpVisitorId)
                append("currentMenu", "1")
                append("failN", "0")
                append("mfaState", "")
                append("geolocation", "")
            }
        )
        val loginBody = loginResp.bodyAsText()
        val loginFinalUrl = loginResp.call.request.url.toString()
        Logger.d(TAG, "casAuthenticate: POST → code=${loginResp.status.value}, finalUrl=$loginFinalUrl")
        return Pair(loginBody, loginFinalUrl)
    }

    fun getRsaPublicKey(): String? = rsaPublicKey

    private suspend fun fetchRsaPublicKeyFromServer(): String {
        val response = client.get("https://login.xjtu.edu.cn/cas/jwt/publicKey") {
            header("Referer", postUrl)
        }
        val body = response.bodyAsText()
        if (body.contains("<html", ignoreCase = true)) {
            throw RuntimeException("RSA 公钥接口返回 HTML 错误页面，可能是网络代理拦截")
        }
        Logger.d(TAG, "fetchRsaPublicKey: ${body.take(80)}...")
        return body
    }

    suspend fun encryptPassword(password: String, publicKeyStr: String? = null): String {
        val pubKey = publicKeyStr ?: run {
            if (rsaPublicKey == null) {
                rsaPublicKey = fetchRsaPublicKeyFromServer()
            }
            rsaPublicKey!!
        }

        val encrypted = try {
            CryptoUtils.rsaEncrypt(password.encodeToByteArray(), pubKey)
        } catch (e: Exception) {
            if (publicKeyStr != null) throw e
            Logger.w(TAG, "缓存的 RSA 公钥解析失败，重新获取: ${e.message}")
            rsaPublicKey = null
            val freshKey = fetchRsaPublicKeyFromServer()
            rsaPublicKey = freshKey
            try {
                CryptoUtils.rsaEncrypt(password.encodeToByteArray(), freshKey)
            } catch (e2: Exception) {
                throw RuntimeException("RSA 公钥解析失败，请检查网络或稍后重试", e2)
            }
        }

        return "__RSA__${Base64Utils.encode(encrypted)}"
    }

    enum class AccountType {
        UNDERGRADUATE,
        POSTGRADUATE
    }
}
