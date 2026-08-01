package com.xjtu.toolbox.schedule

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.select.Elements
import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.ui.ScheduleSlot
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "ScheduleApi"

data class CourseItem(
    val courseName: String,
    val teacher: String,
    val location: String,
    val weekBits: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val courseCode: String,
    val courseType: String
) : ScheduleSlot {
    override val slotName get() = courseName
    override val slotLocation get() = location
    override val slotDayOfWeek get() = dayOfWeek
    override val slotStartSection get() = startSection
    override val slotEndSection get() = endSection

    fun getWeeks(): List<Int> = weekBits.mapIndexedNotNull { index, c -> if (c == '1') index + 1 else null }

    fun isInWeek(week: Int): Boolean {
        val idx = week - 1
        return idx in weekBits.indices && weekBits[idx] == '1'
    }
}

data class ExamItem(
    val courseName: String,
    val courseCode: String,
    val examDate: String,
    val examTime: String,
    val location: String,
    val seatNumber: String
)

data class TextbookItem(
    val courseName: String,
    val textbookName: String,
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val price: String = "",
    val edition: String = ""
) {
    val hasSubstantiveTextbook: Boolean
        get() = textbookName.trim() != "无教材"
                && (textbookName.trim().length >= 2
                    || isbn.any { it.isDigit() }
                    || author.trim().length >= 2)
}

class ScheduleApi(private val login: JwxtLogin) {

    private val client get() = login.client
    private val baseUrl = "https://jwxt.xjtu.edu.cn"
    private var cachedTermCode: String? = null

    suspend fun getCurrentTerm(): String {
        cachedTermCode?.let { return it }
        val responseBody = client.submitForm(
            url = "$baseUrl/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do",
            formParameters = Parameters.Empty
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
        }.bodyAsText()

        val json = responseBody.safeParseJsonObject()
        val code = json["datas"]!!.jsonObject["dqxnxq"]!!.jsonObject["rows"]!!
            .jsonArray[0].jsonObject["DM"]!!.jsonPrimitive.content
        cachedTermCode = code
        return code
    }

    suspend fun getSchedule(termCode: String? = null): List<CourseItem> {
        val term = termCode ?: getCurrentTerm()
        val responseBody = client.submitForm(
            url = "$baseUrl/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
            formParameters = Parameters.build { append("XNXQDM", term) }
        ).bodyAsText()

        val json = responseBody.safeParseJsonObject()
        val rows = json["datas"]?.jsonObject?.get("xskcb")?.jsonObject?.get("rows")?.jsonArray
            ?: return emptyList()

        if (rows.isNotEmpty()) {
            val sample = rows[0].jsonObject
            Logger.d(TAG, "schedule sample keys: ${sample.keys}")
        }

        return rows.map { item ->
            val obj = item.jsonObject
            val courseType = obj["KCXZMC"].safeString().ifEmpty {
                obj["KCXZDM_DISPLAY"].safeString().ifEmpty {
                    obj["KCFLMC"].safeString()
                }
            }
            CourseItem(
                courseName = obj["KCM"].safeString(),
                teacher = obj["SKJS"].safeString(),
                location = obj["JASMC"].safeString(),
                weekBits = obj["SKZC"].safeString(),
                dayOfWeek = obj["SKXQ"].safeInt(1),
                startSection = obj["KSJC"].safeInt(1),
                endSection = obj["JSJC"].safeInt(1),
                courseCode = obj["KCH"].safeString(),
                courseType = courseType
            )
        }
    }

    suspend fun getExamSchedule(termCode: String? = null): List<ExamItem> {
        val term = termCode ?: getCurrentTerm()
        val responseBody = client.submitForm(
            url = "$baseUrl/jwapp/sys/studentWdksapApp/modules/wdksap/wdksap.do",
            formParameters = Parameters.build {
                append("XNXQDM", term)
                append("*order", "-KSRQ,-KSSJMS")
            }
        ).bodyAsText()

        val json = responseBody.safeParseJsonObject()
        val rows = json["datas"]?.jsonObject?.get("wdksap")?.jsonObject?.get("rows")?.jsonArray
            ?: return emptyList()

        return rows.map { item ->
            val obj = item.jsonObject
            val rawDate = obj["KSRQ"].safeString()
            val rawTimeDesc = obj["KSSJMS"].safeString()
            val examDate = rawDate.split(" ").firstOrNull() ?: rawDate
            val examTime = rawTimeDesc
                .replace(examDate, "")
                .replace(rawDate, "")
                .trim()
                .trimStart('-', ' ')
            ExamItem(
                courseName = obj["KCM"].safeString().ifEmpty {
                    obj["KCMC"].safeString().ifEmpty {
                        obj["KCH"].safeString()
                    }
                },
                courseCode = obj["KCH"].safeString(),
                examDate = examDate,
                examTime = examTime.ifEmpty { rawTimeDesc },
                location = obj["JASMC"].safeString(),
                seatNumber = obj["ZWH"].safeString()
            )
        }
    }

    suspend fun getStartOfTerm(termCode: String? = null): LocalDate {
        val term = termCode ?: getCurrentTerm()
        val parts = term.split("-")
        val responseBody = client.submitForm(
            url = "$baseUrl/jwapp/sys/wdkb/modules/jshkcb/cxjcs.do",
            formParameters = Parameters.build {
                append("XN", "${parts[0]}-${parts[1]}")
                append("XQ", parts[2])
            }
        ).bodyAsText()

        val json = responseBody.safeParseJsonObject()
        val dateStr = json["datas"]!!.jsonObject["cxjcs"]!!.jsonObject["rows"]!!
            .jsonArray[0].jsonObject["XQKSRQ"]!!.jsonPrimitive.content
            .split(" ")[0]

        return LocalDate.parse(dateStr)
    }

    suspend fun getTextbooks(studentId: String, termCode: String? = null): List<TextbookItem> {
        val term = termCode ?: getCurrentTerm()
        Logger.d(TAG, "getTextbooks: studentId=$studentId, term=$term")

        val frUrl = "$baseUrl/jwapp/sys/frReport2/show.do"

        val reportletJson = "{'xh':'$studentId','xnxqdm':'$term','reportlet':'jcgl/wdjc.cpt'}"
        var html = client.submitForm(
            url = frUrl,
            formParameters = Parameters.build {
                append("reportlets", "[$reportletJson]")
                append("__cumulatepagenumber__", "false")
            }
        ) {
            header("Referer", "$frUrl?__cumulatepagenumber__=false")
        }.bodyAsText()
        Logger.d(TAG, "getTextbooks: init response len=${html.length}")

        if (html.contains("submitForm") && html.contains(".submit()")) {
            Logger.d(TAG, "getTextbooks: detected auto-submit form, resubmitting")
            val formDoc = Ksoup.parse(html)
            val formAction = formDoc.select("form[name=submitForm]").attr("action")
                .let { if (it.startsWith("http")) it else "$baseUrl$it" }
            val params = Parameters.build {
                formDoc.select("form[name=submitForm] input[type=hidden]").forEach { input ->
                    val name = input.attr("name")
                    val value = input.attr("value")
                    if (name.isNotEmpty()) append(name, value)
                }
            }
            html = client.submitForm(url = formAction, formParameters = params) {
                header("Referer", "$frUrl?__cumulatepagenumber__=false")
            }.bodyAsText()
            Logger.d(TAG, "getTextbooks: resubmit response len=${html.length}")
        }

        if (html.contains("openplatform") || html.contains("login") && !html.contains("SessionMgr")) {
            throw RuntimeException("教材报表会话已过期，请返回重新进入")
        }

        if (html.contains("FR-Engine_Error") || html.contains("error_iframe") || html.contains("出错页面")) {
            val serverError = extractFrErrorMessage(html)
            throw RuntimeException("教务服务器报表异常：$serverError")
        }

        val inlineResult = parseTextbookTable(html)
        if (inlineResult.isNotEmpty()) return inlineResult

        val sessionId = extractFrSessionId(html)
            ?: throw RuntimeException("教材报表初始化失败（未获取到会话ID），请重试")

        return fetchAndParseContent(sessionId)
    }

    private fun extractFrSessionId(html: String): String? {
        val registerPattern = Regex("""FR\.SessionMgr\.register\(\s*['"](\d+)['"]""", RegexOption.IGNORE_CASE)
        registerPattern.find(html)?.let { return it.groupValues[1] }
        val sessionIdPattern = Regex("""sessionID=(\d+)""", RegexOption.IGNORE_CASE)
        sessionIdPattern.find(html)?.let { return it.groupValues[1] }
        val currentPattern = Regex("""currentSessionID\s*=\s*['"](\d+)['"]""")
        currentPattern.find(html)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractFrErrorMessage(html: String): String {
        val messagePattern = Regex("""value='((?:\[\w{4}]|[^'])+)'""")
        val matches = messagePattern.findAll(html).toList()
        for (m in matches) {
            val raw = m.groupValues[1]
            if (raw.contains("[") && raw.length > 10) {
                val decoded = raw.replace(Regex("""\[([0-9a-fA-F]{4})]""")) { mr ->
                    mr.groupValues[1].toInt(16).toChar().toString()
                }
                val brief = decoded.take(200)
                    .replace("java.lang.RuntimeException:", "")
                    .replace("java.sql.SQLException:", "")
                    .trim()
                if (brief.isNotBlank()) return brief
            }
        }
        if (html.contains("出错页面")) return "服务器报表引擎出错，请稍后重试"
        return "未知服务器错误"
    }

    private fun extractTotalPages(html: String): Int {
        val pattern = Regex("""FR\._p\.reportTotalPage\s*=\s*(\d+)""")
        return pattern.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private suspend fun fetchAndParseContent(sessionId: String): List<TextbookItem> {
        val frUrl = "$baseUrl/jwapp/sys/frReport2/show.do"
        val firstPageUrl = "$frUrl?_=${currentTimeMillis()}&__boxModel__=true&op=page_content&sessionID=$sessionId&pn=1"
        val firstPageHtml = client.get(firstPageUrl) {
            header("X-Requested-With", "XMLHttpRequest")
            header("Referer", frUrl)
        }.bodyAsText()

        if (firstPageHtml.contains("FR-Engine_Error") || firstPageHtml.contains("error_iframe")) {
            throw RuntimeException("教材报表服务端渲染失败，请稍后重试")
        }

        val totalPages = extractTotalPages(firstPageHtml)
        val allItems = mutableListOf<TextbookItem>()
        allItems.addAll(parseTextbookTable(firstPageHtml))

        for (pn in 2..totalPages) {
            val pageUrl = "$frUrl?_=${currentTimeMillis()}&__boxModel__=true&op=page_content&sessionID=$sessionId&pn=$pn"
            val pageHtml = client.get(pageUrl) {
                header("X-Requested-With", "XMLHttpRequest")
                header("Referer", frUrl)
            }.bodyAsText()
            allItems.addAll(parseTextbookTable(pageHtml))
        }
        return allItems
    }

    private fun parseTextbookTable(html: String): List<TextbookItem> {
        val doc = Ksoup.parse(html)
        val tables = doc.select("table")

        if (tables.isNotEmpty()) {
            val dataTable = tables.maxByOrNull { it.select("tr").size } ?: return emptyList()
            val rows = dataTable.select("tr")
            if (rows.size >= 2) {
                val result = parseTableRows(rows)
                if (result.isNotEmpty()) return result
            }
        }

        val textDivs = doc.select("div[style*=position]")
            .filter { it.children().isEmpty() || it.select("span").isNotEmpty() }
            .mapNotNull { div ->
                val style = div.attr("style")
                val left = Regex("""left\s*:\s*(\d+(?:\.\d+)?)""").find(style)?.groupValues?.get(1)?.toFloatOrNull()
                val top = Regex("""top\s*:\s*(\d+(?:\.\d+)?)""").find(style)?.groupValues?.get(1)?.toFloatOrNull()
                val text = div.text().trim()
                if (left != null && top != null && text.isNotBlank()) Triple(left, top, text) else null
            }

        if (textDivs.isNotEmpty()) {
            val result = parseDivBasedReport(textDivs)
            if (result.isNotEmpty()) return result
        }

        return emptyList()
    }

    private fun parseTableRows(rows: Elements): List<TextbookItem> {
        var headerRowIndex = -1
        val colMap = mutableMapOf<String, Int>()

        for (ri in rows.indices) {
            val cells = rows[ri].select("td, th").map { it.text().trim() }
            if (cells.size >= 3 && cells.any { "课程" in it } && cells.any { "书名" in it || "教材" in it || "ISBN" in it.uppercase() }) {
                headerRowIndex = ri
                cells.forEachIndexed { index, header -> mapHeaderColumn(header, index, colMap) }
                break
            }
        }

        if (headerRowIndex == -1) {
            for (ri in rows.indices) {
                val cells = rows[ri].select("td, th").map { it.text().trim() }
                if (cells.size >= 4 && cells.count { it.isNotBlank() } >= 4) {
                    headerRowIndex = ri
                    if (cells.size >= 8) {
                        colMap["course"] = 1; colMap["textbook"] = 3; colMap["isbn"] = 4
                        colMap["author"] = 5; colMap["edition"] = 6; colMap["publisher"] = 7
                    } else {
                        colMap["course"] = 0; colMap["textbook"] = 1
                        if (cells.size > 2) colMap["author"] = 2
                        if (cells.size > 3) colMap["publisher"] = 3
                        if (cells.size > 4) colMap["isbn"] = 4
                    }
                    break
                }
            }
        }

        if (headerRowIndex == -1) return emptyList()
        return extractTextbooksFromRows(rows, headerRowIndex, colMap)
    }

    private fun parseDivBasedReport(cells: List<Triple<Float, Float, String>>): List<TextbookItem> {
        val sortedByY = cells.sortedBy { it.second }
        val rows = mutableListOf<MutableList<Pair<Float, String>>>()
        var currentRowY = -1f
        var currentRow = mutableListOf<Pair<Float, String>>()

        for ((x, y, text) in sortedByY) {
            if (currentRowY < 0 || kotlin.math.abs(y - currentRowY) > 3f) {
                if (currentRow.isNotEmpty()) rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowY = y
            }
            currentRow.add(x to text)
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        if (rows.size < 2) return emptyList()
        rows.forEach { it.sortBy { cell -> cell.first } }

        val colMap = mutableMapOf<String, Int>()
        var headerRowIndex = -1
        for (ri in rows.indices) {
            val texts = rows[ri].map { it.second }
            if (texts.size >= 3 && texts.any { "课程" in it } && texts.any { "书名" in it || "教材" in it || "ISBN" in it.uppercase() }) {
                headerRowIndex = ri
                texts.forEachIndexed { index, header -> mapHeaderColumn(header, index, colMap) }
                break
            }
        }

        if (headerRowIndex == -1) return emptyList()

        val textbooks = mutableListOf<TextbookItem>()
        for (ri in (headerRowIndex + 1) until rows.size) {
            val texts = rows[ri].map { it.second }
            if (texts.isEmpty() || texts.all { it.isBlank() }) continue
            fun col(key: String): String = colMap[key]?.let { texts.getOrNull(it) } ?: ""
            val courseName = col("course")
            val textbookName = col("textbook")
            if (courseName.isBlank() && textbookName.isBlank()) continue
            textbooks.add(TextbookItem(courseName, textbookName, col("author"), col("publisher"), col("isbn"), col("price"), col("edition")))
        }
        return textbooks
    }

    private fun mapHeaderColumn(header: String, index: Int, colMap: MutableMap<String, Int>) {
        when {
            header == "课程名" || ("课程" in header && "名" in header && "号" !in header) -> colMap["course"] = index
            header == "书名" || "教材名" in header || ("教材" in header && "名" in header) -> colMap["textbook"] = index
            "主编" in header || "作者" in header || "编者" in header -> colMap["author"] = index
            "出版社" in header || ("出版" in header && "社" in header) -> colMap["publisher"] = index
            "ISBN" in header.uppercase() || "书号" in header -> colMap["isbn"] = index
            "价" in header || "定价" in header -> colMap["price"] = index
            "版次" in header || "版本" in header -> colMap["edition"] = index
        }
    }

    private fun extractTextbooksFromRows(rows: Elements, headerRowIndex: Int, colMap: Map<String, Int>): List<TextbookItem> {
        val textbooks = mutableListOf<TextbookItem>()
        for (i in (headerRowIndex + 1) until rows.size) {
            val cells = rows[i].select("td, th").map { it.text().trim() }
            if (cells.isEmpty() || cells.all { it.isBlank() }) continue
            fun col(key: String): String = colMap[key]?.let { cells.getOrNull(it) } ?: ""
            val courseName = col("course")
            val textbookName = col("textbook")
            if (courseName.isBlank() && textbookName.isBlank()) continue
            textbooks.add(TextbookItem(courseName, textbookName, col("author"), col("publisher"), col("isbn"), col("price"), col("edition")))
        }
        return textbooks
    }

    suspend fun getTermList(): List<String> {
        val responseBody = client.submitForm(
            url = "$baseUrl/jwapp/sys/wdkb/modules/jshkcb/cxxnxqgl.do",
            formParameters = Parameters.Empty
        ) {
            header("Accept", "application/json, text/javascript, */*; q=0.01")
        }.bodyAsText()

        return try {
            val json = responseBody.safeParseJsonObject()
            json["datas"]!!.jsonObject["cxxnxqgl"]!!.jsonObject["rows"]!!.jsonArray
                .map { it.jsonObject["DM"]!!.jsonPrimitive.content }
        } catch (_: Exception) {
            generateRecentTerms()
        }
    }

    private suspend fun generateRecentTerms(): List<String> {
        val current = getCurrentTerm()
        val parts = current.split("-")
        val year1 = parts[0].toInt()
        val year2 = parts[1].toInt()
        val sem = parts[2].toInt()

        val terms = mutableListOf<String>()
        var y1 = year1; var y2 = year2; var s = sem
        repeat(6) {
            terms.add("$y1-$y2-$s")
            if (s == 1) { y1--; y2--; s = 2 } else { s = 1 }
        }
        return terms
    }
}
