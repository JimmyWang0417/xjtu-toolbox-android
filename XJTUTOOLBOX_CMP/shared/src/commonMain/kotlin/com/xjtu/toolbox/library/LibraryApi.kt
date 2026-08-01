package com.xjtu.toolbox.library

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.xjtu.toolbox.auth.LibraryLogin
import com.xjtu.toolbox.util.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.ceil

// ══════ 数据类 ══════

data class SeatInfo(val seatId: String, val available: Boolean)

data class AreaStats(val available: Int, val total: Int) {
    val isOpen get() = total > 0
    val label get() = "${available}/${total}"
}

data class BookResult(
    val success: Boolean, val message: String, val finalUrl: String = ""
)

data class RecommendPrefs(
    val emptinessWeight: Int = 3,
    val allEmptyBonus: Int = 5,
    val isolationLevel: Int = 2,
    val avoidEntrancePenalty: Int = 2,
    val avoidExitPenalty: Int = 0,
    val avoidAdjacentBusyPenalty: Int = 3,
    val wallBias: Int = 1,
    val cornerBias: Int = 1,
    val corridorSidePenalty: Int = 2,
    val facingWallBias: Int = 0,
    val avoidFacingCrowdBias: Int = 0,
    val enableTimeSlotAdjust: Boolean = false,
    val preferredGridXRange: IntRange? = null,
    val preferredGridYRange: IntRange? = null,
    val historyBias: Int = 0,
)

data class MyBookingInfo(
    val seatId: String?,
    val area: String?,
    val statusText: String?,
    val actionUrls: Map<String, String>
)

sealed class SeatResult {
    data class Success(
        val seats: List<SeatInfo>,
        val areaStatsMap: Map<String, AreaStats> = emptyMap()
    ) : SeatResult()
    data class AuthError(val message: String, val htmlPreview: String = "") : SeatResult()
    data class Error(val message: String) : SeatResult()
}

// ══════ LibraryApi ══════

class LibraryApi(private val login: LibraryLogin) {

    companion object {
        private const val BASE_URL = "http://rg.lib.xjtu.edu.cn:8086"
        private const val TAG = "LibraryApi"

        val AREA_MAP = linkedMapOf(
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
            "北楼四层东南侧" to "north4southeast"
        )

        val FLOORS = linkedMapOf(
            "二楼" to listOf("北楼二层外文库（东）", "二层连廊及流通大厅", "北楼二层外文库（西）", "南楼二层大厅"),
            "三楼" to listOf("北楼三层ILibrary-B（西）", "大屏辅学空间", "南楼三层中段", "北楼三层ILibrary-A（东）"),
            "四楼" to listOf("北楼四层西侧", "北楼四层中间", "北楼四层东侧", "北楼四层西南侧", "北楼四层东南侧")
        )

        private val VALID_AREA_CODES = AREA_MAP.values.toSet()
        val AREA_MAP_REVERSE = AREA_MAP.entries.associate { (name, code) -> code to name }
        val SEAT_ID_REGEX = Regex("""(?:[A-Z]\d{2,4}|\b\d{3}\b)""")

        fun filterScount(raw: Map<String, AreaStats>): Map<String, AreaStats> =
            raw.filterKeys { it in VALID_AREA_CODES }

        fun guessAreaCode(seatId: String): String? {
            val prefix = seatId.firstOrNull()?.uppercaseChar() ?: return null
            return when (prefix) {
                'A', 'B' -> "north2elian"; 'D', 'E' -> "north2east"
                'C' -> "south2"; 'N' -> "north2west"
                'Y' -> "west3B"; 'P' -> "eastnorthda"; 'X' -> "east3A"
                'K', 'L', 'M' -> "north4west"; 'J' -> "north4middle"
                'H', 'F', 'G' -> "north4east"
                'Q' -> "north4southwest"; 'T' -> "north4southeast"
                else -> null
            }
        }
    }

    var cachedAreaStats: Map<String, AreaStats> = emptyMap()
        private set

    private fun isRedirectedToLogin(body: String, finalUrl: String): Boolean =
        body.contains("id=\"loginForm\"") || body.contains("name=\"execution\"") ||
        body.contains("cas/login") || finalUrl.contains("login.xjtu.edu.cn")

    private suspend fun executeWithReAuth(url: String, ajax: Boolean = false): Pair<String, String> {
        val resp = login.client.get(url) {
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            header("Referer", "$BASE_URL/seat/")
            if (ajax) {
                header("X-Requested-With", "XMLHttpRequest")
                header("Accept", "application/json, text/javascript, */*; q=0.01")
            }
        }
        val body = resp.bodyAsText()
        val finalUrl = resp.call.request.url.toString()

        if (isRedirectedToLogin(body, finalUrl)) {
            Logger.d(TAG, "executeWithReAuth: redirected to login, trying reAuthenticate...")
            if (login.reAuthenticate()) {
                val retryResp = login.client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    header("Referer", "$BASE_URL/seat/")
                    if (ajax) {
                        header("X-Requested-With", "XMLHttpRequest")
                        header("Accept", "application/json, text/javascript, */*; q=0.01")
                    }
                }
                return retryResp.bodyAsText() to retryResp.call.request.url.toString()
            }
        }
        return body to finalUrl
    }

    // ── 座位查询 ──

    suspend fun getSeats(areaCode: String): SeatResult {
        if (!login.seatSystemReady && login.diagnosticInfo.isNotEmpty()) {
            return SeatResult.AuthError(
                "座位系统认证未完成\n${login.diagnosticInfo}\n\n请确认：\n1. 已连接校园网或 VPN\n2. 图书馆系统在服务时间内"
            )
        }

        val body: String
        try {
            val (b, _) = executeWithReAuth("$BASE_URL/qseat?sp=$areaCode", ajax = true)
            body = b
        } catch (e: Exception) {
            Logger.e(TAG, "getSeats network error", e)
            return SeatResult.Error("网络请求失败: ${e.message}")
        }

        if (body.length < 10)
            return SeatResult.Error("服务器返回异常")

        try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject

            // 解析 scount
            val statsMap = mutableMapOf<String, AreaStats>()
            val scountObj = json["scount"]?.jsonObject
            if (scountObj != null) {
                for ((key, value) in scountObj) {
                    if (key.isBlank()) continue
                    val arr = try { value.jsonArray } catch (_: Exception) { continue }
                    if (arr.size >= 2) {
                        statsMap[key] = AreaStats(arr[0].jsonPrimitive.int, arr[1].jsonPrimitive.int)
                    }
                }
            }
            cachedAreaStats = filterScount(statsMap)
            Logger.d(TAG, "scount: ${cachedAreaStats.size} areas open")

            // 解析 seat
            val seatObj = json["seat"]?.jsonObject
            if (seatObj == null || seatObj.isEmpty()) {
                return SeatResult.Success(emptyList(), cachedAreaStats)
            }

            val seatList = mutableListOf<SeatInfo>()
            for ((seatId, statusEl) in seatObj) {
                val status = try { statusEl.jsonPrimitive.int } catch (_: Exception) { -1 }
                seatList.add(SeatInfo(seatId, status == 0))
            }

            seatList.sortWith(compareBy(
                { it.seatId.firstOrNull { c -> c.isLetter() } ?: ' ' },
                { it.seatId.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            ))

            Logger.d(TAG, "seats: ${seatList.size} total, ${seatList.count { it.available }} avail")
            return SeatResult.Success(seatList, cachedAreaStats)
        } catch (e: Exception) {
            Logger.e(TAG, "JSON parse error", e)
            return SeatResult.Error("座位数据解析失败: ${e.message}")
        }
    }

    // ── 预约座位 ──

    suspend fun bookSeat(seatId: String, areaCode: String, autoSwap: Boolean = true): BookResult {
        val url = "$BASE_URL/seat/?kid=$seatId&sp=$areaCode"
        val html: String
        val finalUrl: String
        try {
            val (h, f) = executeWithReAuth(url)
            html = h; finalUrl = f
        } catch (e: Exception) {
            return BookResult(false, "网络异常: ${e.message}")
        }

        val success = "/my/" in finalUrl || "/seat/my/" in finalUrl
        if (success) return BookResult(true, "✓ 座位 $seatId 预约成功！", finalUrl)

        if (autoSwap) {
            val bodyText = Ksoup.parse(html).body()?.text() ?: ""
            if ("已有预约" in bodyText || "已预约" in bodyText || "换座" in bodyText
                || "已经预约" in bodyText || "存在预约" in bodyText) {
                Logger.d(TAG, "bookSeat: existing booking detected, using /updateseat/ endpoint")
                return swapSeat(seatId, areaCode)
            }
        }

        val reason = parseBookingFailure(html)
        return BookResult(false, reason, finalUrl)
    }

    suspend fun swapSeat(seatId: String, areaCode: String): BookResult {
        val url = "$BASE_URL/updateseat/?kid=$seatId&sp=$areaCode"
        Logger.d(TAG, "swapSeat: $url")
        return try {
            val (html, finalUrl) = executeWithReAuth(url)
            val success = "/my/" in finalUrl || "成功换座" in html || "成功" in html
            if (success) BookResult(true, "✓ 已换座到 $seatId！", finalUrl)
            else BookResult(false, "换座失败: ${parseBookingFailure(html)}", finalUrl)
        } catch (e: Exception) {
            BookResult(false, "换座请求失败: ${e.message}")
        }
    }

    private fun parseBookingFailure(html: String): String {
        val doc = Ksoup.parse(html)
        val alertText = doc.select(".alert, .error, .msg, .message, .warn, .notice, #msg, .tip").text()
        if (alertText.isNotBlank()) return alertText

        val bodyText = doc.body()?.text() ?: ""
        return when {
            "30分钟" in bodyText || "30 min" in bodyText -> "30 分钟内不能重复预约\n‣ 取消后 30 分钟内不能重新预约"
            "已被预约" in bodyText || "已被占" in bodyText -> "该座位已被他人预约\n‣ 已自动刷新座位列表"
            "已有预约" in bodyText || "已预约" in bodyText -> "您已有其他座位预约\n‣ 如需更换，请先取消当前预约"
            "不在预约时间" in bodyText || "未开放" in bodyText -> "当前不在预约开放时间\n‣ 预约通常在 22:00 开放次日抢座"
            "维护" in bodyText -> "系统维护中，请稍后再试"
            isRedirectedToLogin(html, "") -> "登录状态过期，请返回重新登录"
            else -> "预约失败（未知原因）"
        }
    }

    // ── 我的预约 ──

    suspend fun getMyBooking(): MyBookingInfo? {
        val mainHtml: String
        val mainFinalUrl: String
        try {
            val (h, f) = executeWithReAuth("$BASE_URL/seat/")
            mainHtml = h; mainFinalUrl = f
        } catch (e: Exception) {
            Logger.e(TAG, "getMyBooking: failed to load seat main page", e)
            return null
        }

        if (mainHtml.length < 50 || isRedirectedToLogin(mainHtml, mainFinalUrl)) return null

        val mainDoc = Ksoup.parse(mainHtml, mainFinalUrl)
        val myBookingLink = mainDoc.select("a").firstOrNull { el ->
            val text = el.text().trim()
            "预约的座" in text || "我的预约" in text || "mybooking" in el.attr("href").lowercase()
        }?.attr("abs:href")?.ifBlank { null }

        val candidateUrls = buildList {
            myBookingLink?.let { add(it) }
            add("$BASE_URL/my/")
            add("$BASE_URL/seat/my/")
            add("$BASE_URL/seat/my")
        }.distinct()

        for (url in candidateUrls) {
            try {
                val (html, finalUrl) = executeWithReAuth(url)
                if (html.length < 50 || isRedirectedToLogin(html, finalUrl)) continue

                val doc = Ksoup.parse(html, finalUrl)
                val bodyText = doc.body()?.text() ?: ""
                Logger.d(TAG, "my page try $url body (500): ${bodyText.take(500)}")

                if ("Not Found" in bodyText && bodyText.length < 800) continue

                val hasSeatId = SEAT_ID_REGEX.containsMatchIn(bodyText)

                if (!hasSeatId) {
                    if (listOf("暂无", "没有预约", "无预约", "暂无预约").any { it in bodyText }) {
                        Logger.d(TAG, "getMyBooking: no active booking at $url")
                        return null
                    }
                    continue
                }

                val result = parseActiveBooking(doc, bodyText, html)
                if (result != null) return result

                val hasStatus = "预约状态" in bodyText
                if (hasStatus) {
                    Logger.d(TAG, "getMyBooking: all bookings cancelled at $url")
                    return null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "getMyBooking: error trying $url", e)
            }
        }

        Logger.d(TAG, "getMyBooking: no booking found across all candidate URLs")
        return null
    }

    private fun parseActiveBooking(
        doc: Document, bodyText: String, html: String
    ): MyBookingInfo? {
        val inactiveStatuses = setOf("已取消", "已完成", "已过期", "已失效", "已违约", "超时取消", "超时未入馆", "超时", "已离馆")
        val statusRegex = Regex("""预约状态[:：]\s*(\S+)""")
        val statusMatches = statusRegex.findAll(bodyText).toList()

        if (statusMatches.isEmpty()) {
            val seatId = SEAT_ID_REGEX.find(bodyText)?.value ?: return null
            val area = AREA_MAP.keys.firstOrNull { it in bodyText }
            val actionUrls = parseActionsFromHtml(doc, html)
            Logger.d(TAG, "getMyBooking (no status): seatId=$seatId, area=$area")
            return MyBookingInfo(seatId, area, null, actionUrls)
        }

        var blockStart = 0
        for (statusMatch in statusMatches) {
            val status = statusMatch.groupValues[1]
            val blockEnd = statusMatch.range.last + 1
            val blockText = bodyText.substring(blockStart, blockEnd)

            if (status in inactiveStatuses) { blockStart = blockEnd; continue }

            val seatId = SEAT_ID_REGEX.findAll(blockText).lastOrNull()?.value
            if (seatId == null) { blockStart = blockEnd; continue }

            val area = AREA_MAP.keys.firstOrNull { it in blockText }
            val actionUrls = parseActionsFromHtml(doc, html)

            Logger.d(TAG, "getMyBooking: seatId=$seatId, area=$area, status=$status, actions=${actionUrls.keys}")
            return MyBookingInfo(seatId, area, status, actionUrls)
        }

        return null
    }

    private fun parseActionsFromHtml(doc: Document, html: String): MutableMap<String, String> {
        val actionUrls = mutableMapOf<String, String>()
        val navTexts = setOf("座位预约", "我预约的座位", "我预约的图书", "跨校", "提存", "典藏", "意见反馈",
            "资料修改", "活动查询", "注销", "常见问题", "其他功能", "Toggle navigation", "首页",
            "English version", "确认操作", "确认", "取消", "×")

        doc.body()?.select("a[href], button[onclick], a[onclick], a[data-href], a[data-url]")?.forEach { el ->
            val text = el.text().trim()
            if (text.isBlank() || text in navTexts || text.length > 15) return@forEach
            if ("logout" in (el.attr("href") + el.attr("onclick")).lowercase()) return@forEach

            val realUrl = el.attr("data-href").ifBlank { null }
                ?: el.attr("data-url").ifBlank { null }
                ?: el.attr("data-action").ifBlank { null }
                ?: extractUrlFromOnclick(el.attr("onclick"))
                ?: el.attr("abs:href").let { href ->
                    if (href.isBlank() || href.endsWith("#") || href == "#" || "javascript:" in href) null
                    else href
                }

            val label = classifyActionLabel(text) ?: return@forEach
            if (realUrl != null) {
                actionUrls[label] = realUrl
                Logger.d(TAG, "getMyBooking action found: $label -> $realUrl (from DOM)")
            }
        }

        doc.select("form[action]").forEach { form ->
            val action = form.attr("abs:action").ifBlank { return@forEach }
            val submitText = form.select("button[type=submit], input[type=submit]").firstOrNull()?.let {
                it.text().ifBlank { it.attr("value") }
            } ?: return@forEach
            val label = classifyActionLabel(submitText)
            if (label != null && action.isNotBlank() && !action.endsWith("#")) {
                actionUrls[label] = action
            }
        }

        if (actionUrls.isEmpty() || "取消预约" !in actionUrls) {
            extractActionsFromScripts(doc).forEach { (label, url) ->
                if (label !in actionUrls) actionUrls[label] = url
            }
        }

        return actionUrls
    }

    private fun classifyActionLabel(text: String): String? = when {
        "取消" in text && "预约" in text -> "取消预约"
        "线上签到" in text -> "入馆签到"
        "首次入馆" in text || "入馆" in text && "离" !in text && "返" !in text -> "入馆签到"
        "签到" in text && "回馆" !in text && "离" !in text && "返" !in text -> "入馆签到"
        "中途离开" in text -> "中途离开"
        "离馆" in text || "暂离" in text || "中途离" in text -> "中途离开"
        "中途返回" in text -> "中途返回"
        "回馆" in text || "返回签到" in text || "中途返" in text -> "中途返回"
        "换座" in text -> "我想换座"
        "取消" in text -> "取消预约"
        else -> null
    }

    private fun extractUrlFromOnclick(onclick: String?): String? {
        if (onclick.isNullOrBlank()) return null
        Regex("""(?:location\.href|location|window\.location)\s*=\s*['"]([^'"]+)['"]""")
            .find(onclick)?.groupValues?.get(1)?.let { return it }
        Regex("""showConfirmModal\s*\(\s*['"][^'"]*['"]\s*,\s*'(\w+)'\s*,\s*'(\d+)'\s*\)""")
            .find(onclick)?.let { match ->
                val action = match.groupValues[1]; val id = match.groupValues[2]
                return buildActionUrl(action, id)
            }
        Regex("""['"](/[^'"]+)['"]""").find(onclick)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private fun buildActionUrl(action: String, reserveId: String): String? = when (action) {
        "cancel" -> "$BASE_URL/my/?cancel=1&ri=$reserveId"
        "ruguan1" -> "$BASE_URL/my/?firstruguan=1&ri=$reserveId"
        "leave", "midleave" -> "$BASE_URL/my/?midleave=1&ri=$reserveId"
        "return", "midreturn" -> "$BASE_URL/my/?midreturn=1&ri=$reserveId"
        else -> { Logger.w(TAG, "Unknown showConfirmModal action: $action (ri=$reserveId)"); null }
    }

    private fun extractActionsFromScripts(doc: Document): Map<String, String> {
        val found = mutableMapOf<String, String>()
        doc.select("script").forEach { script ->
            val code = script.data()
            if (code.length < 20) return@forEach

            Regex("""showConfirmModal\s*\(\s*['"][^'"]*['"]\s*,\s*'(\w+)'\s*,\s*'(\d+)'\s*\)""")
                .findAll(code).forEach { match ->
                    val action = match.groupValues[1]; val id = match.groupValues[2]
                    val url = buildActionUrl(action, id)
                    if (url != null) {
                        val label = when (action) {
                            "cancel" -> "取消预约"; "ruguan1" -> "入馆签到"
                            "leave", "midleave" -> "中途离开"; "return", "midreturn" -> "中途返回"
                            else -> null
                        }
                        if (label != null && label !in found) found[label] = url
                    }
                }

            Regex("""['"](/my/\?(?:cancel|firstruguan|midleave|midreturn)=1&ri=)\s*['"]?\s*\+?\s*(?:['"]?(\d+)['"]?|(\w+))""")
                .findAll(code).forEach { match ->
                    val urlPrefix = match.groupValues[1]; val directId = match.groupValues[2]
                    if (directId.isNotEmpty()) {
                        val fullUrl = "$BASE_URL$urlPrefix$directId"
                        val label = when {
                            "cancel" in urlPrefix -> "取消预约"; "firstruguan" in urlPrefix -> "入馆签到"
                            "midleave" in urlPrefix -> "中途离开"; "midreturn" in urlPrefix -> "中途返回"
                            else -> null
                        }
                        if (label != null && label !in found) found[label] = fullUrl
                    }
                }

            listOf(
                Regex("""['"]([^'"]*(?:cancel|quxiao|取消)[^'"]*(?:reserve|booking|seat)?[^'"]*)['"]"""),
                Regex("""url\s*[:=]\s*['"]([^'"]*cancel[^'"]+)['"]"""),
                Regex("""['"](/my/cancel[^'"]*)['"]""")
            ).forEach { pattern ->
                pattern.find(code)?.groupValues?.get(1)?.let { url ->
                    if (url.startsWith("/") && url.length > 2 && "取消预约" !in found)
                        found["取消预约"] = "$BASE_URL$url"
                }
            }
            listOf(
                Regex("""['"]([^'"]*(?:checkin|signin|签到|firstruguan)[^'"]*)['"]"""),
                Regex("""['"](/my/checkin[^'"]*)['"]"""),
                Regex("""['"](/my/\?firstruguan[^'"]*)['"]""")
            ).forEach { pattern ->
                pattern.find(code)?.groupValues?.get(1)?.let { url ->
                    if (url.startsWith("/") && url.length > 2 && "入馆签到" !in found)
                        found["入馆签到"] = "$BASE_URL$url"
                }
            }
        }
        return found
    }

    suspend fun executeAction(actionUrl: String): BookResult {
        val normalizedUrl = if (actionUrl.startsWith("/")) "$BASE_URL$actionUrl" else actionUrl
        try {
            val (html, finalUrl) = executeWithReAuth(normalizedUrl)
            val doc = Ksoup.parse(html)
            val bodyText = doc.body()?.text() ?: ""
            val msg = doc.select(".alert, .msg, .message, .success, .error").text()
            val success = listOf("成功", "success", "已取消", "取消成功").any { it in bodyText.lowercase() }
                    || "/my/" in finalUrl
            return BookResult(success, msg.ifBlank { if (success) "操作成功" else "操作可能未生效" }, finalUrl)
        } catch (e: Exception) {
            return BookResult(false, "操作失败: ${e.message}")
        }
    }

    // ── 座位推荐 ──

    private data class ScoreEntry(val seat: SeatInfo, val score: Int)

    private fun seatRegion(id: String): String = when (id.firstOrNull()) {
        'F', 'H' -> "FH"; 'K', 'L' -> "KL"
        else -> id.takeWhile { it.isLetter() }
    }

    private fun adjacentUnitOccupancyRate(
        pos: SeatNeighborData.SeatPosition, unitDelta: Int, region: String,
        posCache: Map<SeatInfo, SeatNeighborData.SeatPosition>, seatMap: Map<String, Boolean>
    ): Double {
        if (pos.rowIndex < 0 || pos.unitIndexInRow < 0) return 0.0
        val targetUnit = pos.unitIndexInRow + unitDelta
        if (targetUnit < 0 || targetUnit >= pos.rowLength) return 0.0
        val unitSeats = posCache.filter { (s, p) ->
            p.rowIndex == pos.rowIndex && p.unitIndexInRow == targetUnit && seatRegion(s.seatId) == region
        }
        if (unitSeats.isEmpty()) return 0.0
        val occupied = unitSeats.count { (s, _) -> seatMap[s.seatId] == false }
        return occupied.toDouble() / unitSeats.size
    }

    fun recommendSeats(
        seats: List<SeatInfo>, areaCode: String,
        topN: Int = 5, prefs: RecommendPrefs = RecommendPrefs()
    ): List<SeatInfo> {
        val available = seats.filter { it.available }
        if (available.size <= topN) return available

        if (SeatNeighborData.getSeatPosition(available.first().seatId, areaCode).rowLength == 0)
            return emptyList()

        val seatMap = seats.associate { it.seatId to it.available }
        val posCache = seats.associateWith { SeatNeighborData.getSeatPosition(it.seatId, areaCode) }

        val corridorBenchCache: Set<String> = when {
            areaCode.endsWith("southwest") || areaCode.endsWith("southeast") ->
                available.filter { s ->
                    val n = s.seatId.toIntOrNull() ?: return@filter false
                    n in 65..136 && (136 - n) % 8 >= 4
                }.map { it.seatId }.toHashSet()
            else -> emptySet()
        }
        val corridorBenchColCache: Set<String> = when {
            corridorBenchCache.isNotEmpty() ->
                available.filter { s ->
                    val n = s.seatId.toIntOrNull() ?: return@filter false
                    if (n !in 65..136) return@filter false
                    val rem = (136 - n) % 8
                    rem == 7 || rem == 6
                }.map { it.seatId }.toHashSet()
            else -> emptySet()
        }

        val scores = available.map { seat ->
            val neighbors = SeatNeighborData.getNeighborSeats(seat.seatId, areaCode)
            val known = neighbors.filter { it in seatMap }
            val avail = known.count { seatMap[it] == true }
            val occupied = known.size - avail
            val norm = when {
                known.isEmpty() -> 1f
                neighbors.isNotEmpty() && known.size < neighbors.size / 2 -> 6f / 4
                else -> 6f / known.size
            }
            var score = (avail * prefs.emptinessWeight * norm).toInt()
            if (known.isNotEmpty() && occupied == 0) score += prefs.allEmptyBonus
            if (neighbors.size <= 2) score += 1

            val basePenalty = if (occupied == 0) 0 else
                maxOf(occupied * prefs.isolationLevel, (occupied * prefs.isolationLevel * norm).toInt())
            val squeezed = norm >= 1f && occupied > 0 && occupied * 2 >= known.size
            score -= if (squeezed) basePenalty * 2 else basePenalty
            if (known.isNotEmpty() && avail == 0) score -= prefs.allEmptyBonus

            val pos = posCache[seat] ?: SeatNeighborData.getSeatPosition(seat.seatId, areaCode)
            if (occupied > 0 && pos.gridY >= 0 && pos.isBiEntrance) {
                val occupiedGridYs = known
                    .filter { seatMap[it] == false }
                    .mapNotNull { id -> seats.find { s -> s.seatId == id }?.let { s -> posCache[s]?.gridY } }
                    .filter { it >= 0 }
                if (occupiedGridYs.isNotEmpty()) {
                    val minDist = occupiedGridYs.minOf { abs(it - pos.gridY) }
                    score += minDist.coerceAtMost(5)
                }
            }

            if (pos.isNearEntrance) score -= prefs.avoidEntrancePenalty
            val isExitEnd = pos.unitIndexInRow in 0..1 ||
                (pos.isBiEntrance && pos.rowLength > 0 && pos.unitIndexInRow >= pos.rowLength - 2)
            if (isExitEnd) score -= prefs.avoidExitPenalty

            if (prefs.avoidAdjacentBusyPenalty > 0) {
                val region = seatRegion(seat.seatId)
                val leftRate = adjacentUnitOccupancyRate(pos, -1, region, posCache, seatMap).toFloat()
                val rightRate = adjacentUnitOccupancyRate(pos, +1, region, posCache, seatMap).toFloat()
                if (leftRate > 0f) score -= maxOf(1, (leftRate * prefs.avoidAdjacentBusyPenalty * 3).toInt()).coerceAtMost(8)
                if (rightRate > 0f) score -= maxOf(1, (rightRate * prefs.avoidAdjacentBusyPenalty * 3).toInt()).coerceAtMost(8)
            }

            val neighborsSet = neighbors.toSet()
            if (prefs.avoidAdjacentBusyPenalty > 0 && pos.rowLength > 0 && pos.unitIndexInRow >= 0) {
                val region = seatRegion(seat.seatId)
                val buddySeats = posCache.filter { (s, p) ->
                    p.rowIndex == pos.rowIndex && p.unitIndexInRow == pos.unitIndexInRow
                        && seatRegion(s.seatId) == region
                        && s.seatId !in neighborsSet && s.seatId != seat.seatId
                }
                if (buddySeats.isNotEmpty()) {
                    val buddyOccupied = buddySeats.count { (s, _) -> seatMap[s.seatId] == false }
                    if (buddyOccupied > 0) {
                        val buddyRate = buddyOccupied.toFloat() / buddySeats.size
                        score -= maxOf(1, (buddyRate * prefs.avoidAdjacentBusyPenalty * 2).toInt())
                        if (occupied > 0) {
                            score -= (occupied + buddyOccupied) * prefs.isolationLevel * 2
                        }
                    }
                }
            }

            if (prefs.wallBias != 0) score += (pos.wallProximityScore * prefs.wallBias).toInt()
            if (pos.isCornerUnit) score += prefs.cornerBias
            if (pos.isCorridorSide && prefs.corridorSidePenalty != 0)
                score -= prefs.corridorSidePenalty
            if (seat.seatId in corridorBenchCache && prefs.corridorSidePenalty != 0) {
                score -= prefs.corridorSidePenalty
                if (seat.seatId in corridorBenchColCache)
                    score -= prefs.corridorSidePenalty
            }
            if (!pos.isWallSide && pos.facingDir != SeatNeighborData.FacingDir.UNKNOWN)
                score += prefs.facingWallBias
            if (pos.acrossCount > 0 && prefs.avoidFacingCrowdBias != 0)
                score -= ceil(pos.acrossCount * prefs.avoidFacingCrowdBias / 4.0).toInt()

            if (prefs.historyBias > 0 && pos.gridX >= 0 && pos.gridY >= 0) {
                val inX = prefs.preferredGridXRange?.contains(pos.gridX) == true
                val inY = prefs.preferredGridYRange?.contains(pos.gridY) == true
                if (inX && inY) score += prefs.historyBias
            }

            ScoreEntry(seat, score)
        }

        val sorted = scores.sortedByDescending { it.score }
        val scoreFloor = if (sorted.firstOrNull()?.score ?: 0 > 0) 0 else Int.MIN_VALUE
        val result = mutableListOf<SeatInfo>()
        val pickedGroup = mutableSetOf<String>()
        for ((seat, sc) in sorted) {
            if (sc < scoreFloor) break
            if (seat.seatId in pickedGroup) continue
            result.add(seat)
            pickedGroup.add(seat.seatId)
            pickedGroup.addAll(SeatNeighborData.getSideNeighbors(seat.seatId, areaCode))
            if (result.size >= topN) break
        }
        if (result.size < topN) {
            for ((seat, sc) in sorted) {
                if (sc < scoreFloor) break
                if (seat.seatId !in pickedGroup) {
                    result.add(seat)
                    pickedGroup.add(seat.seatId)
                    pickedGroup.addAll(SeatNeighborData.getSideNeighbors(seat.seatId, areaCode))
                }
                if (result.size >= topN) break
            }
        }

        return result
    }
}
