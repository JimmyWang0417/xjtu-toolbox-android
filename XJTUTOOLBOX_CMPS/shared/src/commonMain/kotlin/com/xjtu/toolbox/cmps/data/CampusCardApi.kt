package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class CampusCardApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun getCardInfo(ticket: AuthTicket): CardInfo {
        val root = getJson("$baseUrl/berserker-app/ykt/tsm/queryCard?synAccessSource=h5", ticket)
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: 0
        if (code == 401) error("校园卡登录态已失效")
        if (code != 200) error(root["message"]?.jsonPrimitive?.contentOrNull ?: "获取校园卡信息失败")
        val card = root["data"]?.jsonObject
            ?.get("card")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?: error("未找到校园卡信息")
        return CardInfo(
            account = ticket.cookies["card_account"].orEmpty(),
            name = ticket.cookies["user_name"].orEmpty(),
            studentNo = ticket.cookies["student_no"].orEmpty(),
            balance = (card["elec_accamt"]?.jsonPrimitive?.longOrNull ?: 0L) / 100.0,
            pendingAmount = (card["unsettle_amount"]?.jsonPrimitive?.longOrNull ?: 0L) / 100.0,
            lostFlag = card["barflag"]?.jsonPrimitive?.intOrNull == 1,
            frozenFlag = card["freezeflag"]?.jsonPrimitive?.intOrNull == 1,
            expireDate = formatExpDate(card["expdate"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            cardType = card["cardname"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        )
    }

    suspend fun getTransactions(ticket: AuthTicket, pageSize: Int = 50): List<RawCardTransaction> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val start = today.minus(DatePeriod(months = 3))
        val url = "$baseUrl/berserker-search/search/personal/turnover" +
            "?size=$pageSize&current=1&timeFrom=$start&timeTo=$today&synAccessSource=h5"
        val root = getJson(url, ticket)
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: 0
        if (code == 401) error("校园卡登录态已失效")
        if (code != 200) error(root["message"]?.jsonPrimitive?.contentOrNull ?: "获取校园卡流水失败")
        return root["data"]?.jsonObject
            ?.get("records")?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .map { rec ->
                val tranAmt = rec["tranamt"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val icon = rec["icon"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val turnoverType = rec["turnoverType"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val isIncome = icon == "recharge" || turnoverType.contains("充值") || turnoverType.contains("圈存")
                val merchant = rec["toMerchant"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: rec["resume"]?.jsonPrimitive?.contentOrNull.orEmpty().substringBefore("-").trim()
                RawCardTransaction(
                    time = rec["jndatetimeStr"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    merchant = merchant,
                    amount = if (isIncome) tranAmt / 100.0 else -tranAmt / 100.0,
                    balance = (rec["cardBalance"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 100.0,
                    type = turnoverType,
                    description = rec["resume"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                )
            }
    }

    private suspend fun getJson(url: String, ticket: AuthTicket) =
        Json.parseToJsonElement(
            client.get(url) {
                header("synjones-auth", "bearer ${ticket.cookies["synjones_auth"].orEmpty()}")
                header("synAccessSource", "h5")
            }.bodyAsText(),
        ).jsonObject

    private fun formatExpDate(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 8) {
            "${digits.take(4)}-${digits.drop(4).take(2)}-${digits.drop(6).take(2)}"
        } else {
            raw
        }
    }

    companion object {
        private const val baseUrl = "https://ncard.xjtu.edu.cn"
    }
}
