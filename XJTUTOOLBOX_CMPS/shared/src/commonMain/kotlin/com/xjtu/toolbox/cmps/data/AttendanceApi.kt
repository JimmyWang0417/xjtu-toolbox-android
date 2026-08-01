package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AttendanceApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun summary(ticket: AuthTicket): AttendanceSummary {
        val student = postJson(ticket, "/attendance-student/global/getStuInfo")
            .dataObject()
        val terms = postJson(ticket, "/attendance-student/global/getBeforeTodayTerm")
            ["data"]?.jsonArray.orEmpty()
            .map { item ->
                val obj = item.jsonObject
                AttendanceTerm(
                    code = obj.str("bh"),
                    name = obj.str("name"),
                    startDate = obj.str("startDate").ifBlank { obj.str("kssj") },
                    endDate = obj.str("endDate").ifBlank { obj.str("jssj") },
                )
            }
        val termCode = postJson(ticket, "/attendance-student/global/getNearTerm")
            .dataObject()
            .str("bh")
        val records = waterRecords(ticket, termCode)
        val stats = courseStats(ticket).ifEmpty { computeStats(records) }
        return AttendanceSummary(
            studentName = student.str("name"),
            studentNo = student.str("sno").ifBlank { student.str("account") },
            departmentName = student.str("departmentName"),
            campusName = student.str("campusName"),
            terms = terms,
            records = records,
            flows = emptyList(),
            courseStats = stats,
        )
    }

    private suspend fun waterRecords(ticket: AuthTicket, termCode: String): List<AttendanceRecord> {
        val body = """{"startDate":"","endDate":"","current":1,"pageSize":500,"timeCondition":"","subjectBean":{"sCode":""},"classWaterBean":{"status":""},"classBean":{"termNo":"$termCode"}}"""
        val root = postJson(ticket, "/attendance-student/classWater/getClassWaterPage", body)
        return root.dataObject()["list"]?.jsonArray.orEmpty()
            .map { item ->
                val obj = item.jsonObject
                val classWater = obj.obj("classWaterBean")
                val account = obj.obj("accountBean")
                val build = obj.obj("buildBean")
                val room = obj.obj("roomBean")
                val subject = obj.obj("subjectBean")
                val id = classWater?.str("sBh").orEmpty()
                AttendanceRecord(
                    id = id,
                    termName = obj.str("termString"),
                    startSection = classWater?.int("startTime") ?: 0,
                    endSection = classWater?.int("endTime") ?: 0,
                    week = classWater?.int("week") ?: 0,
                    location = listOfNotNull(build?.str("buildingName"), room?.str("roomName")).filter { it.isNotBlank() }.joinToString(" "),
                    courseName = obj.str("subjectName").ifBlank { subject?.str("subjectname").orEmpty() },
                    teacher = obj.str("teachNameList"),
                    status = attendanceStatus(classWater?.int("status") ?: 1),
                    date = account?.str("checkdate").orEmpty(),
                )
            }
    }

    private suspend fun courseStats(ticket: AuthTicket): List<CourseAttendanceStat> =
        postJson(ticket, "/attendance-student/kqtj/getKqtjCurrentWeek")
            ["data"]?.jsonArray.orEmpty()
            .mapNotNull { item ->
                val obj = item.jsonObject
                val name = obj.str("subjectname")
                if (name.isBlank()) return@mapNotNull null
                CourseAttendanceStat(
                    subjectName = name,
                    subjectCode = obj.str("subjectCode"),
                    normalCount = obj.int("normalCount") ?: 0,
                    lateCount = obj.int("lateCount") ?: 0,
                    absenceCount = obj.int("absenceCount") ?: 0,
                    leaveEarlyCount = obj.int("leaveEarlyCount") ?: 0,
                    leaveCount = obj.int("leaveCount") ?: 0,
                    total = obj.int("total") ?: 0,
                )
            }

    private fun computeStats(records: List<AttendanceRecord>): List<CourseAttendanceStat> =
        records.groupBy { it.courseName }
            .filterKeys { it.isNotBlank() }
            .map { (name, recs) ->
                CourseAttendanceStat(
                    subjectName = name,
                    subjectCode = "",
                    normalCount = recs.count { it.status == AttendanceStatus.Normal },
                    lateCount = recs.count { it.status == AttendanceStatus.Late },
                    absenceCount = recs.count { it.status == AttendanceStatus.Absence },
                    leaveEarlyCount = 0,
                    leaveCount = recs.count { it.status == AttendanceStatus.Leave },
                    total = recs.size,
                )
            }

    private suspend fun postJson(ticket: AuthTicket, path: String, body: String = "") =
        Json.parseToJsonElement(
            client.post("$baseUrl$path") {
                header("Synjones-Auth", "bearer ${ticket.cookies["attendance_token"].orEmpty()}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText(),
        ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.dataObject() =
        this["data"]?.jsonObject ?: Json.parseToJsonElement("{}").jsonObject

    private fun kotlinx.serialization.json.JsonObject.obj(key: String) =
        this[key]?.takeIf { it is kotlinx.serialization.json.JsonObject }?.jsonObject

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun kotlinx.serialization.json.JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun attendanceStatus(value: Int): AttendanceStatus =
        when (value) {
            2 -> AttendanceStatus.Late
            3 -> AttendanceStatus.Absence
            5 -> AttendanceStatus.Leave
            else -> AttendanceStatus.Normal
        }

    companion object {
        private const val baseUrl = "https://bkkq.xjtu.edu.cn"
    }
}
