package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.LocalDate

class SchoolCalendarApi(
    private val client: HttpClient = platformHttpClient(),
) {
    private val baseUrl = "http://one2020.xjtu.edu.cn"

    suspend fun getTerms(): List<SchoolTerm> {
        runCatching {
            client.get("$baseUrl/EIP/edu/education/schoolcalendar/showCalendar.htm")
        }

        val body = client.post("$baseUrl/EIP/schoolcalendar/terms.htm") {
            header("X-Requested-With", "XMLHttpRequest")
            header("Origin", baseUrl)
            header("Referer", "$baseUrl/EIP/edu/education/schoolcalendar/showCalendar.htm")
        }.bodyAsText()

        val json = Json.parseToJsonElement(body).jsonObject
        val code = json["code"]?.jsonPrimitive?.int ?: -1
        if (code != 200) {
            val message = json["msg"]?.jsonPrimitive?.content ?: "unknown"
            throw RuntimeException("校历接口返回 code=$code: $message")
        }

        return json.getValue("data").jsonArray
            .map { parseTerm(it.jsonObject) }
            .sortedBy { it.startDate }
    }

    private fun parseTerm(obj: JsonObject): SchoolTerm {
        val events = obj["holidays"]?.jsonArray
            ?.mapNotNull { runCatching { parseEvent(it.jsonObject) }.getOrNull() }
            ?.sortedBy { it.startDate }
            ?: emptyList()
        return SchoolTerm(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            startDate = LocalDate.parse(obj.getValue("start_date").jsonPrimitive.content),
            endDate = LocalDate.parse(obj.getValue("end_date").jsonPrimitive.content),
            termName = obj["term_num"]?.jsonPrimitive?.content ?: "",
            yearName = obj["year_num"]?.jsonPrimitive?.content ?: "",
            totalWeeks = obj["week_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            workDays = obj["work_days"]?.jsonPrimitive?.int ?: 0,
            events = events,
        )
    }

    private fun parseEvent(obj: JsonObject) = CalendarEvent(
        id = obj["id"]?.jsonPrimitive?.content ?: "",
        startDate = LocalDate.parse(obj.getValue("start_date").jsonPrimitive.content),
        endDate = LocalDate.parse(obj.getValue("end_date").jsonPrimitive.content),
        name = obj["holiday_name"]?.jsonPrimitive?.content ?: "",
        remark = obj["holiday_remark"]?.jsonPrimitive?.content ?: "",
        days = obj["holiday_days"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        colorHex = obj["holiday_color"]?.jsonPrimitive?.content ?: "#196dd0",
    )
}
