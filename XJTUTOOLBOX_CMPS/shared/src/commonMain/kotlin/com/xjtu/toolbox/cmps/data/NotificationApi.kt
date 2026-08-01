package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class NotificationCategory(val displayName: String) {
    General("综合"),
    Engineering("工学"),
    Science("理学"),
    Humanities("人文经管"),
}

enum class NotificationSource(
    val displayName: String,
    val baseUrl: String,
    val category: NotificationCategory,
) {
    Jwc("教务处", "https://dean.xjtu.edu.cn/jxxx/jxtz2.htm", NotificationCategory.General),
    Gs("研究生院", "https://gs.xjtu.edu.cn/tzgg.htm", NotificationCategory.General),
    Qxs("钱学森书院", "https://bjb.xjtu.edu.cn/xydt/tzgg.htm", NotificationCategory.General),
    Fti("未来技术学院", "https://wljsxy.xjtu.edu.cn/xwgg/tzgg.htm", NotificationCategory.General),
    Xsc("学生处", "https://xsc.xjtu.edu.cn/xgdt/tzgg.htm", NotificationCategory.General),
    Me("机械学院", "https://mec.xjtu.edu.cn/index/tzgg/bks.htm", NotificationCategory.Engineering),
    Ee("电气学院", "https://ee.xjtu.edu.cn/jzxx/bks.htm", NotificationCategory.Engineering),
    Epe("能动学院", "https://epe.xjtu.edu.cn/index/tzgg.htm", NotificationCategory.Engineering),
    Aero("航天学院", "https://sae.xjtu.edu.cn/index/tzgg.htm", NotificationCategory.Engineering),
    Mse("材料学院", "https://mse.xjtu.edu.cn/xwgg/tzgg1.htm", NotificationCategory.Engineering),
    Math("数学学院", "https://math.xjtu.edu.cn/index/jxjw1.htm", NotificationCategory.Science),
    Phy("物理学院", "https://phy.xjtu.edu.cn/glfw/tzgg.htm", NotificationCategory.Science),
    Chem("化学学院", "https://chem.xjtu.edu.cn/tzgg.htm", NotificationCategory.Science),
    Som("管理学院", "https://som.xjtu.edu.cn/xwgg/tzgg.htm", NotificationCategory.Humanities),
    Sfs("外国语学院", "https://sfs.xjtu.edu.cn/glfw/jxjw.htm", NotificationCategory.Humanities),
    Law("法学院", "https://fxy.xjtu.edu.cn/index/tzgg.htm", NotificationCategory.Humanities),
}

class NotificationApi(
    private val client: HttpClient = platformHttpClient(),
) {
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    suspend fun getMergedNotifications(
        sources: List<NotificationSource> = defaultSources,
        limitPerSource: Int = 8,
    ): List<NotificationItem> = coroutineScope {
        sources.map { source ->
            async {
                runCatching { getNotifications(source, limitPerSource) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
            .distinctBy { it.title to it.link }
            .sortedWith(compareByDescending<NotificationItem> { it.date }.thenBy { it.source })
    }

    suspend fun getNotifications(source: NotificationSource, limit: Int = 10): List<NotificationItem> {
        val html = client.get(source.baseUrl) {
            header("User-Agent", userAgent)
        }.bodyAsText()
        return parseList(html, source).take(limit)
    }

    private fun parseList(html: String, source: NotificationSource): List<NotificationItem> {
        val compact = html.replace(Regex("\\s+"), " ")
        val itemRegex = Regex(
            """<a\s+[^>]*href=["']([^"']+)["'][^>]*?(?:title=["']([^"']*)["'])?[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return itemRegex.findAll(compact).mapNotNull { match ->
            val href = match.groupValues[1].trim()
            if (href.isBlank() || href.startsWith("javascript") || href == "#") return@mapNotNull null
            val title = cleanText(match.groupValues[2].ifBlank { match.groupValues[3] })
            if (title.length < 4 || title in navWords) return@mapNotNull null
            val context = compact.substring((match.range.first - 120).coerceAtLeast(0), (match.range.last + 120).coerceAtMost(compact.length))
            NotificationItem(
                title = title,
                source = source.displayName,
                date = parseDate(context),
                important = title.contains("重要") || title.contains("考试") || title.contains("选课"),
                link = resolveUrl(source.baseUrl, href),
                category = source.category.displayName,
                tags = inferTags(title),
            )
        }.distinctBy { it.title to it.link }.toList()
    }

    private fun cleanText(raw: String): String =
        raw.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '\t', '\n', '\r', '[', ']', '【', '】')

    private fun parseDate(text: String): String {
        val full = Regex("""(\d{4})[-./](\d{1,2})[-./](\d{1,2})""").find(text)
        if (full != null) {
            val (y, m, d) = full.destructured
            return "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        val md = Regex("""(\d{1,2})[-./](\d{1,2})""").find(text)
        val y = Regex("""(20\d{2})""").find(text)?.groupValues?.get(1)
        if (md != null && y != null) {
            val (m, d) = md.destructured
            return "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        return ""
    }

    private fun resolveUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val scheme = baseUrl.substringBefore("://")
        val host = baseUrl.substringAfter("://").substringBefore("/")
        if (href.startsWith("/")) return "$scheme://$host$href"
        val basePath = baseUrl.substringBeforeLast("/")
        return "$basePath/$href"
    }

    private fun inferTags(title: String): List<String> = buildList {
        if ("考试" in title) add("考试")
        if ("选课" in title) add("选课")
        if ("竞赛" in title || "比赛" in title) add("竞赛")
        if ("奖学金" in title || "资助" in title) add("资助")
        if ("放假" in title || "假期" in title) add("假期")
    }

    companion object {
        val defaultSources = listOf(
            NotificationSource.Jwc,
            NotificationSource.Gs,
            NotificationSource.Qxs,
            NotificationSource.Fti,
            NotificationSource.Xsc,
            NotificationSource.Me,
            NotificationSource.Ee,
            NotificationSource.Math,
            NotificationSource.Som,
        )

        private val navWords = setOf("首页", "通知公告", "更多", "上一页", "下一页", "English")
    }
}
