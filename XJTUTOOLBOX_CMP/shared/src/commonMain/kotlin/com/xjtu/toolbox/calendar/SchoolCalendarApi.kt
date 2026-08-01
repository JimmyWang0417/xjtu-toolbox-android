package com.xjtu.toolbox.calendar

import com.xjtu.toolbox.auth.XJTULogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "SchoolCalendarApi"
private const val BASE_URL = "http://one2020.xjtu.edu.cn"

data class CalendarEvent(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val name: String,
    val remark: String,
    val days: Int,
    val colorHex: String
)

data class SchoolTerm(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val termName: String,
    val yearName: String,
    val totalWeeks: Int,
    val workDays: Int,
    val events: List<CalendarEvent>
) {
    fun currentWeek(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Int {
        if (today < startDate || today > endDate) return 0
        return ((today.toEpochDays() - startDate.toEpochDays()) / 7 + 1)
    }

    fun currentDay(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Int {
        if (today < startDate) return 0
        return (today.toEpochDays() - startDate.toEpochDays() + 1)
    }

    fun totalDays(): Int = (endDate.toEpochDays() - startDate.toEpochDays() + 1)

    fun daysRemaining(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Int {
        if (today > endDate) return 0
        val from = if (today < startDate) startDate else today
        return (endDate.toEpochDays() - from.toEpochDays())
    }

    fun progress(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): Float {
        if (today <= startDate) return 0f
        if (today >= endDate) return 1f
        val total = totalDays().toFloat()
        val elapsed = currentDay(today).toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    fun todayEvent(today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): CalendarEvent? {
        return events.firstOrNull { today >= it.startDate && today <= it.endDate }
    }
}

class SchoolCalendarApi(private val login: XJTULogin? = null) {

    private val client: HttpClient = login?.client ?: HttpClient()

    suspend fun getTerms(): List<SchoolTerm> {
        try {
            client.get("$BASE_URL/EIP/edu/education/schoolcalendar/showCalendar.htm")
            Logger.d(TAG, "init page accessed")
        } catch (e: Exception) {
            Logger.w(TAG, "init page failed (continuing): ${e.message}")
        }

        val body = client.post("$BASE_URL/EIP/schoolcalendar/terms.htm") {
            header("X-Requested-With", "XMLHttpRequest")
            header("Origin", BASE_URL)
            header("Referer", "$BASE_URL/EIP/edu/education/schoolcalendar/showCalendar.htm")
        }.bodyAsText()

        Logger.d(TAG, "terms response (${body.length}): ${body.take(200)}")

        val json = body.safeParseJsonObject()
        val code = json["code"]?.jsonPrimitive?.int ?: -1
        if (code != 200) throw RuntimeException("校历接口返回 code=$code: ${json["msg"]?.jsonPrimitive?.content}")

        return json["data"]!!.jsonArray
            .map { parseTerm(it.jsonObject) }
            .sortedBy { it.startDate }
    }

    private fun parseTerm(obj: kotlinx.serialization.json.JsonObject): SchoolTerm {
        val events = obj["holidays"]?.jsonArray
            ?.mapNotNull { runCatching { parseEvent(it.jsonObject) }.getOrNull() }
            ?.sortedBy { it.startDate }
            ?: emptyList()
        return SchoolTerm(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            startDate = LocalDate.parse(obj["start_date"]!!.jsonPrimitive.content),
            endDate = LocalDate.parse(obj["end_date"]!!.jsonPrimitive.content),
            termName = obj["term_num"]?.jsonPrimitive?.content ?: "",
            yearName = obj["year_num"]?.jsonPrimitive?.content ?: "",
            totalWeeks = obj["week_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            workDays = obj["work_days"]?.jsonPrimitive?.int ?: 0,
            events = events
        )
    }

    private fun parseEvent(obj: kotlinx.serialization.json.JsonObject) = CalendarEvent(
        id = obj["id"]?.jsonPrimitive?.content ?: "",
        startDate = LocalDate.parse(obj["start_date"]!!.jsonPrimitive.content),
        endDate = LocalDate.parse(obj["end_date"]!!.jsonPrimitive.content),
        name = obj["holiday_name"]?.jsonPrimitive?.content ?: "",
        remark = obj["holiday_remark"]?.jsonPrimitive?.content ?: "",
        days = obj["holiday_days"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        colorHex = obj["holiday_color"]?.jsonPrimitive?.content ?: "#196dd0"
    )
}
