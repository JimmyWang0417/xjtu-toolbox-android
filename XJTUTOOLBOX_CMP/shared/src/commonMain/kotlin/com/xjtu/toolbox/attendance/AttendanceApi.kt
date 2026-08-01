package com.xjtu.toolbox.attendance

import com.xjtu.toolbox.auth.AttendanceLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val TAG = "AttendanceApi"

enum class FlowRecordType(val value: Int) {
    INVALID(0), VALID(1), REPEATED(2);
    companion object { fun fromValue(v: Int) = entries.first { it.value == v } }
}

enum class WaterType(val value: Int) {
    NORMAL(1), LATE(2), ABSENCE(3), LEAVE(5);
    val displayName: String get() = when (this) {
        NORMAL -> "正常"; LATE -> "迟到"; ABSENCE -> "缺勤"; LEAVE -> "请假"
    }
    companion object { fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: NORMAL }
}

data class AttendanceFlow(
    val sbh: String, val place: String, val waterTime: String, val type: FlowRecordType
)

data class AttendanceWaterRecord(
    val sbh: String, val termString: String, val startTime: Int, val endTime: Int,
    val week: Int, val location: String, val courseName: String, val teacher: String,
    val status: WaterType, val date: String
)

data class TermInfo(
    val bh: String, val name: String, val startDate: String = "", val endDate: String = ""
)

data class CourseAttendanceStat(
    val subjectName: String, val subjectCode: String,
    val normalCount: Int, val lateCount: Int, val absenceCount: Int,
    val leaveEarlyCount: Int, val leaveCount: Int, val total: Int
) {
    val actualCount: Int get() = normalCount + leaveCount
    val abnormalCount: Int get() = lateCount + absenceCount
}

class AttendanceApi(private val login: AttendanceLogin) {

    private val baseUrl = "http://bkkq.xjtu.edu.cn"

    private suspend fun post(path: String, jsonBody: String? = null): String {
        val (authKey, authVal) = login.authHeader()
        return login.executeWithReAuth {
            val resp = login.client.post("$baseUrl$path") {
                header(authKey, authVal)
                if (jsonBody != null) {
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
            }
            val body = resp.bodyAsText()
            Logger.d(TAG, "POST $path → ${resp.status.value}, len=${body.length}")
            resp.status.value to body
        }
    }

    suspend fun getStudentInfo(): Map<String, String> {
        val result = post("/attendance-student/global/getStuInfo")
        val json = result.safeParseJsonObject()
        val data = json["data"]?.jsonObject ?: run {
            Logger.w(TAG, "getStudentInfo: data null")
            return mapOf("name" to "", "sno" to "")
        }
        return mapOf(
            "name" to data["name"].safeString(),
            "sno" to data["sno"].safeString().ifEmpty { data["account"].safeString() },
            "identity" to data["identity"].safeString(),
            "campusName" to data["campusName"].safeString(),
            "departmentName" to data["departmentName"].safeString()
        )
    }

    suspend fun getTermBh(): String {
        val result = post("/attendance-student/global/getNearTerm")
        val json = result.safeParseJsonObject()
        val data = json["data"]?.jsonObject
            ?: throw RuntimeException("getNearTerm: data 为空")
        return data["bh"].safeString().ifEmpty {
            throw RuntimeException("getNearTerm: bh 字段缺失")
        }
    }

    suspend fun getTermList(): List<TermInfo> {
        val result = post("/attendance-student/global/getBeforeTodayTerm")
        val json = result.safeParseJsonObject()
        val data = json["data"]?.jsonArray ?: return emptyList()
        return data.map { item ->
            val obj = item.jsonObject
            TermInfo(
                bh = obj["bh"].safeString(),
                name = obj["name"].safeString(),
                startDate = obj["startDate"].safeString()
                    .ifEmpty { obj["kssj"].safeString() }
                    .ifEmpty { obj["startTime"].safeString() },
                endDate = obj["endDate"].safeString()
                    .ifEmpty { obj["jssj"].safeString() }
                    .ifEmpty { obj["endTime"].safeString() }
            )
        }
    }

    suspend fun getFlowRecords(date: String? = null): List<AttendanceFlow> {
        val queryDate = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val jsonBody = """{"startdate":"$queryDate","enddate":"$queryDate","current":1,"pageSize":200,"calendarBh":""}"""
        val result = post("/attendance-student/waterList/page", jsonBody)
        val json = result.safeParseJsonObject()
        val list = json["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
        return list.map { item ->
            val obj = item.jsonObject
            AttendanceFlow(
                sbh = obj["sBh"].safeString(),
                place = obj["eqno"].safeString(),
                waterTime = obj["watertime"].safeString(),
                type = FlowRecordType.fromValue(obj["isdone"].safeInt())
            )
        }
    }

    suspend fun getFlowRecordsByRange(startDate: String, endDate: String): List<AttendanceFlow> {
        val jsonBody = """{"startdate":"$startDate","enddate":"$endDate","current":1,"pageSize":200,"calendarBh":""}"""
        val result = post("/attendance-student/waterList/page", jsonBody)
        val json = result.safeParseJsonObject()
        val list = json["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
        return list.map { item ->
            val obj = item.jsonObject
            AttendanceFlow(
                sbh = obj["sBh"].safeString(),
                place = obj["eqno"].safeString(),
                waterTime = obj["watertime"].safeString(),
                type = FlowRecordType.fromValue(obj["isdone"].safeInt())
            )
        }
    }

    suspend fun getWaterRecords(
        termBh: String? = null, startDate: String = "", endDate: String = ""
    ): List<AttendanceWaterRecord> {
        val bh = termBh ?: getTermBh()
        val endDateFormatted = if (endDate.isNotEmpty() && !endDate.contains(" ")) "$endDate 23:59:59" else endDate
        val jsonBody = """{"startDate":"$startDate","endDate":"$endDateFormatted","current":1,"pageSize":500,"timeCondition":"","subjectBean":{"sCode":""},"classWaterBean":{"status":""},"classBean":{"termNo":"$bh"}}"""
        val result = post("/attendance-student/classWater/getClassWaterPage", jsonBody)
        val json = result.safeParseJsonObject()
        val list = json["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
        return list.map { item ->
            val obj = item.jsonObject
            val classWater = obj["classWaterBean"]?.jsonObject
            val account = obj["accountBean"]?.jsonObject
            val build = obj["buildBean"]?.jsonObject
            val room = obj["roomBean"]?.jsonObject
            val calendar = obj["calendarBean"]?.jsonObject
            val subject = obj["subjectBean"]?.jsonObject
            AttendanceWaterRecord(
                sbh = classWater?.get("bh").safeString(),
                termString = calendar?.get("name").safeString(),
                startTime = account?.get("startJc").safeInt(),
                endTime = account?.get("endJc").safeInt(),
                week = account?.get("week").safeInt(),
                location = "${build?.get("name").safeString()}-${room?.get("roomnum").safeString()}",
                courseName = subject?.get("sName").safeString()
                    .ifEmpty { subject?.get("subjectname").safeString() },
                teacher = obj["teachNameList"].safeString(),
                status = WaterType.fromValue(classWater?.get("status").safeInt().let { if (it == 0) 1 else it }),
                date = account?.get("checkdate").safeString()
            )
        }
    }

    suspend fun getKqtjCurrentWeek(): List<CourseAttendanceStat> {
        val result = post("/attendance-student/kqtj/getKqtjCurrentWeek")
        return parseKqtjList(result)
    }

    suspend fun getKqtjByTime(startDate: String, endDate: String): List<CourseAttendanceStat> {
        val jsonBody = """{"startDate":"$startDate","endDate":"$endDate 23:59:59"}"""
        val result = post("/attendance-student/kqtj/getKqtjByTime", jsonBody)
        return parseKqtjList(result)
    }

    fun computeCourseStatsFromRecords(records: List<AttendanceWaterRecord>): List<CourseAttendanceStat> {
        if (records.isEmpty()) return emptyList()
        return records.groupBy { it.courseName }
            .filter { it.key.isNotEmpty() }
            .map { (name, recs) ->
                CourseAttendanceStat(
                    subjectName = name, subjectCode = "",
                    normalCount = recs.count { it.status == WaterType.NORMAL },
                    lateCount = recs.count { it.status == WaterType.LATE },
                    absenceCount = recs.count { it.status == WaterType.ABSENCE },
                    leaveEarlyCount = 0,
                    leaveCount = recs.count { it.status == WaterType.LEAVE },
                    total = recs.size
                )
            }
    }

    private fun parseKqtjList(result: String): List<CourseAttendanceStat> {
        val json = result.safeParseJsonObject()
        val data = json["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj["subjectname"].safeString()
            if (name.isEmpty()) return@mapNotNull null
            CourseAttendanceStat(
                subjectName = name,
                subjectCode = obj["subjectCode"].safeString(),
                normalCount = obj["normalCount"].safeInt(),
                lateCount = obj["lateCount"].safeInt(),
                absenceCount = obj["absenceCount"].safeInt(),
                leaveEarlyCount = obj["leaveEarlyCount"].safeInt(),
                leaveCount = obj["leaveCount"].safeInt(),
                total = obj["total"].safeInt()
            )
        }
    }
}
