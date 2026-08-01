package com.xjtu.toolbox.jwapp

import com.xjtu.toolbox.auth.JwappLogin
import com.xjtu.toolbox.score.ScoreReportApi
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeBoolean
import com.xjtu.toolbox.util.safeDouble
import com.xjtu.toolbox.util.safeDoubleOrNull
import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import com.xjtu.toolbox.util.safeStringOrNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "JwappGPA"

// ── 数据类 ──────────────────────────────

enum class ScoreSource { JWAPP, REPORT }

enum class CourseGroup(val label: String, val shortLabel: String) {
    GEN_CORE("通核", "通核"),
    GEN_ELECTIVE("通选", "通选");
}

data class ScoreItem(
    val id: String,
    val termCode: String,
    val courseName: String,
    val score: String,
    val scoreValue: Double?,
    val passFlag: Boolean,
    val specificReason: String?,
    val coursePoint: Double,
    val examType: String,
    val majorFlag: String?,
    val examProp: String,
    val replaceFlag: Boolean,
    val gpa: Double? = null,
    val source: ScoreSource = ScoreSource.JWAPP,
    val courseCategory: String? = null,
    val courseCode: String? = null,
    val courseGroup: CourseGroup? = null,
)

data class ScoreDetailItem(
    val itemName: String, val itemPercent: Double,
    val itemScore: String, val itemScoreValue: Double?
)

data class ScoreDetail(
    val courseName: String, val coursePoint: Double,
    val examType: String, val majorFlag: String?, val examProp: String,
    val replaceFlag: Boolean, val score: String, val scoreValue: Double?,
    val gpa: Double, val passFlag: Boolean, val specificReason: String?,
    val itemList: List<ScoreDetailItem>
)

data class TermScore(
    val termCode: String, val termName: String, val scoreList: List<ScoreItem>
)

data class ScoreRank(
    val defeatPercent: Double?, val scoreHigh: Double?,
    val scoreAvg: Double?, val scoreLow: Double?,
    val scoreDist: List<ScoreDistRange>
)

data class ScoreDistRange(val range: String, val num: Int)

data class TimeTableBasis(
    val termCode: String, val termName: String,
    val maxWeekNum: Int, val maxSection: Int,
    val todayWeekDay: Int, val todayWeekNum: Int
)

data class GpaInfo(
    val gpa: Double, val averageScore: Double,
    val totalCredits: Double, val courseCount: Int
)

// ── API ──────────────────────────────

class JwappApi(private val login: JwappLogin) {

    private val baseUrl = "http://jwapp.xjtu.edu.cn"

    private var cachedBasis: TimeTableBasis? = null
    private var cachedBasisTime: Long = 0L
    private val BASIS_TTL_MS = 60L * 60 * 1000L

    private suspend fun postJson(url: String, jsonBody: String): String {
        val token = login.authToken ?: ""
        val resp = login.client.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(jsonBody)
        }
        if (resp.status.value in listOf(401, 403)) {
            if (login.reAuthenticate()) {
                val newToken = login.authToken ?: ""
                return login.client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $newToken")
                    setBody(jsonBody)
                }.bodyAsText()
            }
        }
        return resp.bodyAsText()
    }

    private suspend fun getJson(url: String): String {
        val token = login.authToken ?: ""
        val resp = login.client.get(url) {
            header("Authorization", "Bearer $token")
        }
        if (resp.status.value in listOf(401, 403)) {
            if (login.reAuthenticate()) {
                val newToken = login.authToken ?: ""
                return login.client.get(url) {
                    header("Authorization", "Bearer $newToken")
                }.bodyAsText()
            }
        }
        return resp.bodyAsText()
    }

    suspend fun getGrade(termCode: String? = null): List<TermScore> {
        val code = termCode ?: "*"
        val jsonBody = buildJsonObject { put("termCode", code) }.toString()
        val responseBody = postJson("$baseUrl/api/biz/v410/score/termScore", jsonBody)

        val root = responseBody.safeParseJsonObject()
        val resultCode = root["code"]?.jsonPrimitive?.int ?: -1
        if (resultCode != 200) {
            throw RuntimeException(root["msg"]?.jsonPrimitive?.content ?: "服务器错误 ($resultCode)")
        }

        val termScoreList = root["data"]!!.jsonObject["termScoreList"]!!.jsonArray
        return termScoreList.map { termElement ->
            val termObj = termElement.jsonObject
            val scores = termObj["scoreList"]!!.jsonArray.map { scoreEl ->
                val s = scoreEl.jsonObject
                val rawScore = s["score"].safeString()
                val numericScore = rawScore.toDoubleOrNull()
                val courseName = s["courseName"].safeString()
                val extractedCode = Regex("\\(([A-Z]{2,}\\d{4,}\\w*)\\)$")
                    .find(courseName.trim())?.groupValues?.get(1)

                ScoreItem(
                    id = s["id"].safeString(),
                    termCode = s["termCode"].safeString(),
                    courseName = courseName,
                    score = rawScore,
                    scoreValue = numericScore,
                    passFlag = s["passFlag"].safeBoolean(),
                    specificReason = s["specificReason"].safeStringOrNull(),
                    coursePoint = s["coursePoint"].safeDouble(),
                    examType = s["examType"].safeString(),
                    majorFlag = s["majorFlag"].safeStringOrNull(),
                    examProp = s["examProp"].safeString(),
                    replaceFlag = s["replaceFlag"].safeBoolean(),
                    gpa = s["gpa"].safeDoubleOrNull(),
                    courseCode = extractedCode
                )
            }
            TermScore(
                termCode = termObj["termCode"].safeString(),
                termName = termObj["termName"].safeString(),
                scoreList = scores
            )
        }
    }

    suspend fun getDetail(courseId: String): ScoreDetail {
        val jsonBody = buildJsonObject { put("id", courseId) }.toString()
        val responseBody = postJson("$baseUrl/api/biz/v410/score/scoreDetail", jsonBody)
        val root = responseBody.safeParseJsonObject()
        val resultCode = root["code"]!!.jsonPrimitive.int
        if (resultCode != 200) {
            throw RuntimeException(root["msg"]?.jsonPrimitive?.content ?: "服务器错误 ($resultCode)")
        }

        val data = root["data"]!!.jsonObject
        val items = data["itemList"]?.jsonArray?.map { itemEl ->
            val item = itemEl.jsonObject
            val percentStr = item["itemPercent"].safeString("0")
            val percent = percentStr.trimEnd('%').toDoubleOrNull()?.let { it / 100.0 } ?: 0.0
            ScoreDetailItem(
                itemName = item["itemName"].safeString(),
                itemPercent = percent,
                itemScore = item["itemScore"].safeString(),
                itemScoreValue = item["itemScore"].safeString().toDoubleOrNull()
            )
        } ?: emptyList()

        val rawScore = data["score"].safeString()
        val serverGpa = data["gpa"].safeDouble()
        val effectiveGpa = if (serverGpa > 0.0) serverGpa
        else ScoreReportApi.scoreToGpa(rawScore) ?: 0.0

        return ScoreDetail(
            courseName = data["courseName"].safeString(),
            coursePoint = data["coursePoint"].safeDouble(),
            examType = data["examType"].safeString(),
            majorFlag = data["majorFlag"].safeStringOrNull(),
            examProp = data["examProp"].safeString(),
            replaceFlag = data["replaceFlag"].safeBoolean(),
            score = rawScore,
            scoreValue = rawScore.toDoubleOrNull(),
            gpa = effectiveGpa,
            passFlag = data["passFlag"].safeBoolean(),
            specificReason = data["specificReason"].safeStringOrNull(),
            itemList = items
        )
    }

    suspend fun getRank(courseId: String): ScoreRank {
        val jsonBody = buildJsonObject { put("id", courseId) }.toString()
        val responseBody = postJson("$baseUrl/api/biz/v410/score/scoreAnalyze", jsonBody)
        val root = responseBody.safeParseJsonObject()
        val resultCode = root["code"]!!.jsonPrimitive.int
        if (resultCode != 200) {
            throw RuntimeException(root["msg"]?.jsonPrimitive?.content ?: "服务器错误 ($resultCode)")
        }

        val data = root["data"]!!.jsonObject
        val dist = data["scoreDist"]?.jsonArray?.map { distEl ->
            val d = distEl.jsonObject
            ScoreDistRange(range = d["range"].safeString(), num = d["num"].safeInt())
        } ?: emptyList()

        return ScoreRank(
            defeatPercent = data["defeatPercent"].safeDoubleOrNull(),
            scoreHigh = data["scoreHigh"].safeDoubleOrNull(),
            scoreAvg = data["scoreAvg"].safeDoubleOrNull(),
            scoreLow = data["scoreLow"].safeDoubleOrNull(),
            scoreDist = dist
        )
    }

    suspend fun getTimeTableBasis(): TimeTableBasis {
        cachedBasis?.let {
            if (currentTimeMillis() - cachedBasisTime < BASIS_TTL_MS) return it
            cachedBasis = null
        }

        val body = getJson("https://jwapp.xjtu.edu.cn/api/biz/v410/common/school/time")
        val root = body.safeParseJsonObject()
        val resultCode = root["code"]!!.jsonPrimitive.int
        if (resultCode != 200) {
            throw RuntimeException(root["msg"]?.jsonPrimitive?.content ?: "服务器错误 ($resultCode)")
        }

        val obj = if (root["data"]?.jsonObject != null) root["data"]!!.jsonObject else root

        return TimeTableBasis(
            termCode = obj["xnxqdm"].safeString(),
            termName = obj["xnxqmc"].safeString(),
            maxWeekNum = obj["maxWeekNum"].safeInt(),
            maxSection = obj["maxSection"].safeInt(),
            todayWeekDay = obj["todayWeekDay"].safeInt(),
            todayWeekNum = obj["todayWeekNum"].safeInt()
        ).also { cachedBasis = it; cachedBasisTime = currentTimeMillis() }
    }

    suspend fun getCurrentTerm(): String = getTimeTableBasis().termCode
    suspend fun getCurrentWeek(): Int = getTimeTableBasis().todayWeekNum

    suspend fun getTermList(): List<Pair<String, String>> {
        val allGrades = getGrade(null)
        return allGrades.map { it.termCode to it.termName }
    }

    fun calculateGpaFromGrades(termScores: List<TermScore>): GpaInfo =
        calculateGpaForCourses(termScores.flatMap { it.scoreList })

    fun calculateGpaForCourses(courses: List<ScoreItem>): GpaInfo {
        var totalCredits = 0.0
        var weightedGpa = 0.0
        var weightedScore = 0.0
        var courseCount = 0

        for (score in courses) {
            val raw = score.score.trim()
            if (raw == "通过" || raw == "不通过") continue

            val courseGpa = score.gpa?.takeIf { it > 0.0 }
                ?: ScoreReportApi.scoreToGpa(raw)
                ?: 0.0
            val numeric = score.scoreValue ?: 0.0
            val passed = score.passFlag || courseGpa > 0.0 || numeric >= 60.0

            if (!passed && score.examProp == "初修") continue

            totalCredits += score.coursePoint
            weightedGpa += courseGpa * score.coursePoint
            weightedScore += numeric * score.coursePoint
            courseCount++
        }

        val gpa = if (totalCredits > 0) weightedGpa / totalCredits else 0.0
        val avg = if (totalCredits > 0) weightedScore / totalCredits else 0.0
        Logger.d(TAG, "GPA=${gpa}, 均分=${avg}, $courseCount/${courses.size}门, ${totalCredits}学分")
        return GpaInfo(gpa, avg, totalCredits, courseCount)
    }
}
