package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class CouponApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun types(ticket: AuthTicket): List<CouponType> {
        val root = postJson(ticket, "$baseUrl/app/voucher/query.type.list", """{"json":true}""")
        val data = root["data"] ?: return emptyList()
        val array = when (data) {
            is kotlinx.serialization.json.JsonArray -> data
            is JsonObject -> data["records"]?.jsonArray ?: data["list"]?.jsonArray
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.int("typeId") ?: obj.int("id") ?: obj.int("value") ?: 0
            val name = obj.str("typeName").ifBlank { obj.str("name").ifBlank { obj.str("label") } }
            if (id == 0 && name.isBlank()) null else CouponType(id, name)
        }
    }

    suspend fun page(ticket: AuthTicket, filter: CouponFilterOption?, page: Int = 1, pageSize: Int = 20): CouponPage {
        val body = """
            {
              "pageNum": $page,
              "pageSize": $pageSize,
              "obj": {
                "typeId": 4,
                "status": "${filter?.status.orEmpty()}",
                "count": "${filter?.count.orEmpty()}",
                "expired": "${filter?.expired.orEmpty()}"
              },
              "json": true
            }
        """.trimIndent()
        val root = postJson(ticket, "$baseUrl/app/voucher/query.page.list", body)
        val data = root["data"] as? JsonObject ?: return CouponPage(emptyList(), 0)
        val records = data["records"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::record) }
        return CouponPage(records, data.int("total") ?: records.size)
    }

    suspend fun detail(ticket: AuthTicket, showCardId: String): CouponDetail {
        val root = postJson(ticket, "$baseUrl/app/voucher/query.details", """{"cardId":"$showCardId","json":true}""")
        val data = root["data"] as? JsonObject
            ?: return CouponDetail(showCardId, "加餐券", "", "", 0L, 0L, "", "", "", "", "", "")
        return CouponDetail(
            showCardId = showCardId,
            voucherName = data.str("voucherName").ifBlank { "加餐券" },
            title = data.str("destitle"),
            description = data.str("describes"),
            amountFen = data.long("tranamt"),
            leftAmountFen = data.long("ltranamt"),
            startDate = data.str("startDate"),
            endDate = data.str("endDate"),
            batchId = data.str("batchId"),
            imageUrl = image(data.str("pic")),
            closedPacketImageUrl = image(data.str("rclose")),
            openPacketImageUrl = image(data.str("ropen")),
        )
    }

    private suspend fun postJson(ticket: AuthTicket, url: String, body: String): JsonObject {
        val text = client.post(url) {
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header(HttpHeaders.Authorization, ticket.cookies["coupon_token"].orEmpty())
            header("Origin", baseUrl)
            header("Referer", "$baseUrl/page/cas/receiveCas.html?version=SAFT_VERSION")
            header("X-Requested-With", "XMLHttpRequest")
            contentType(ContentType.parse("application/json;charset=UTF-8"))
            setBody(body)
        }.bodyAsText()
        if (text.contains("<html", ignoreCase = true)) error("加餐券登录态已失效")
        val root = Json.parseToJsonElement(text).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) {
            error(root["msg"]?.jsonPrimitive?.contentOrNull ?: "加餐券接口返回错误: $code")
        }
        return root
    }

    private fun record(obj: JsonObject): CouponRecord =
        CouponRecord(
            sendId = obj.str("sendId"),
            showCardId = obj.str("showCardId"),
            voucherName = obj.str("voucherName").ifBlank { "加餐券" },
            typeName = obj.str("typeName").ifBlank { "加餐券" },
            amountFen = obj.long("tranamt"),
            leftAmountFen = obj.long("ltranamt"),
            leftCount = obj.int("lknumber") ?: 0,
            startDate = obj.str("startDate"),
            endDate = obj.str("endDate"),
            imageUrl = image(obj.str("pic")),
        )

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.long(key: String): Long {
        val element: JsonElement = this[key] ?: return 0L
        val primitive = element.jsonPrimitive
        return primitive.longOrNull ?: primitive.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun image(value: String): String =
        when {
            value.isBlank() -> ""
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> baseUrl + value
            else -> "$baseUrl/$value"
        }

    companion object {
        private const val baseUrl = "https://egc.xjtu.edu.cn"
    }
}
