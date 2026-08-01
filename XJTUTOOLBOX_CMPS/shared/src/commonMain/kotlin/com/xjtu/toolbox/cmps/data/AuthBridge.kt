package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.app.AccountProfile
import com.xjtu.toolbox.cmps.app.AccountType
import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthTicket(
    val accountId: String,
    val service: CampusEndpoint,
    val cookies: Map<String, String>,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
) {
    val isExpired: Boolean get() = expiresAtEpochMs != null && currentEpochMillis() >= expiresAtEpochMs
}

sealed interface LoginStep {
    data object Idle : LoginStep
    data class Loading(val message: String) : LoginStep
    data class NeedCaptcha(val imageBytes: ByteArray) : LoginStep
    data class NeedMfa(val prompt: String) : LoginStep
    data class AccountChoice(val choices: List<String>) : LoginStep
    data class Success(val account: AccountProfile) : LoginStep
    data class Failed(val reason: String, val recoverable: Boolean = true) : LoginStep
}

interface AuthBridge {
    suspend fun login(username: String, password: String, service: CampusEndpoint): LoginStep
    suspend fun refresh(ticket: AuthTicket): AuthTicket?
    suspend fun logout(accountId: String)
}

data class CasLoginPage(
    val actionUrl: String,
    val execution: String,
    val eventId: String = "submit",
    val mfaEnabled: Boolean = false,
    val requiresCaptcha: Boolean = false,
    val safetyVerify: CasSafetyVerify? = null,
    val hiddenFields: Map<String, String> = emptyMap(),
)

data class CasSafetyVerify(
    val secState: String,
    val execution: String,
    val eventId: String,
    val submitValue: String,
)

data class CasSession(
    val finalUrl: String,
    val cookies: Map<String, String>,
)

class CasAuthApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun login(username: String, password: String, endpoint: CampusEndpoint): CasLoginResult {
        val loginUrl = loginUrlFor(endpoint)
        val initial = client.get(loginUrl) {
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        val initialBody = initial.bodyAsText()
        val page = parseLoginPage(initial.request.url.toString(), initialBody)
            ?: return if (isAlreadyAuthenticated(initial, initialBody)) {
                CasLoginResult.Success(CasSession(initial.request.url.toString(), extractCookies(initial)))
            } else {
                CasLoginResult.Failed("统一认证页面解析失败")
            }
        page.safetyVerify?.let {
            return CasLoginResult.NeedMfa("需要完成统一认证二次验证")
        }
        if (page.requiresCaptcha) {
            return CasLoginResult.NeedCaptcha(fetchCaptcha())
        }
        val publicKey = runCatching { client.get("$casBase/jwt/publicKey").bodyAsText() }.getOrElse {
            return CasLoginResult.Failed("RSA 公钥获取失败: ${it.message.orEmpty()}")
        }
        val encryptedPassword = runCatching { encryptCasPassword(password, publicKey) }.getOrElse {
            return CasLoginResult.Failed("密码加密失败: ${it.message.orEmpty()}")
        }
        val response = client.submitForm(
            url = page.actionUrl,
            formParameters = Parameters.build {
                page.hiddenFields.forEach { (key, value) -> append(key, value) }
                append("username", username)
                append("password", encryptedPassword)
                append("execution", page.execution)
                append("_eventId", page.eventId)
                append("submit", "登录")
                append("geolocation", "")
            },
        ) {
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.Referer, loginUrl)
        }
        val body = response.bodyAsText()
        if (isSafetyVerifyPage(body)) return CasLoginResult.NeedMfa("需要完成统一认证二次验证")
        if (hasAccountChoice(body)) return CasLoginResult.NeedAccountChoice(listOf("本科生", "研究生"))
        if (isCredentialFailure(body)) return CasLoginResult.Failed("用户名或密码错误")
        if (parseLoginPage(response.request.url.toString(), body) != null) {
            return CasLoginResult.Failed("统一认证未完成，请检查账号状态")
        }
        val session = CasSession(response.request.url.toString(), extractCookies(response))
        return CasLoginResult.Success(enrichSession(endpoint, session))
    }

    suspend fun refresh(ticket: AuthTicket): AuthTicket? =
        ticket.takeUnless { it.isExpired } ?: ticket.copy(
            issuedAtEpochMs = currentEpochMillis(),
            expiresAtEpochMs = currentEpochMillis() + defaultTicketTtlMs,
        )

    private suspend fun fetchCaptcha(): ByteArray =
        client.get("$casBase/captcha.jpg").body<ByteArray>()

    private fun loginUrlFor(endpoint: CampusEndpoint): String {
        if (endpoint == CampusEndpoint.CampusCard) return ncardLoginUrl
        if (endpoint == CampusEndpoint.JwApp) return jwAppLoginUrl
        if (endpoint == CampusEndpoint.Fitness) return fitnessLoginUrl
        if (endpoint == CampusEndpoint.Attendance) return attendanceLoginUrl
        if (endpoint == CampusEndpoint.Coupon) return couponLoginUrl
        if (endpoint == CampusEndpoint.ClassReplay) return ClassReplayApi.baseUrl
        if (endpoint == CampusEndpoint.JwxtJudge) return UndergraduateJudgeApi.loginUrl
        if (endpoint == CampusEndpoint.GraduateJudge) return GraduateJudgeApi.loginUrl
        if (endpoint == CampusEndpoint.Venue) return VenueApi.oauthLoginUrl
        if (endpoint == CampusEndpoint.Cas) return CampusEndpoint.Cas.url
        return "${CampusEndpoint.Cas.url}?service=${endpoint.url.encodeURLParameter()}"
    }

    private suspend fun enrichSession(endpoint: CampusEndpoint, session: CasSession): CasSession =
        when (endpoint) {
            CampusEndpoint.CampusCard -> enrichCampusCardSession(session)
            CampusEndpoint.Ywtb -> enrichYwtbSession(session)
            CampusEndpoint.JwApp -> enrichJwAppSession(session)
            CampusEndpoint.Fitness -> enrichFitnessSession(session)
            CampusEndpoint.Attendance -> enrichAttendanceSession(session)
            CampusEndpoint.Coupon -> enrichCouponSession(session)
            CampusEndpoint.Venue -> enrichVenueSession(session)
            CampusEndpoint.Lms -> enrichLmsSession(session)
            CampusEndpoint.ClassReplay -> enrichClassReplaySession(session)
            CampusEndpoint.JwxtJudge -> enrichJwxtJudgeSession(session)
            CampusEndpoint.GraduateJudge -> enrichGraduateJudgeSession(session)
            else -> session
        }

    private suspend fun enrichCampusCardSession(session: CasSession): CasSession {
        val ticket = extractQuery(session.finalUrl, "ticket")
            ?: return session
        val tokenResponse = client.submitForm(
            url = "$ncardBase/berserker-auth/oauth/token",
            formParameters = Parameters.build {
                append("username", ticket)
                append("password", ticket)
                append("grant_type", "password")
                append("scope", "all")
                append("loginFrom", "h5")
                append("logintype", "sso")
                append("device_token", "h5")
                append("synAccessSource", "h5")
            },
        ) {
            header(HttpHeaders.Authorization, ncardTokenBasicAuth)
        }
        val tokenRoot = Json.parseToJsonElement(tokenResponse.bodyAsText()).jsonObject
        val token = tokenRoot["access_token"]?.jsonPrimitive?.contentOrNull
            ?: return session
        val userResponse = client.get("$ncardBase/berserker-base/user?synAccessSource=h5") {
            header("synjones-auth", "bearer $token")
            header("synAccessSource", "h5")
        }
        val userRoot = Json.parseToJsonElement(userResponse.bodyAsText()).jsonObject
        val userData = userRoot["data"]?.jsonObject
        return session.copy(
            cookies = session.cookies + mapOf(
                "synjones_auth" to token,
                "card_account" to userData?.get("cardAccount")?.jsonPrimitive?.contentOrNull.orEmpty(),
                "user_name" to userData?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                "student_no" to userData?.get("sno")?.jsonPrimitive?.contentOrNull.orEmpty(),
            ),
        )
    }

    private fun extractQuery(url: String, key: String): String? {
        val query = url.substringAfter("?", missingDelimiterValue = "")
        if (query.isBlank()) return null
        return query.split("&").firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            if (name == key && value.isNotBlank()) value.decodeURLQueryComponent() else null
        }
    }

    private fun enrichYwtbSession(session: CasSession): CasSession {
        val ticket = extractQuery(session.finalUrl, "ticket")
            ?: return session
        val payload = ticket.split(".").getOrNull(1)
            ?.let(::decodeBase64UrlToString)
            ?.let { Json.parseToJsonElement(it).jsonObject }
            ?: return session
        val idToken = payload["idToken"]?.jsonPrimitive?.contentOrNull
            ?: return session
        return session.copy(cookies = session.cookies + ("x_id_token" to idToken))
    }

    private fun enrichJwAppSession(session: CasSession): CasSession {
        val token = extractQuery(session.finalUrl, "token")
            ?: return session
        return session.copy(cookies = session.cookies + ("jwapp_token" to token))
    }

    private fun enrichFitnessSession(session: CasSession): CasSession =
        session.copy(cookies = session.cookies + ("fitness_referer" to session.finalUrl.ifBlank { fitnessHomeUrl }))

    private suspend fun enrichAttendanceSession(session: CasSession): CasSession {
        val token = extractQuery(session.finalUrl, "token")
            ?: runCatching {
                val retry = client.get(attendanceLoginUrl)
                extractQuery(retry.request.url.toString(), "token")
            }.getOrNull()
            ?: return session
        return session.copy(cookies = session.cookies + ("attendance_token" to token))
    }

    private suspend fun enrichCouponSession(session: CasSession): CasSession {
        val code = extractQuery(session.finalUrl, "code")
            ?: return session
        val userType = extractQuery(session.finalUrl, "userType").orEmpty()
        val employeeNo = extractQuery(session.finalUrl, "employeeNo").orEmpty()
        val tokenResponse = client.post(
            "$couponBase/sso/login?code=${code.encodeURLParameter()}&userType=${userType.encodeURLParameter()}&employeeNo=${employeeNo.encodeURLParameter()}",
        ) {
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header("Content-Type", "application/json;charset=UTF-8")
            header("Origin", couponBase)
            header("Referer", couponReceiveUrl)
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.UserAgent, couponBrowserUserAgent)
            setBody("""{"json":true}""")
        }
        val text = tokenResponse.bodyAsText()
        val headerToken = tokenResponse.headers[HttpHeaders.Authorization]?.normalizeBearer()
        val bodyToken = extractTokenFromText(text)
        val token = headerToken ?: bodyToken ?: return session
        return session.copy(cookies = session.cookies + ("coupon_token" to token))
    }

    private suspend fun enrichVenueSession(session: CasSession): CasSession {
        val index = runCatching { client.get("${VenueApi.base}/index.html") }.getOrNull()
        val app = runCatching { client.get("${VenueApi.appBase}/product/index.html") }.getOrNull()
        val cookies = session.cookies + index?.let(::extractCookies).orEmpty() + app?.let(::extractCookies).orEmpty()
        return session.copy(
            finalUrl = app?.request?.url?.toString() ?: index?.request?.url?.toString() ?: session.finalUrl,
            cookies = cookies,
        )
    }

    private suspend fun enrichLmsSession(session: CasSession): CasSession {
        val index = runCatching { client.get("${LmsApi.baseUrl}/user/index") }.getOrNull()
        val courses = runCatching { client.post("${LmsApi.baseUrl}/api/my-courses") { setBody("") } }.getOrNull()
        val cookies = session.cookies + index?.let(::extractCookies).orEmpty() + courses?.let(::extractCookies).orEmpty()
        return session.copy(
            finalUrl = courses?.request?.url?.toString() ?: index?.request?.url?.toString() ?: session.finalUrl,
            cookies = cookies,
        )
    }

    private suspend fun enrichClassReplaySession(session: CasSession): CasSession {
        val index = runCatching { client.get("${ClassReplayApi.baseUrl}/user/index") }.getOrNull()
        val courses = runCatching { client.post("${ClassReplayApi.baseUrl}/api/my-courses") { setBody("{}") } }.getOrNull()
        val cookies = session.cookies + index?.let(::extractCookies).orEmpty() + courses?.let(::extractCookies).orEmpty()
        return session.copy(
            finalUrl = courses?.request?.url?.toString() ?: index?.request?.url?.toString() ?: session.finalUrl,
            cookies = cookies,
        )
    }

    private suspend fun enrichJwxtJudgeSession(session: CasSession): CasSession {
        val home = runCatching { client.get(UndergraduateJudgeApi.loginUrl) }.getOrNull()
        val term = runCatching { client.get("https://jwxt.xjtu.edu.cn/api/v2/system/term-info") }.getOrNull()
        val cookies = session.cookies + home?.let(::extractCookies).orEmpty() + term?.let(::extractCookies).orEmpty()
        return session.copy(
            finalUrl = term?.request?.url?.toString() ?: home?.request?.url?.toString() ?: session.finalUrl,
            cookies = cookies,
        )
    }

    private suspend fun enrichGraduateJudgeSession(session: CasSession): CasSession {
        val list = runCatching { client.get("${GraduateJudgeApi.baseUrl}/app/sshd4Stu/list.do") }.getOrNull()
        val cookies = session.cookies + list?.let(::extractCookies).orEmpty()
        return session.copy(
            finalUrl = list?.request?.url?.toString() ?: session.finalUrl,
            cookies = cookies,
        )
    }

    private fun extractTokenFromText(text: String): String? {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (root != null) {
            listOf("Authorization", "authorization", "token", "accessToken", "access_token", "jwt", "data").forEach { key ->
                runCatching { root[key]?.jsonPrimitive?.contentOrNull }
                    .getOrNull()
                    ?.normalizeBearer()
                    ?.takeIf { it.startsWith("eyJ") }
                    ?.let { return it }
            }
        }
        return Regex("""eyJ[A-Za-z0-9_\-.]+""").find(text)?.value?.normalizeBearer()
    }

    private fun decodeBase64UrlToString(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = decodeBase64(padded)
        return bytes.decodeToString()
    }

    private fun decodeBase64(value: String): ByteArray {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = value.filter { !it.isWhitespace() }
        val output = mutableListOf<Byte>()
        var index = 0
        while (index < clean.length) {
            val chunk = clean.substring(index, minOf(index + 4, clean.length)).padEnd(4, '=')
            val nums = chunk.map { char ->
                if (char == '=') -1 else table.indexOf(char).also { if (it < 0) error("Base64 内容非法") }
            }
            val combined = ((nums[0].coerceAtLeast(0) shl 18) or
                (nums[1].coerceAtLeast(0) shl 12) or
                (nums[2].coerceAtLeast(0) shl 6) or
                nums[3].coerceAtLeast(0))
            output += ((combined shr 16) and 0xff).toByte()
            if (nums[2] >= 0) output += ((combined shr 8) and 0xff).toByte()
            if (nums[3] >= 0) output += (combined and 0xff).toByte()
            index += 4
        }
        return output.toByteArray()
    }

    private fun parseLoginPage(currentUrl: String, html: String): CasLoginPage? {
        val form = extractForm(html) ?: return null
        val execution = form.input("execution")
        if (execution.isBlank()) return null
        val action = form.action.ifBlank { currentUrl }
        return CasLoginPage(
            actionUrl = absolutizeUrl(currentUrl, action),
            execution = execution,
            eventId = form.input("_eventId").ifBlank { "submit" },
            mfaEnabled = "mfaEnabled" in html || "/cas/mfa/" in html,
            requiresCaptcha = "captcha" in html || "验证码" in html,
            safetyVerify = parseSafetyVerify(form, html),
            hiddenFields = form.hiddenInputs.filterKeys { key ->
                key !in setOf("username", "password", "execution", "_eventId", "submit", "geolocation")
            },
        )
    }

    private fun parseSafetyVerify(form: HtmlForm, html: String): CasSafetyVerify? {
        if (!isSafetyVerifyPage(html)) return null
        val secState = form.input("secState")
        if (secState.isBlank()) return null
        return CasSafetyVerify(
            secState = secState,
            execution = form.input("execution"),
            eventId = form.input("_eventId").ifBlank { "submit" },
            submitValue = form.input("submit").ifBlank { "Login1" },
        )
    }

    private fun isAlreadyAuthenticated(response: HttpResponse, body: String): Boolean =
        response.request.url.host != "login.xjtu.edu.cn" && !isCasLoginPage(body)

    private fun extractCookies(response: HttpResponse): Map<String, String> =
        response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .mapNotNull { raw -> runCatching { parseServerSetCookieHeader(raw) }.getOrNull() }
            .associate { cookie -> cookie.name to cookie.value }

    private fun absolutizeUrl(baseUrl: String, action: String): String =
        when {
            action.startsWith("http://") || action.startsWith("https://") -> action
            action.startsWith("/") -> "${Url(baseUrl).protocol.name}://${Url(baseUrl).host}$action"
            else -> baseUrl.substringBeforeLast("/") + "/$action"
        }

    private fun extractForm(html: String): HtmlForm? {
        val formMatch = Regex("""<form\b([^>]*)>(.*?)</form>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .firstOrNull { it.value.contains("name=\"execution\"") || it.value.contains("name='execution'") }
            ?: return null
        val attrs = formMatch.groupValues[1]
        val body = formMatch.groupValues[2]
        val action = attr(attrs, "action")
        val inputs = Regex("""<input\b([^>]*)>""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .mapNotNull { match ->
                val inputAttrs = match.groupValues[1]
                val name = attr(inputAttrs, "name")
                if (name.isBlank()) null else name to attr(inputAttrs, "value")
            }
            .toMap()
        return HtmlForm(action, inputs)
    }

    private fun attr(attrs: String, name: String): String {
        val quoted = Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)
        if (quoted != null) return htmlDecode(quoted.groupValues[1])
        val bare = Regex("""\b$name\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE).find(attrs)
        return htmlDecode(bare?.groupValues?.get(1).orEmpty())
    }

    private fun htmlDecode(value: String): String =
        value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun isCasLoginPage(html: String): Boolean =
        html.contains("name=\"execution\"") || html.contains("name='execution'")

    private fun isSafetyVerifyPage(html: String): Boolean =
        html.contains("name=\"secState\"") && html.contains("name=\"execution\"") ||
            html.contains("Safety Verify") ||
            html.contains("/cas/sec/initByType") ||
            html.contains("二次认证")

    private fun hasAccountChoice(html: String): Boolean =
        html.contains("选择账号") || html.contains("accountType") || html.contains("本科") && html.contains("研究生")

    private fun isCredentialFailure(html: String): Boolean =
        html.contains("用户名或密码") ||
            html.contains("密码错误") ||
            html.contains("账号或密码") ||
            html.contains("authenticationFailure")

    private data class HtmlForm(
        val action: String,
        val hiddenInputs: Map<String, String>,
    ) {
        fun input(name: String): String = hiddenInputs[name].orEmpty()
    }

    sealed interface CasLoginResult {
        data class Success(val session: CasSession) : CasLoginResult
        data class NeedCaptcha(val imageBytes: ByteArray) : CasLoginResult
        data class NeedMfa(val prompt: String) : CasLoginResult
        data class NeedAccountChoice(val choices: List<String>) : CasLoginResult
        data class Failed(val reason: String) : CasLoginResult
    }

    companion object {
        private const val casBase = "https://login.xjtu.edu.cn/cas"
        private const val ncardBase = "https://ncard.xjtu.edu.cn"
        private const val ncardLoginUrl = "$ncardBase/berserker-base/redirect?type=login&loginFrom=h5&synAccessSource=h5"
        private const val ncardTokenBasicAuth =
            "Basic bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm06bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm1fc2VjcmV0"
        private const val jwAppLoginUrl =
            "https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1370&redirectUri=http://jwapp.xjtu.edu.cn/app/index&responseType=code&scope=user_info&state=1234"
        private const val fitnessLoginUrl =
            "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/public/index.php/index/login/xjtuLogin"
        private const val fitnessHomeUrl =
            "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/view/h5xajt/#/pages/index/index"
        private const val attendanceLoginUrl =
            "https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1298&redirectUri=https://bkkq.xjtu.edu.cn/attendance-mobile/xjtu/index.html&responseType=code&scope=user_info&state=1234"
        private const val couponBase = "https://egc.xjtu.edu.cn"
        private const val couponReceiveUrl = "$couponBase/page/cas/receiveCas.html?version=SAFT_VERSION"
        private const val couponLoginUrl =
            "https://login.xjtu.edu.cn/cas/oauth2.0/authorize?response_type=code&client_id=1596&redirect_uri=https%3A%2F%2Forg.xjtu.edu.cn%2Fopenplatform%2Foauth%2Fauthorizesw%3Fredirect_uri%3Dbase64aHR0cHM6Ly9lZ2MueGp0dS5lZHUuY24vcGFnZS9jYXMvcmVjZWl2ZUNhcy5odG1sP3ZlcnNpb249U0FGVF9WRVJTSU9O&state=1995"
        private const val couponBrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0"
        private const val defaultTicketTtlMs = 30L * 60 * 1000
    }
}

class CampusCasAuthBridge(
    private val api: CasAuthApi = CasAuthApi(),
) : AuthBridge {
    private val lastSessions = mutableMapOf<CampusEndpoint, CasSession>()

    override suspend fun login(username: String, password: String, service: CampusEndpoint): LoginStep {
        if (username.isBlank() || password.isBlank()) {
            return LoginStep.Failed("账号或密码为空", recoverable = true)
        }
        return when (val result = api.login(username, password, service)) {
            is CasAuthApi.CasLoginResult.Success -> {
                lastSessions[service] = result.session
                LoginStep.Success(
                    AccountProfile(
                        id = username,
                        username = username,
                        displayName = username,
                        type = AccountType.Undergraduate,
                        isActive = true,
                    ),
                )
            }
            is CasAuthApi.CasLoginResult.NeedCaptcha -> LoginStep.NeedCaptcha(result.imageBytes)
            is CasAuthApi.CasLoginResult.NeedMfa -> LoginStep.NeedMfa(result.prompt)
            is CasAuthApi.CasLoginResult.NeedAccountChoice -> LoginStep.AccountChoice(result.choices)
            is CasAuthApi.CasLoginResult.Failed -> LoginStep.Failed(result.reason, recoverable = true)
        }
    }

    fun consumeSession(service: CampusEndpoint): CasSession? =
        lastSessions.remove(service)

    override suspend fun refresh(ticket: AuthTicket): AuthTicket? =
        api.refresh(ticket)

    override suspend fun logout(accountId: String) = Unit
}

data class AuthSessionState(
    val accountId: String,
    val tickets: Map<CampusEndpoint, AuthTicket> = emptyMap(),
    val lastStep: LoginStep = LoginStep.Idle,
) {
    fun ticketFor(endpoint: CampusEndpoint): AuthTicket? =
        tickets[endpoint]?.takeUnless { it.isExpired }
}

class AuthSessionCoordinator(
    private val localStore: CampusLocalStore,
    private val bridge: AuthBridge,
) {
    suspend fun ensureTicket(accountId: String, endpoint: CampusEndpoint): LoginStep {
        val cached = readTicket(accountId, endpoint)
        if (cached != null && !cached.isExpired) return LoginStep.Loading("${endpoint.label} 登录态可用")
        val credential = localStore.getCredential(accountId)
            ?: return LoginStep.Failed("未保存账号凭据，请先登录", recoverable = true)
        val step = bridge.login(credential.username, credential.password, endpoint)
        if (step is LoginStep.Success) {
            val session = (bridge as? CampusCasAuthBridge)?.consumeSession(endpoint)
            val ticket = AuthTicket(
                accountId = accountId,
                service = endpoint,
                cookies = session?.cookies.orEmpty(),
                issuedAtEpochMs = currentEpochMillis(),
                expiresAtEpochMs = currentEpochMillis() + defaultTicketTtlMs,
            )
            writeTicket(ticket)
        }
        return step
    }

    suspend fun refreshTicket(ticket: AuthTicket): AuthTicket? =
        bridge.refresh(ticket)?.also(::writeTicket)

    fun ticketFor(accountId: String, endpoint: CampusEndpoint): AuthTicket? =
        readTicket(accountId, endpoint)?.takeUnless { it.isExpired }

    suspend fun logout(accountId: String) {
        bridge.logout(accountId)
        CampusEndpoint.entries.forEach { endpoint ->
            localStore.removeCache(ticketKey(accountId, endpoint), accountScoped = false)
        }
    }

    private fun readTicket(accountId: String, endpoint: CampusEndpoint): AuthTicket? {
        val raw = localStore.getCache(ticketKey(accountId, endpoint), accountScoped = false, allowStale = true)?.value ?: return null
        val parts = raw.split("|")
        if (parts.size < 4) return null
        val issued = parts[2].toLongOrNull() ?: return null
        val expires = parts[3].toLongOrNull()?.takeIf { it > 0 }
        val cookies = parts.getOrNull(4)?.decodeCookies().orEmpty()
        return AuthTicket(accountId, endpoint, cookies, issued, expires)
    }

    private fun writeTicket(ticket: AuthTicket) {
        val encoded = listOf(
            ticket.accountId,
            ticket.service.name,
            ticket.issuedAtEpochMs.toString(),
            (ticket.expiresAtEpochMs ?: 0L).toString(),
            ticket.cookies.encodeCookies(),
        ).joinToString("|")
        localStore.putCache(ticketKey(ticket.accountId, ticket.service), encoded, accountScoped = false)
    }

    private fun ticketKey(accountId: String, endpoint: CampusEndpoint): String =
        "auth_ticket_${accountId}_${endpoint.name}"

    companion object {
        private const val defaultTicketTtlMs = 30L * 60 * 1000
    }
}

internal expect fun currentEpochMillis(): Long
internal expect fun encryptCasPassword(password: String, publicKeyPem: String): String

private fun Map<String, String>.encodeCookies(): String =
    entries.joinToString("&") { (key, value) -> "${key.escapeCookiePart()}=${value.escapeCookiePart()}" }

private fun String.decodeCookies(): Map<String, String> {
    if (isBlank()) return emptyMap()
    return split("&").mapNotNull { pair ->
        val key = pair.substringBefore("=").unescapeCookiePart()
        val value = pair.substringAfter("=", "").unescapeCookiePart()
        if (key.isBlank()) null else key to value
    }.toMap()
}

private fun String.escapeCookiePart(): String =
    replace("%", "%25").replace("|", "%7C").replace("&", "%26").replace("=", "%3D")

private fun String.unescapeCookiePart(): String =
    replace("%3D", "=").replace("%26", "&").replace("%7C", "|").replace("%25", "%")

private fun String.normalizeBearer(): String =
    trim().removePrefix("Bearer ").removePrefix("bearer ")
