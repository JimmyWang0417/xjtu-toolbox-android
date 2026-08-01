package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class VenueApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun venues(ticket: AuthTicket): List<Venue> {
        val html = getText(ticket, "$appBase/product/index.html", appBase)
        if (isAuthFailure(html)) error("场馆登录态已失效")
        val venues = parseVenues(html)
        if (venues.isEmpty()) error("场馆列表为空或页面结构已变化")
        return venues
    }

    suspend fun availableAreaSlots(ticket: AuthTicket, serviceId: Int, date: String = todayString()): List<VenueAreaSlot> {
        val text = getText(
            ticket = ticket,
            url = "$appBase/product/findOkArea.html?s_date=$date&serviceid=$serviceId&_=${currentEpochMillis()}",
            referer = "$appBase/product/show.html?id=$serviceId",
            accept = "application/json, text/javascript, */*; q=0.01",
        )
        if (isAuthFailure(text)) error("场馆登录态已失效")
        return parseAreaSlots(text, date, serviceId.toString())
    }

    suspend fun lockedAreaSlots(ticket: AuthTicket, serviceId: Int, date: String = todayString()): List<VenueAreaSlot> {
        val text = getText(
            ticket = ticket,
            url = "$appBase/product/findLockArea.html?s_date=$date&serviceid=$serviceId&_=${currentEpochMillis()}",
            referer = "$appBase/product/show.html?id=$serviceId",
            accept = "application/json, text/javascript, */*; q=0.01",
        )
        if (isAuthFailure(text)) error("场馆登录态已失效")
        return parseAreaSlots(text, date, serviceId.toString())
    }

    suspend fun slots(ticket: AuthTicket, venue: Venue? = null, date: String = todayString()): List<VenueSlot> {
        val target = venue ?: venues(ticket).firstOrNull() ?: return emptyList()
        return availableAreaSlots(ticket, target.id, date).mapNotNull { slot ->
            val parts = slot.timeSlot.split("-", limit = 2)
            val start = parts.getOrNull(0)?.trim()?.parseVenueTime() ?: return@mapNotNull null
            val end = parts.getOrNull(1)?.trim()?.parseVenueTime() ?: return@mapNotNull null
            VenueSlot(
                venue = "${target.name} ${slot.areaName}".trim(),
                date = LocalDate.parse(slot.date),
                start = start,
                end = end,
                available = slot.isAvailable,
            )
        }
    }

    private suspend fun getText(
        ticket: AuthTicket,
        url: String,
        referer: String,
        accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    ): String =
        client.get(url) {
            header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
            header(HttpHeaders.UserAgent, browserUa)
            header(HttpHeaders.Accept, accept)
            header("Referer", referer)
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()

    private fun parseVenues(html: String): List<Venue> {
        val blocks = Regex("""<li\b[^>]*>.*?</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .map { it.value }
        return blocks.mapNotNull { block ->
            val href = Regex("""href=["']([^"']*show\.html\?id=\d+[^"']*)["']""", RegexOption.IGNORE_CASE)
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?: return@mapNotNull null
            val id = Regex("""id=(\d+)""").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val name = Regex("""<h5\b[^>]*>(.*?)</h5>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.stripHtml()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val address = Regex("""class=["'][^"']*\baddress\b[^"']*["'][^>]*>(.*?)</""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
                ?.stripHtml()
                ?.removePrefix("地址:")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val iconType = Regex("""class=["'][^"']*\bicon-(\w+)\b[^"']*["']""", RegexOption.IGNORE_CASE)
                .find(block)
                ?.groupValues
                ?.getOrNull(1)
            Venue(id, name, address, iconType)
        }.toList()
    }

    private fun parseAreaSlots(text: String, date: String, serviceId: String): List<VenueAreaSlot> {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val array = root["object"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val stock = obj["stock"] as? JsonObject ?: return@mapNotNull null
            val areaId = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val stockId = obj["stockid"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            VenueAreaSlot(
                areaId = areaId,
                areaName = obj["sname"]?.jsonPrimitive?.content.orEmpty().ifBlank { "场地" },
                stockId = stockId,
                timeSlot = stock["time_no"]?.jsonPrimitive?.content.orEmpty(),
                price = stock["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                date = date,
                status = stock["status"]?.jsonPrimitive?.intOrNull ?: 0,
                allCount = stock["all_count"]?.jsonPrimitive?.intOrNull ?: 0,
                usingNum = stock["using_num"]?.jsonPrimitive?.intOrNull ?: 0,
                serviceId = serviceId,
            )
        }
    }

    private fun String.parseVenueTime(): LocalTime? =
        runCatching { LocalTime.parse(this) }
            .recoverCatching { LocalTime.parse("$this:00") }
            .getOrNull()

    private fun String.stripHtml(): String =
        replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun Map<String, String>.toCookieHeader(): String =
        entries.joinToString("; ") { (key, value) -> "$key=$value" }

    private fun isAuthFailure(text: String): Boolean =
        text.contains("login.xjtu.edu.cn/cas/login", ignoreCase = true) ||
            text.contains("name=\"execution\"", ignoreCase = true) ||
            text.contains("统一身份认证", ignoreCase = true)

    private fun todayString(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    companion object {
        const val base = "http://202.117.17.144"
        const val appBase = "http://202.117.17.144:8071"
        const val oauthLoginUrl =
            "https://login.xjtu.edu.cn/cas/oauth2.0/authorize?" +
                "response_type=code&client_id=1439&" +
                "redirect_uri=https%3A%2F%2Forg.xjtu.edu.cn%2Fopenplatform%2Foauth%2Fauthorizesw" +
                "%3Fredirect_uri%3Dhttp%3A%2F%2F202.117.17.144%2Fxjtu%2Fcas%2Foauth2url.html&" +
                "state=1"
        private const val browserUa =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
