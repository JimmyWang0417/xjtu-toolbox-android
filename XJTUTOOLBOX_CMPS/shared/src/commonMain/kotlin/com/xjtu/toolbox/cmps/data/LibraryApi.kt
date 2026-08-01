package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LibraryApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun areas(ticket: AuthTicket): List<LibraryArea> {
        val stats = loadFloorStats(ticket, "xingqing2floor") +
            loadFloorStats(ticket, "xingqing3floor") +
            loadFloorStats(ticket, "xingqing4floor")
        return floors.flatMap { (floor, names) ->
            names.mapNotNull { name ->
                val code = areaMap[name] ?: return@mapNotNull null
                val stat = stats[code] ?: return@mapNotNull LibraryArea(floor, name, 0, 0)
                LibraryArea(floor, name, stat.available, stat.total)
            }
        }
    }

    suspend fun seats(ticket: AuthTicket, areaCode: String?): SeatResult {
        val code = areaCode ?: "north2elian"
        val floor = areaFloorCodes[code]
        if (floor != null) loadFloorStats(ticket, floor)
        val root = getJson(ticket, "$baseUrl/qseat?sp=$code", "$baseUrl/qspace?lang=zh&floor=${floor.orEmpty()}")
        val stats = parseStats(root["scount"] as? JsonObject)
        val seatObj = root["seat"] as? JsonObject ?: return SeatResult.Success(emptyList(), stats)
        val seats = seatObj.entries.map { (seatId, value) ->
            SeatInfo(seatId = seatId, available = value.jsonPrimitive.intOrNull == 0)
        }.sortedWith(compareBy({ it.seatId.firstOrNull { c -> c.isLetter() } ?: ' ' }, { it.seatId.filter(Char::isDigit).toIntOrNull() ?: 0 }))
        return SeatResult.Success(seats, stats)
    }

    private suspend fun loadFloorStats(ticket: AuthTicket, floorCode: String): Map<String, AreaStats> {
        val root = getJson(ticket, "$baseUrl/qspace?lang=zh&floor=$floorCode", "$baseUrl/seat/")
        return parseStats(root["scount"] as? JsonObject)
    }

    private suspend fun getJson(ticket: AuthTicket, url: String, referer: String): JsonObject {
        val text = client.get(url) {
            header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
            header(HttpHeaders.UserAgent, browserUa)
            header("Referer", referer)
            header("X-Requested-With", "XMLHttpRequest")
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
        }.bodyAsText()
        if (text.contains("cas/login", ignoreCase = true) || text.contains("name=\"execution\"", ignoreCase = true)) {
            error("图书馆登录态已失效")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun parseStats(raw: JsonObject?): Map<String, AreaStats> {
        if (raw == null) return emptyMap()
        return raw.mapNotNull { (code, value) ->
            val arr = value as? JsonArray ?: return@mapNotNull null
            if (code !in validAreaCodes || arr.size < 2) return@mapNotNull null
            val total = arr[0].jsonPrimitive.intOrNull ?: 0
            val available = arr[1].jsonPrimitive.intOrNull ?: 0
            code to AreaStats(available, total)
        }.toMap()
    }

    private fun Map<String, String>.toCookieHeader(): String =
        entries.joinToString("; ") { (key, value) -> "$key=$value" }

    companion object {
        private const val baseUrl = "http://rg.lib.xjtu.edu.cn:8086"
        private const val browserUa =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val areaMap = linkedMapOf(
            "北楼二层外文库（东）" to "north2east",
            "二层连廊及流通大厅" to "north2elian",
            "北楼二层外文库（西）" to "north2west",
            "南楼二层大厅" to "south2",
            "北楼三层ILibrary-B（西）" to "west3B",
            "大屏辅学空间" to "eastnorthda",
            "南楼三层中段" to "south3middle",
            "北楼三层ILibrary-A（东）" to "east3A",
            "北楼四层西侧" to "north4west",
            "北楼四层中间" to "north4middle",
            "北楼四层东侧" to "north4east",
            "北楼四层西南侧" to "north4southwest",
            "北楼四层东南侧" to "north4southeast",
        )
        private val floors = linkedMapOf(
            "二楼" to listOf("北楼二层外文库（东）", "二层连廊及流通大厅", "北楼二层外文库（西）", "南楼二层大厅"),
            "三楼" to listOf("北楼三层ILibrary-B（西）", "大屏辅学空间", "南楼三层中段", "北楼三层ILibrary-A（东）"),
            "四楼" to listOf("北楼四层西侧", "北楼四层中间", "北楼四层东侧", "北楼四层西南侧", "北楼四层东南侧"),
        )
        private val floorCodes = mapOf("二楼" to "xingqing2floor", "三楼" to "xingqing3floor", "四楼" to "xingqing4floor")
        private val areaFloorCodes = buildMap {
            floors.forEach { (floor, areas) ->
                val floorCode = floorCodes[floor] ?: return@forEach
                areas.forEach { area -> areaMap[area]?.let { put(it, floorCode) } }
            }
        }
        private val validAreaCodes = areaMap.values.toSet()
    }
}
