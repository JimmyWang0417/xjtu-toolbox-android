package com.xjtu.toolbox.pay

import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 付款码 API — pay.xjtu.edu.cn
 * CAS OAuth 认证 → 从 HTML 提取 JWT → GetBarCode 获取动态付款码数字。
 */
class PaymentCodeApi(private val client: HttpClient) {

    companion object {
        private const val TAG = "PaymentCodeApi"
        private const val BASE_URL = "https://pay.xjtu.edu.cn"
        private const val CAS_ENTRY = "$BASE_URL/ThirdWeb/CasQrcode"
        private val JWT_REGEX = Regex("""sessionStorage\.Authorization\s*=\s*'(eyJ[^']+)'""")

        /** JWT 全局缓存 */
        private var cachedJwt: String? = null
        private var cachedJwtTime: Long = 0
        private const val JWT_TTL_MS = 30 * 60 * 1000L

        /** 清除缓存的 JWT（logout 时调用） */
        fun clearCachedJwt() {
            cachedJwt = null
            cachedJwtTime = 0
        }
    }

    private var jwtToken: String? = null

    /**
     * CAS OAuth 认证并提取 JWT。
     */
    suspend fun authenticate() {
        val cached = cachedJwt
        if (cached != null && currentTimeMillis() - cachedJwtTime < JWT_TTL_MS) {
            Logger.d(TAG, "authenticate: using cached JWT (age=${(currentTimeMillis() - cachedJwtTime) / 1000}s)")
            jwtToken = cached
            return
        }

        Logger.d(TAG, "authenticate: visiting $CAS_ENTRY")
        val resp = client.get(CAS_ENTRY)
        val body = resp.bodyAsText()
        val finalUrl = resp.call.request.url.toString()
        Logger.d(TAG, "authenticate: code=${resp.status.value}, finalUrl=$finalUrl, bodyLen=${body.length}")

        if (resp.status.value != 200 || !finalUrl.contains("pay.xjtu.edu.cn")) {
            throw RuntimeException("付款码认证失败 (code=${resp.status.value}, url=$finalUrl)")
        }

        jwtToken = JWT_REGEX.find(body)?.groupValues?.get(1)
            ?: throw RuntimeException("未找到 JWT 令牌")
        cachedJwt = jwtToken
        cachedJwtTime = currentTimeMillis()
        Logger.d(TAG, "authenticate: JWT extracted and cached (len=${jwtToken!!.length})")
    }

    /**
     * 获取付款码数字。
     * @return 付款码数字字符串
     */
    suspend fun getBarCode(): String {
        val token = jwtToken ?: throw RuntimeException("未认证，请先调用 authenticate()")

        val resp = client.submitForm(
            url = "$BASE_URL/ThirdWeb/GetBarCode",
            formParameters = parameters { append("acctype", "000") }
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
            header("Authorization", token)
            header("Referer", CAS_ENTRY)
        }

        val text = resp.bodyAsText()
        Logger.d(TAG, "getBarCode: code=${resp.status.value}, body=${text.take(200)}")

        val root = text.safeParseJsonObject()
        if (root["IsSucceed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() != true) {
            throw RuntimeException("获取付款码失败: ${root["Msg"]?.jsonPrimitive?.content ?: text.take(100)}")
        }
        val arr = root["Obj"]?.jsonArray
        if (arr == null || arr.isEmpty()) {
            throw RuntimeException("付款码数组为空")
        }
        return arr[0].jsonPrimitive.content
    }
}
