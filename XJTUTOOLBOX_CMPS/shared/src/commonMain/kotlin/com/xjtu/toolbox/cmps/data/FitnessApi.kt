package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FitnessApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun years(ticket: AuthTicket): List<FitnessYear> {
        val root = postJson("$apiRoot/fitness/fitnessYear", ticket, mapOf("from" to "1"))
        return root["data"]?.jsonObject
            ?.get("list")?.jsonArray.orEmpty()
            .mapNotNull { element ->
                val item = element.jsonObject
                val yearNum = item["year_num"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                FitnessYear(
                    yearNum = yearNum,
                    name = item["name"]?.jsonPrimitive?.contentOrNull ?: yearNum,
                    checked = item["checked"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
    }

    suspend fun score(ticket: AuthTicket, yearNum: String? = null): FitnessScore {
        val selectedYear = yearNum ?: years(ticket).firstOrNull { it.checked }?.yearNum
            ?: years(ticket).firstOrNull()?.yearNum
            ?: error("暂无体测学年")
        val root = postJson("$apiRoot/Report/getStudentScore", ticket, mapOf("year_num" to selectedYear))
        val data = root["data"]?.jsonObject
            ?: error(root["info"]?.jsonPrimitive?.contentOrNull ?: "暂无体测数据")
        fun value(key: String): String = data[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun scoreValue(key: String): String = value(key).formatScore()
        fun item(name: String, key: String, display: String = value("${key}_score")) = FitnessItem(
            name = name,
            value = display.formatScore().ifBlank { "未测" },
            grade = scoreValue("${key}_grade").ifBlank { "缺项" },
            tone = value("${key}_class"),
        )
        val strengthName = if (value("sex") == "女") "仰卧起坐" else "引体向上"
        val runName = if (value("sex") == "女") "800 米" else "1000 米"
        val bmiDisplay = value("bmi_score_new").ifBlank { value("bmi_score") }
        return FitnessScore(
            studentNumber = value("student_num"),
            studentName = value("student_name"),
            totalScore = scoreValue("total_score").ifBlank { "--" },
            totalGrade = value("total_grade").ifBlank { "未测" },
            reportType = value("report_type"),
            reportStatus = value("report_status"),
            sex = value("sex"),
            grade = value("grade"),
            items = listOf(
                item("身高 / 体重", "bmi", bmiDisplay),
                item("肺活量", "vc"),
                item("立定跳远", "jump"),
                item("坐位体前屈", "sit_and_reach"),
                item(strengthName, "pull_and_sit"),
                item("50 米", "50m"),
                item(runName, "run"),
            ),
        )
    }

    private suspend fun postJson(url: String, ticket: AuthTicket, form: Map<String, String>) =
        Json.parseToJsonElement(
            client.submitForm(url = url, formParameters = parameters { form.forEach { (key, value) -> append(key, value) } }) {
                header("Origin", origin)
                header("Referer", ticket.cookies["fitness_referer"].orEmpty().ifBlank { homeUrl })
                header("X-Requested-With", "XMLHttpRequest")
            }.bodyAsText(),
        ).jsonObject.also { root ->
            val status = root["status"]?.jsonPrimitive?.intOrNull
            if (status != null && status != 1) {
                error(root["info"]?.jsonPrimitive?.contentOrNull ?: "体测查询失败")
            }
        }

    private fun String.formatScore(): String =
        trim().toDoubleOrNull()?.let { asNumber ->
            val text = asNumber.toString()
            if (text.endsWith(".0")) text.dropLast(2) else asNumber.toFixed2()
        } ?: this

    private fun Double.toFixed2(): String {
        val scaled = (this * 100).toInt()
        val whole = scaled / 100
        val fraction = (scaled % 100).toString().padStart(2, '0')
        return "$whole.$fraction"
    }

    companion object {
        private const val apiRoot = "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/public/index.php/index"
        private const val origin = "https://tyxylp.xjtu.edu.cn"
        private const val homeUrl = "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/view/h5xajt/#/pages/index/index"
    }
}
