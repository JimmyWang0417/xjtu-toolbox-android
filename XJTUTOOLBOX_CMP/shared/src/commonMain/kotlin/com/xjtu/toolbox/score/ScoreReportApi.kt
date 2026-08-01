package com.xjtu.toolbox.score

import com.fleeksoft.ksoup.Ksoup
import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.util.currentTimeMillis
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

data class ReportedGrade(
    val courseName: String,
    val coursePoint: Double,
    val score: String,
    val gpa: Double?,
    val term: String
)

class ScoreReportApi(private val login: JwxtLogin) {

    private val client get() = login.client

    companion object {
        private const val FR_REPORT_URL = "https://jwxt.xjtu.edu.cn/jwapp/sys/frReport2/show.do"

        fun scoreToGpa(score: Any?): Double? {
            return when (score) {
                is Number -> {
                    val s = score.toDouble()
                    when {
                        s >= 95 -> 4.3; s >= 90 -> 4.0; s >= 85 -> 3.7
                        s >= 81 -> 3.3; s >= 78 -> 3.0; s >= 75 -> 2.7
                        s >= 72 -> 2.3; s >= 68 -> 2.0; s >= 64 -> 1.7
                        s >= 60 -> 1.3; else -> 0.0
                    }
                }
                is String -> {
                    val g = score
                        .replace('＋', '+').replace('－', '-')
                        .replace(Regex("[^a-zA-Z0-9+\\-\\u4e00-\\u9fff]"), "").uppercase()
                    when {
                        g.isEmpty() -> null
                        g.toDoubleOrNull() != null -> scoreToGpa(g.toDouble())
                        g == "A+" -> 4.3; g == "A" -> 4.0; g == "A-" -> 3.7
                        g == "B+" -> 3.3; g == "B" -> 3.0; g == "B-" -> 2.7
                        g == "C+" -> 2.3; g == "C" -> 2.0; g == "C-" -> 1.7
                        g == "D" -> 1.3; g == "F" -> 0.0
                        g == "优+" -> 4.3; g == "优" -> 4.0; g == "优-" -> 3.7
                        g == "良+" -> 3.3; g == "良" -> 3.0; g == "良-" -> 2.7
                        g == "中+" -> 2.3; g == "中" -> 2.0; g == "中-" -> 1.7
                        g == "及格" -> 1.3; g == "不及格" -> 0.0
                        g == "通过" || g == "不通过" -> null
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    private fun extractFrSessionId(html: String): String {
        Regex("""FR\.SessionMgr\.register\(\s*['"](\d+)['"]""", RegexOption.IGNORE_CASE)
            .find(html)?.let { return it.groupValues[1] }
        Regex("""sessionID=(\d+)""", RegexOption.IGNORE_CASE)
            .find(html)?.let { return it.groupValues[1] }
        throw RuntimeException("FR Session ID 未找到")
    }

    private fun extractTotalPages(html: String): Int {
        return Regex("""FR\._p\.reportTotalPage\s*=\s*(\d+)""")
            .find(html)?.groupValues?.get(1)?.toInt() ?: 1
    }

    private fun parseCoursesFromHtml(html: String): List<ReportedGrade> {
        val doc = Ksoup.parse(html)
        val courses = mutableListOf<ReportedGrade>()
        var currentTerm: String? = null
        val cnNumMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6)

        val rows = doc.select("tbody tr")
        if (rows.isEmpty()) return emptyList()

        for (tr in rows) {
            val tds = tr.select("td")
            if (tds.isEmpty()) continue

            if (tds.size == 1) {
                val text = tds[0].text().trim().replace("\u3000", " ")
                val termMatch = Regex("""(\d{4})\s*-\s*(\d{4})\s*学年\s*(.+?)\s*学期""").find(text)
                if (termMatch != null) {
                    val y1 = termMatch.groupValues[1]
                    val y2 = termMatch.groupValues[2]
                    val termDisplay = termMatch.groupValues[3]
                    val termNo = termDisplay.toIntOrNull()
                        ?: cnNumMap[Regex("第(.)")?.find(termDisplay)?.groupValues?.get(1) ?: ""]
                        ?: continue
                    currentTerm = "$y1-$y2-$termNo"
                }
                continue
            }

            if (tds.size < 3 || currentTerm == null) continue

            val courseName = tds[0].text().trim().replace("\u3000", " ")
            val creditText = tds[1].text().trim()
            val scoreText = tds[2].text().trim()
                .replace("＋", "+").replace("－", "-").replace("—", "-")

            if (courseName in listOf("课程", "学分", "成绩") || creditText.toDoubleOrNull() == null) continue

            val credit = creditText.toDoubleOrNull() ?: continue
            val gpa = scoreToGpa(scoreText)
            courses.add(ReportedGrade(courseName, credit, scoreText, gpa, currentTerm))
        }
        return courses
    }

    suspend fun getReportedGrade(studentId: String, filterTerms: List<String>? = null): List<ReportedGrade> {
        val initUrl = "$FR_REPORT_URL?reportlet=bkdsglxjtu/XAJTDX_BDS_CJ.cpt&xh=$studentId"
        val initHtml = client.get(initUrl).bodyAsText()

        val sessionId = extractFrSessionId(initHtml)

        val firstPageUrl = "$FR_REPORT_URL?_=${currentTimeMillis()}&__boxModel__=true&op=page_content&sessionID=$sessionId&pn=1"
        val firstPageHtml = client.get(firstPageUrl).bodyAsText()

        val totalPages = extractTotalPages(firstPageHtml)
        val allCourses = mutableListOf<ReportedGrade>()
        allCourses.addAll(parseCoursesFromHtml(firstPageHtml))

        for (pn in 2..totalPages) {
            val pageUrl = "$FR_REPORT_URL?_=${currentTimeMillis()}&__boxModel__=true&op=page_content&sessionID=$sessionId&pn=$pn"
            val pageHtml = client.get(pageUrl).bodyAsText()
            allCourses.addAll(parseCoursesFromHtml(pageHtml))
        }

        return if (filterTerms != null) allCourses.filter { it.term in filterTerms } else allCourses
    }
}
