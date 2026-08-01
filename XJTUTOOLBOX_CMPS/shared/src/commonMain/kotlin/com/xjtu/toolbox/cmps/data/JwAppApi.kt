package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JwAppApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun currentTerm(ticket: AuthTicket): String {
        val root = postJson("$baseUrl/jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do", ticket)
        return root["datas"]?.jsonObject
            ?.get("dqxnxq")?.jsonObject
            ?.get("rows")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("DM")?.jsonPrimitive?.contentOrNull
            ?: error("教务当前学期为空")
    }

    suspend fun weeklySchedule(ticket: AuthTicket, termCode: String = currentTerm(ticket)): List<ScheduleCourse> {
        val root = postJson(
            "$baseUrl/jwapp/sys/wdkb/modules/xskcb/xskcb.do",
            ticket,
            form = mapOf("XNXQDM" to termCode),
        )
        return root["datas"]?.jsonObject
            ?.get("xskcb")?.jsonObject
            ?.get("rows")?.jsonArray.orEmpty()
            .mapIndexed { index, item ->
                val obj = item.jsonObject
                ScheduleCourse(
                    name = obj.str("KCM"),
                    teacher = obj.str("SKJS"),
                    location = obj.str("JASMC"),
                    dayOfWeek = obj.int("SKXQ") ?: 1,
                    sections = (obj.int("KSJC") ?: 1)..(obj.int("JSJC") ?: 1),
                    weeks = obj.str("SKZC").toWeekRangeText(),
                    colorSeed = index + 1,
                )
            }
    }

    suspend fun examSchedule(ticket: AuthTicket, termCode: String = currentTerm(ticket)): List<ExamItem> {
        val root = postJson(
            "$baseUrl/jwapp/sys/studentWdksapApp/modules/wdksap/wdksap.do",
            ticket,
            form = mapOf("XNXQDM" to termCode, "*order" to "-KSRQ,-KSSJMS"),
        )
        return root["datas"]?.jsonObject
            ?.get("wdksap")?.jsonObject
            ?.get("rows")?.jsonArray.orEmpty()
            .map { item ->
                val obj = item.jsonObject
                val rawDate = obj.str("KSRQ")
                val date = rawDate.substringBefore(" ")
                val rawTime = obj.str("KSSJMS")
                ExamItem(
                    courseName = obj.str("KCM").ifBlank { obj.str("KCMC").ifBlank { obj.str("KCH") } },
                    courseCode = obj.str("KCH"),
                    examDate = date,
                    examTime = rawTime.replace(date, "").replace(rawDate, "").trim().trimStart('-', ' ').ifBlank { rawTime },
                    location = obj.str("JASMC"),
                    seatNumber = obj.str("ZWH"),
                )
            }
    }

    suspend fun termScores(ticket: AuthTicket, termCode: String? = null): List<TermScore> {
        val root = postJsonBody(
            "$jwAppBase/api/biz/v410/score/termScore",
            ticket,
            """{"termCode":"${termCode ?: "*"}"}""",
        )
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: 0
        if (code != 200) error(root["msg"]?.jsonPrimitive?.contentOrNull ?: "教务成绩接口错误")
        return root["data"]?.jsonObject
            ?.get("termScoreList")?.jsonArray.orEmpty()
            .map { termElement ->
                val termObj = termElement.jsonObject
                val term = termObj.str("termCode")
                TermScore(
                    termCode = term,
                    termName = termObj.str("termName"),
                    scoreList = termObj["scoreList"]?.jsonArray.orEmpty()
                        .map { scoreElement ->
                            val item = scoreElement.jsonObject
                            val score = item.str("score")
                            ScoreRecord(
                                courseName = item.str("courseName"),
                                credit = item.double("coursePoint") ?: 0.0,
                                score = score,
                                gpa = item.double("gpa")?.takeIf { it > 0.0 } ?: scoreToGpa(score),
                                term = item.str("termCode").ifBlank { term },
                                selectedForGpa = item.str("examProp") != "重修",
                            )
                        },
                )
            }
    }

    private suspend fun postJson(
        url: String,
        ticket: AuthTicket,
        form: Map<String, String> = emptyMap(),
    ) = Json.parseToJsonElement(
        client.submitForm(
            url = url,
            formParameters = parameters { form.forEach { (key, value) -> append(key, value) } },
        ) {
            header(HttpHeaders.Authorization, ticket.cookies["jwapp_token"].orEmpty())
            header(HttpHeaders.UserAgent, browserUserAgent)
            header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText(),
    ).jsonObject

    private suspend fun postJsonBody(url: String, ticket: AuthTicket, body: String) =
        Json.parseToJsonElement(
            client.post(url) {
                header(HttpHeaders.Authorization, ticket.cookies["jwapp_token"].orEmpty())
                header(HttpHeaders.UserAgent, browserUserAgent)
                header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText(),
        ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun kotlinx.serialization.json.JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun kotlinx.serialization.json.JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun String.toWeekRangeText(): String {
        val weeks = mapIndexedNotNull { index, char -> if (char == '1') index + 1 else null }
        if (weeks.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var start = weeks.first()
        var previous = start
        weeks.drop(1).forEach { week ->
            if (week == previous + 1) {
                previous = week
            } else {
                ranges += if (start == previous) "$start" else "$start-$previous"
                start = week
                previous = week
            }
        }
        ranges += if (start == previous) "$start" else "$start-$previous"
        return ranges.joinToString(",") + "周"
    }

    companion object {
        private const val baseUrl = "https://jwxt.xjtu.edu.cn"
        private const val jwAppBase = "https://jwapp.xjtu.edu.cn"
        private const val browserUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun scoreToGpa(score: String): Double? {
            val numeric = score.toDoubleOrNull()
            if (numeric != null) {
                return when {
                    numeric >= 95 -> 4.3
                    numeric >= 90 -> 4.0
                    numeric >= 85 -> 3.7
                    numeric >= 81 -> 3.3
                    numeric >= 78 -> 3.0
                    numeric >= 75 -> 2.7
                    numeric >= 72 -> 2.3
                    numeric >= 68 -> 2.0
                    numeric >= 64 -> 1.7
                    numeric >= 60 -> 1.3
                    else -> 0.0
                }
            }
            val normalized = score
                .replace('＋', '+')
                .replace('－', '-')
                .replace(Regex("[^a-zA-Z0-9+\\-\\u4e00-\\u9fff]"), "")
                .uppercase()
            return when (normalized) {
                "A+", "优+" -> 4.3
                "A", "优" -> 4.0
                "A-", "优-" -> 3.7
                "B+", "良+" -> 3.3
                "B", "良" -> 3.0
                "B-", "良-" -> 2.7
                "C+", "中+" -> 2.3
                "C", "中" -> 2.0
                "C-", "中-" -> 1.7
                "D", "及格" -> 1.3
                "F", "不及格" -> 0.0
                "通过", "不通过" -> null
                else -> null
            }
        }
    }
}
