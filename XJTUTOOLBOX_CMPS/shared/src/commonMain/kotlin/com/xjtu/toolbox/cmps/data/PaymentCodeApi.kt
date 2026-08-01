package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PaymentCodeApi(
    private val client: HttpClient = platformHttpClient(),
) {
    private val baseUrl = "https://pay.xjtu.edu.cn"
    private val casEntry = "$baseUrl/ThirdWeb/CasQrcode"
    private val jwtRegex = Regex("""sessionStorage\.Authorization\s*=\s*'(eyJ[^']+)'""")
    private var jwtToken: String? = null

    suspend fun authenticate(): Boolean {
        val response = client.get(casEntry)
        val body = response.bodyAsText()
        jwtToken = jwtRegex.find(body)?.groupValues?.get(1)
        return jwtToken != null
    }

    suspend fun getBarCode(): String {
        val token = jwtToken ?: throw RuntimeException("未认证，请先调用 authenticate()")
        val response = client.submitForm(
            url = "$baseUrl/ThirdWeb/GetBarCode",
            formParameters = parameters { append("acctype", "000") },
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
            header("Authorization", token)
            header("Referer", casEntry)
        }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (root["IsSucceed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() != true) {
            throw RuntimeException("获取付款码失败: ${root["Msg"]?.jsonPrimitive?.content ?: "未知错误"}")
        }
        val arr = root["Obj"]?.jsonArray
        if (arr == null || arr.isEmpty()) throw RuntimeException("付款码数组为空")
        return arr[0].jsonPrimitive.content
    }
}
