package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UndergraduateJudgeApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun dashboard(ticket: AuthTicket): Pair<String, List<JudgeQuestionnaire>> {
        warmUp(ticket)
        val term = currentTerm(ticket)
        val unfinished = questionnaires(ticket, "05", term, finished = false) +
            questionnaires(ticket, "01", term, finished = false)
        val finished = questionnaires(ticket, "05", term, finished = true) +
            questionnaires(ticket, "01", term, finished = true)
        return term to (unfinished + finished)
    }

    private suspend fun warmUp(ticket: AuthTicket) {
        runCatching {
            client.get("$baseUrl/modules/xspj/index.do") {
                addJwxtHeaders(ticket, "text/html")
            }.bodyAsText()
        }
    }

    private suspend fun currentTerm(ticket: AuthTicket): String {
        val root = postForm(
            ticket = ticket,
            url = "$baseUrl/modules/xspj/cxxtcs.do",
            parameters = Parameters.build {
                append(
                    "setting",
                    "[{\"name\":\"CSDM\",\"value\":\"PJGLPJSJ\",\"builder\":\"equal\",\"linkOpt\":\"AND\"}" +
                        ",{\"name\":\"ZCSDM\",\"value\":\"PJXNXQ\",\"builder\":\"m_value_equal\",\"linkOpt\":\"AND\"}]",
                )
            },
        )
        val rows = root.rows("cxxtcs")
        return rows.firstOrNull()?.string("CSZA")?.takeIf { it.isNotBlank() }
            ?: error("评教学期数据为空")
    }

    private suspend fun questionnaires(
        ticket: AuthTicket,
        type: String,
        term: String,
        finished: Boolean,
    ): List<JudgeQuestionnaire> {
        val root = postForm(
            ticket = ticket,
            url = "$baseUrl/modules/xspj/cxdwpj.do",
            parameters = Parameters.build {
                append("PGLXDM", type)
                append("SFPG", if (finished) "1" else "0")
                append("SFKF", "1")
                append("SFFB", "1")
                append("XNXQDM", term)
            },
        )
        return root.rows("cxdwpj").map { obj ->
            JudgeQuestionnaire(
                id = listOf(obj.string("WJDM"), obj.string("JXBID"), obj.string("BPR"), type).joinToString("_"),
                courseName = obj.string("KCM"),
                teacherName = obj.string("BPJS").ifBlank { obj.string("BPR") },
                termCode = obj.string("XNXQDM").ifBlank { term },
                startTime = obj.string("KSSJ"),
                endTime = obj.string("JSSJ"),
                typeCode = obj.string("PGLXDM").ifBlank { type },
                typeName = when (obj.string("PGLXDM").ifBlank { type }) {
                    "01" -> "期末评教"
                    "05" -> "过程评教"
                    else -> obj.string("WJMC").ifBlank { "评教" }
                },
                finished = finished,
            )
        }
    }

    private suspend fun postForm(ticket: AuthTicket, url: String, parameters: Parameters): JsonObject {
        val text = client.submitForm(url = url, formParameters = parameters) {
            addJwxtHeaders(ticket, "application/json, text/javascript, */*; q=0.01")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()
        if (!text.trimStart().startsWith("{") || isAuthFailure(text)) error("教务评教登录态已失效")
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addJwxtHeaders(ticket: AuthTicket, accept: String) {
        header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
        header(HttpHeaders.Accept, accept)
        header(HttpHeaders.UserAgent, browserUa)
        header("Referer", "$baseUrl/modules/xspj/index.do")
    }

    companion object {
        const val loginUrl = "https://jwxt.xjtu.edu.cn/jwapp/sys/homeapp/index.do"
        private const val baseUrl = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp"
    }
}

class GraduateJudgeApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun questionnaires(ticket: AuthTicket): List<GraduateJudgeQuestionnaire> {
        val text = client.get("$baseUrl/app/sshd4Stu/list.do") {
            header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
            header(HttpHeaders.Accept, "application/json, text/plain, */*")
            header(HttpHeaders.UserAgent, browserUa)
        }.bodyAsText()
        if (isAuthFailure(text)) error("研究生评教登录态已失效")
        val array = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.toGraduateQuestionnaire() }
    }

    private fun JsonObject.toGraduateQuestionnaire(): GraduateJudgeQuestionnaire =
        GraduateJudgeQuestionnaire(
            assessment = string("assessment"),
            classId = string("bjid"),
            className = string("bjmc"),
            teachingClassId = int("data_jxb_id"),
            teacherId = string("jsbh"),
            teacherName = string("jsxm"),
            courseCode = string("kcbh"),
            courseName = string("kcmc"),
            department = string("kkdw"),
            termCode = string("termcode"),
            termName = string("termname"),
            status = string("assessment"),
        )

    companion object {
        const val baseUrl = "http://gste.xjtu.edu.cn"
        const val loginUrl = "https://cas.xjtu.edu.cn/login?TARGET=http%3A%2F%2Fgste.xjtu.edu.cn%2Flogin.do"
    }
}

private fun JsonObject.rows(name: String): List<JsonObject> {
    val datas = this["datas"] as? JsonObject ?: return emptyList()
    val table = datas[name] as? JsonObject ?: return emptyList()
    val rows = table["rows"] as? JsonArray ?: return emptyList()
    return rows.mapNotNull { it as? JsonObject }
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun Map<String, String>.toCookieHeader(): String =
    entries.joinToString("; ") { (key, value) -> "$key=$value" }

private fun isAuthFailure(text: String): Boolean =
    text.contains("login.xjtu.edu.cn/cas/login", ignoreCase = true) ||
        text.contains("cas.xjtu.edu.cn/login", ignoreCase = true) ||
        text.contains("name=\"execution\"", ignoreCase = true) ||
        text.contains("统一身份认证", ignoreCase = true)

private const val browserUa =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
