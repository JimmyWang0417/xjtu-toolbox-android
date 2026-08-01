package com.xjtu.toolbox.notification

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.jsonPrimitive

// ==================== 数据类 ====================

data class Notification(
    val title: String,
    val link: String,
    val source: NotificationSource,
    val description: String = "",
    val tags: List<String> = emptyList(),
    /** 日期字符串 yyyy-MM-dd */
    val date: String = "",
    val isRead: Boolean = false
)

// ==================== 来源分类 ====================

enum class SourceCategory(val displayName: String) {
    GENERAL("综合"),
    ENGINEERING("工学"),
    SCIENCE("理学"),
    HUMANITIES("人文经管");
}

// ==================== 通知来源 ====================

enum class NotificationSource(
    val displayName: String,
    val baseUrl: String,
    val category: SourceCategory
) {
    // ── 综合（校级部门） ──
    JWC("教务处", "https://dean.xjtu.edu.cn/jxxx/jxtz2.htm", SourceCategory.GENERAL),
    GS("研究生院", "https://gs.xjtu.edu.cn/tzgg.htm", SourceCategory.GENERAL),
    QXS("钱学森书院", "https://bjb.xjtu.edu.cn/xydt/tzgg.htm", SourceCategory.GENERAL),
    FTI("未来技术学院", "https://wljsxy.xjtu.edu.cn/xwgg/tzgg.htm", SourceCategory.GENERAL),
    XSC("学生处", "https://xsc.xjtu.edu.cn/xgdt/tzgg.htm", SourceCategory.GENERAL),

    // ── 工学 ──
    ME("机械学院", "https://mec.xjtu.edu.cn/index/tzgg/bks.htm", SourceCategory.ENGINEERING),
    EE("电气学院", "https://ee.xjtu.edu.cn/jzxx/bks.htm", SourceCategory.ENGINEERING),
    EPE("能动学院", "https://epe.xjtu.edu.cn/index/tzgg.htm", SourceCategory.ENGINEERING),
    AERO("航天学院", "https://sae.xjtu.edu.cn/index/tzgg.htm", SourceCategory.ENGINEERING),
    MSE("材料学院", "https://mse.xjtu.edu.cn/xwgg/tzgg1.htm", SourceCategory.ENGINEERING),
    CLET("化工学院", "https://clet.xjtu.edu.cn/xwgg/tzgg.htm", SourceCategory.ENGINEERING),
    HSCE("人居学院", "https://hsce.xjtu.edu.cn/xwgg/tzgg1.htm", SourceCategory.ENGINEERING),
    SE("软件学院", "https://se.xjtu.edu.cn/xwgg/tzgg.htm", SourceCategory.ENGINEERING),

    // ── 理学 ──
    MATH("数学学院", "https://math.xjtu.edu.cn/index/jxjw1.htm", SourceCategory.SCIENCE),
    PHY("物理学院", "https://phy.xjtu.edu.cn/glfw/tzgg.htm", SourceCategory.SCIENCE),
    CHEM("化学学院", "https://chem.xjtu.edu.cn/tzgg.htm", SourceCategory.SCIENCE),
    SLST("生命学院", "https://slst.xjtu.edu.cn/ggl/tzgg.htm", SourceCategory.SCIENCE),

    // ── 人文经管 ──
    SOM("管理学院", "https://som.xjtu.edu.cn/xwgg/tzgg.htm", SourceCategory.HUMANITIES),
    RWXY("人文学院", "https://rwxy.xjtu.edu.cn/index/tzgg.htm", SourceCategory.HUMANITIES),
    SFS("外国语学院", "https://sfs.xjtu.edu.cn/glfw/jxjw.htm", SourceCategory.HUMANITIES),
    LAW("法学院", "https://fxy.xjtu.edu.cn/index/tzgg.htm", SourceCategory.HUMANITIES),
    SEF("经金学院", "https://sef.xjtu.edu.cn/rcpy/bks/jxtz1.htm", SourceCategory.HUMANITIES),
    SPPA("公管学院", "https://sppa.xjtu.edu.cn/xwxx/bksjw.htm", SourceCategory.HUMANITIES),
    MARX("马克思主义学院", "https://marx.xjtu.edu.cn/xwgg1/tzgg.htm", SourceCategory.HUMANITIES),
    XMTXY("新媒体学院", "https://xmtxy.xjtu.edu.cn/xwgg/tzgg.htm", SourceCategory.HUMANITIES);

    companion object {
        fun fromDisplayName(name: String): NotificationSource? =
            entries.find { it.displayName == name }

        fun byCategory(cat: SourceCategory): List<NotificationSource> =
            entries.filter { it.category == cat }
    }
}

// ==================== 反爬虫处理 ====================

private const val TAG = "NotificationApi"

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val domainCookies = mutableMapOf<String, String>()

/** 域名级别失败缓存 */
private val failedDomains = mutableMapOf<String, Long>()
private const val DOMAIN_FAILURE_TTL_MS = 5 * 60 * 1000L

private fun isDomainFailed(domain: String): Boolean {
    val failedAt = failedDomains[domain] ?: return false
    if (currentTimeMillis() - failedAt > DOMAIN_FAILURE_TTL_MS) {
        failedDomains.remove(domain)
        return false
    }
    return true
}

private fun markDomainFailed(domain: String) {
    failedDomains[domain] = currentTimeMillis()
}

/** 从 URL 中提取 host */
private fun extractHost(url: String): String {
    val afterScheme = url.substringAfter("://")
    return afterScheme.substringBefore("/").substringBefore(":")
}

/** 从 URL 中提取 scheme://host */
private fun extractBaseHost(url: String): String {
    val scheme = url.substringBefore("://")
    val host = extractHost(url)
    return "$scheme://$host"
}

private suspend fun fetchDocumentWithChallenge(client: HttpClient, url: String): Document {
    val domain = extractHost(url)

    if (isDomainFailed(domain)) {
        throw RuntimeException("Domain $domain is cached as failed")
    }

    val html = try {
        val response = client.get(url) {
            header("User-Agent", USER_AGENT)
            domainCookies[domain]?.let { header("Cookie", it) }
        }
        if (response.status.value == 404 || response.status.value >= 500) {
            throw RuntimeException("HTTP ${response.status.value} for $url")
        }
        response.bodyAsText()
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("UnknownHost", true) || msg.contains("SocketTimeout", true) ||
            msg.contains("ConnectException", true) || msg.contains("connect", true)) {
            markDomainFailed(domain)
        }
        throw e
    }

    if (!html.contains("dynamic_challenge")) {
        return Ksoup.parse(html, url)
    }

    val challengeId = Regex("""var\s+challengeId\s*=\s*"([^"]+)"""").find(html)
        ?.groupValues?.get(1) ?: return Ksoup.parse(html, url)
    val answer = Regex("""var\s+answer\s*=\s*(\d+)""").find(html)
        ?.groupValues?.get(1) ?: return Ksoup.parse(html, url)

    val baseUri = extractBaseHost(url)
    val challengeJson = """{"challenge_id":"$challengeId","answer":$answer,"browser_info":{"userAgent":"$USER_AGENT","language":"zh-CN","platform":"Win32","cookieEnabled":true,"hardwareConcurrency":4,"deviceMemory":8,"timezone":"Asia/Shanghai"}}"""

    val challengeBody = try {
        client.post("$baseUri/dynamic_challenge") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.Json)
            setBody(challengeJson)
        }.bodyAsText()
    } catch (_: Exception) { return Ksoup.parse(html, url) }

    val result = try {
        challengeBody.safeParseJsonObject()
    } catch (_: Exception) {
        return Ksoup.parse(html, url)
    }

    if (result["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() != true) {
        return Ksoup.parse(html, url)
    }

    val clientId = result["client_id"]?.jsonPrimitive?.content ?: return Ksoup.parse(html, url)
    domainCookies[domain] = "client_id=$clientId"

    val retryHtml = try {
        val resp = client.get(url) {
            header("Cookie", "client_id=$clientId")
            header("User-Agent", USER_AGENT)
        }
        if (resp.status.value == 404 || resp.status.value >= 500) {
            throw RuntimeException("HTTP ${resp.status.value} for $url")
        }
        resp.bodyAsText()
    } catch (_: Exception) { html }

    return Ksoup.parse(retryHtml, url)
}

// ==================== 爬虫接口 ====================

private interface NotificationCrawler {
    suspend fun fetch(page: Int): List<Notification>
}

// ==================== 通用 XJTU 学院爬虫 ====================

private class GenericXjtuCrawler(
    private val client: HttpClient,
    private val source: NotificationSource
) : NotificationCrawler {

    companion object {
        val LIST_SELECTORS = listOf(
            "div.list_rnr > ul > li",
            "div.list_rlb > ul > li",
            "#ny-main ul.list > li",
            "div.list_right_con > ul > li",
            "main ul.news_list > li",
            "ul.news_list > li",
            ".main_conRCR ul > li",
            "div.list_con > ul > li",
            ".news-list ul > li",
            "div.content ul.list > li",
            "div.article-list ul > li",
            "ul.clearfix > li",
            "ul.wp_article_list > li",
            "div.right-list ul > li",
            ".list_box ul > li",
            "div.tzgg > ul > li",
            "ul.txtList > li",
            "div.nyrCon ul > li",
            "div.list ul > li",
            "div.list > ul > li",
            ".news_list ul > li",
            "div.newslist ul > li",
            "div.right_con ul > li",
            "#container ul > li",
            "div.content_area ul > li",
            "div.list_main > ul > li",
        )

        val NEXT_SELECTORS = listOf(
            "span.p_next a",
            "a:containsOwn(下一页)",
            "a:containsOwn(下页)",
            "a.next",
            ".pagination a.next",
            "a:containsOwn(>)",
            "a:containsOwn(Next)",
        )

        val FALLBACK_PATHS = listOf(
            "/xwgg/tzgg.htm", "/xwgg/tzgg1.htm",
            "/index/tzgg.htm", "/index/tzgg1.htm", "/index/tzgg/bks.htm", "/index/jxjw1.htm",
            "/xwzx/tzgg.htm", "/xwxx/tzgg.htm", "/xwxx/bksjw.htm", "/xwgg1/tzgg.htm", "/dzxxxb/tzgg.htm",
            "/jzxx/bks.htm", "/glfw/jxjw.htm", "/rcpy/bks/jxtz1.htm",
            "/ggl/tzgg.htm", "/xgdt/tzgg.htm", "/xydt/tzgg.htm", "/glfw/tzgg.htm",
            "/tzgg.htm", "/xyxw/tzgg.htm", "/xwgg/xytz.htm", "/xytz.htm", "/xwgg.htm",
            "/notice.htm", "/jxxx/jxtz2.htm",
        )

        private val FULL_DATE_RE = Regex("""\d{4}[-./]\d{1,2}[-./]\d{1,2}""")
        private val YEAR_MONTH_RE = Regex("""(\d{4})[-./](\d{1,2})""")
        private val MONTH_DAY_RE = Regex("""(\d{1,2})[-./](\d{1,2})""")
        private val YEAR_ONLY_RE = Regex("""\b(\d{4})\b""")
        private val SMALL_NUM_RE = Regex("""\b(\d{1,2})\b""")
        private val DIGITS_RE = Regex("""\d+""")
    }

    override suspend fun fetch(page: Int): List<Notification> {
        val allNotifications = mutableListOf<Notification>()
        var url = source.baseUrl

        val domain = try { extractHost(url) } catch (_: Exception) { null }
        if (domain != null && isDomainFailed(domain)) {
            Logger.d(TAG, "GenericCrawler[${source.displayName}] skipping - domain $domain cached as failed")
            return emptyList()
        }

        var doc = tryFetchDoc(url)

        if (doc == null || extractItems(doc).isEmpty()) {
            if (domain != null && isDomainFailed(domain)) return emptyList()
            val baseHost = try { extractBaseHost(url) } catch (_: Exception) { null }
            if (baseHost != null) {
                for (path in FALLBACK_PATHS) {
                    val fallbackUrl = "$baseHost$path"
                    if (fallbackUrl == url) continue
                    val fallbackDoc = tryFetchDoc(fallbackUrl)
                    if (fallbackDoc != null && extractItems(fallbackDoc).isNotEmpty()) {
                        doc = fallbackDoc
                        url = fallbackUrl
                        Logger.d(TAG, "GenericCrawler[${source.displayName}] fallback hit: $url")
                        break
                    }
                    if (domain != null && isDomainFailed(domain)) break
                }
            }
        }

        if (doc == null) return emptyList()

        for (i in 0 until page) {
            if (i > 0) {
                doc = tryFetchDoc(url)
                if (doc == null) break
            }

            val items = extractItems(doc!!)
            if (items.isEmpty()) {
                Logger.w(TAG, "GenericCrawler[${source.displayName}] no items at $url (page $i)")
                if (i == 0) {
                    val bruteItems = bruteForceExtract(doc!!, url)
                    Logger.d(TAG, "GenericCrawler[${source.displayName}] brute force yielded ${bruteItems.size} items")
                    allNotifications.addAll(bruteItems)
                }
                break
            }

            Logger.d(TAG, "GenericCrawler[${source.displayName}] page $i: ${items.size} items from $url")
            for (el in items) {
                val notification = parseListItem(el, url) ?: continue
                allNotifications.add(notification)
            }

            var nextHref: String? = null
            for (selector in NEXT_SELECTORS) {
                nextHref = doc!!.selectFirst(selector)?.attr("href")
                if (!nextHref.isNullOrBlank()) break
            }
            if (nextHref.isNullOrBlank()) break
            url = resolveUrl(url, nextHref)
        }

        return allNotifications.distinctBy { Triple(it.title, it.link, it.source) }
    }

    private suspend fun tryFetchDoc(url: String): Document? {
        return try {
            val doc = fetchDocumentWithChallenge(client, url)
            val bodyLen = doc.body()?.text()?.length ?: 0
            if (bodyLen < 50) null else doc
        } catch (e: Exception) {
            Logger.w(TAG, "GenericCrawler[${source.displayName}] fetch error at $url: ${e.message}")
            null
        }
    }

    private fun hasInfoLink(el: Element): Boolean {
        val a = el.selectFirst("a[href]") ?: return false
        val href = a.attr("href")
        return href.contains("/info/") || href.contains("content.jsp")
    }

    private fun extractItems(doc: Document): List<Element> {
        data class CandidateList(val items: List<Element>, val infoCount: Int)
        val candidates = mutableListOf<CandidateList>()

        for (selector in LIST_SELECTORS) {
            val allItems = doc.select(selector)
            if (allItems.size < 3) continue
            val infoItems = allItems.filter { hasInfoLink(it) }
            if (infoItems.size >= 3) {
                candidates.add(CandidateList(infoItems, infoItems.size))
            }
        }

        val best = candidates.maxByOrNull { it.infoCount }
        if (best != null) {
            Logger.d(TAG, "GenericCrawler[${source.displayName}] extractItems: /info/ strategy matched ${best.infoCount} items")
            return best.items
        }

        for (selector in LIST_SELECTORS) {
            val items = doc.select(selector)
            if (items.size < 3) continue
            val dateCount = items.count { FULL_DATE_RE.containsMatchIn(it.text()) }
            if (dateCount.toDouble() / items.size >= 0.5 && dateCount >= 3) {
                Logger.d(TAG, "GenericCrawler[${source.displayName}] extractItems: date density fallback matched ${items.size} items")
                return items
            }
        }

        Logger.w(TAG, "GenericCrawler[${source.displayName}] extractItems: no items found")
        return emptyList()
    }

    private fun bruteForceExtract(doc: Document, baseUrl: String): List<Notification> {
        val candidates = doc.select("ul, ol").mapNotNull { ul ->
            val lis = ul.select("> li").filter { li -> hasInfoLink(li) }
            if (lis.size >= 3) lis else null
        }.maxByOrNull { it.size } ?: return emptyList()

        Logger.d(TAG, "GenericCrawler[${source.displayName}] brute force found ${candidates.size} items")
        return candidates.mapNotNull { parseListItem(it, baseUrl) }
    }

    private fun parseSplitDate(text: String): String? {
        if (text.isEmpty()) return null

        FULL_DATE_RE.find(text)?.let {
            val d = parseDateSafe(it.value); if (d.isNotEmpty()) return d
        }

        val ymMatch = YEAR_MONTH_RE.find(text)
        if (ymMatch != null) {
            val y = ymMatch.groupValues[1].toIntOrNull() ?: return null
            val m = ymMatch.groupValues[2].toIntOrNull() ?: return null
            val rest = text.removeRange(ymMatch.range).trim()
            val d = SMALL_NUM_RE.find(rest)?.groupValues?.get(1)?.toIntOrNull()
            if (d != null && y in 2000..2099 && m in 1..12 && d in 1..31)
                return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
        }

        val mdMatch = MONTH_DAY_RE.find(text)
        if (mdMatch != null && ymMatch == null) {
            val a = mdMatch.groupValues[1].toIntOrNull() ?: return null
            val b = mdMatch.groupValues[2].toIntOrNull() ?: return null
            val rest = text.removeRange(mdMatch.range).trim()
            val yStr = YEAR_ONLY_RE.find(rest)?.groupValues?.get(1)
            val y = yStr?.toIntOrNull()
            if (y != null && y in 2000..2099 && a in 1..12 && b in 1..31)
                return "$y-${a.toString().padStart(2, '0')}-${b.toString().padStart(2, '0')}"
        }

        val yOnly = YEAR_ONLY_RE.find(text)
        if (yOnly != null && ymMatch == null && mdMatch == null) {
            val y = yOnly.groupValues[1].toIntOrNull() ?: return null
            val mdAfter = MONTH_DAY_RE.find(text.removeRange(yOnly.range).trim())
            if (mdAfter != null) {
                val m = mdAfter.groupValues[1].toIntOrNull() ?: return null
                val d = mdAfter.groupValues[2].toIntOrNull() ?: return null
                if (y in 2000..2099 && m in 1..12 && d in 1..31)
                    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
            }
        }

        return null
    }

    private fun textWithSpaces(el: Element): String {
        if (el.childNodeSize() <= 1) return el.text()
        return el.childNodes().joinToString(" ") { node ->
            when (node) {
                is TextNode -> node.text().trim()
                is Element -> node.text()
                else -> ""
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractDateFromLi(el: Element): String {
        for (sel in listOf("span.time", "span.date", "em")) {
            val t = el.selectFirst(sel)?.text() ?: continue
            if (FULL_DATE_RE.containsMatchIn(t)) return parseDateSafe(t)
        }

        for (span in el.select("span")) {
            val t = textWithSpaces(span)
            if (FULL_DATE_RE.containsMatchIn(t)) return parseDateSafe(t)
            if (DIGITS_RE.findAll(t).count() >= 3) {
                parseSplitDate(t)?.let { return it }
            }
        }

        for (container in el.select("[class*=date], [class*=time], time")) {
            parseSplitDate(textWithSpaces(container))?.let { return it }
        }

        FULL_DATE_RE.find(el.text())?.let { return parseDateSafe(it.value) }
        return ""
    }

    private fun parseListItem(el: Element, baseUrl: String): Notification? {
        val aTag = el.selectFirst("a[href]") ?: return null
        val href = aTag.attr("href")
        if (href.isBlank() || href == "#" || href.startsWith("javascript")) return null

        val title = aTag.attr("title").ifBlank {
            aTag.selectFirst("p:nth-child(2)")?.text()
                ?: aTag.selectFirst("p")?.text()
                ?: aTag.ownText().ifBlank { aTag.text() }
        }.trim()
        if (title.isBlank() || title.length < 4) return null

        val link = resolveUrl(baseUrl, href)
        val date = extractDateFromLi(el)

        val tagText = aTag.selectFirst("i")?.text()?.trim('[', ']', '【', '】') ?: ""
        val tags = if (tagText.isNotEmpty()) listOf(tagText) else emptyList()

        return Notification(title = title, link = link, source = source, date = date, tags = tags)
    }
}

// ==================== 工具函数 ====================

private fun resolveUrl(baseUrl: String, relative: String): String {
    if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
    if (relative.startsWith("/")) {
        val baseHost = extractBaseHost(baseUrl)
        return "$baseHost$relative"
    }
    // 相对路径
    val basePath = baseUrl.substringBeforeLast("/")
    return "$basePath/$relative"
}

private val DATE_YMD_RE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

private fun parseDateSafe(dateStr: String): String {
    return try {
        val cleaned = dateStr.trim().replace('/', '-').replace('.', '-')
        val match = DATE_YMD_RE.find(cleaned)
        if (match != null) {
            val (y, m, d) = match.destructured
            "${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        } else ""
    } catch (_: Exception) { "" }
}

// ==================== API 类 ====================

class NotificationApi(
    private val client: HttpClient = com.xjtu.toolbox.util.createHttpClient()
) {
    private val crawlers: Map<NotificationSource, NotificationCrawler> = buildMap {
        NotificationSource.entries.forEach { source ->
            put(source, GenericXjtuCrawler(client, source))
        }
    }

    suspend fun getNotifications(source: NotificationSource, page: Int = 1): List<Notification> {
        val crawler = crawlers[source]
            ?: throw IllegalArgumentException("不支持的通知来源: $source")
        return crawler.fetch(page)
    }

    suspend fun getMergedNotifications(sources: List<NotificationSource>, page: Int = 1): List<Notification> {
        return coroutineScope {
            sources.map { source ->
                async {
                    runCatching { getNotifications(source, page) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.sortedByDescending { it.date }
    }

    suspend fun getAllNotifications(page: Int = 1): List<Notification> {
        return getMergedNotifications(NotificationSource.entries, page)
    }

    /** 清除域名失败缓存 */
    fun clearFailedDomainCache() {
        failedDomains.clear()
    }
}
