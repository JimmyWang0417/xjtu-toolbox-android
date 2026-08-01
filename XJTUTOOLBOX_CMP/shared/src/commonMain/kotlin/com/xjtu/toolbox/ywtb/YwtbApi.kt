package com.xjtu.toolbox.ywtb

import com.xjtu.toolbox.auth.YwtbLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

data class UserInfo(
    val userName: String,
    val userUid: String,
    val identityTypeName: String,
    val organizationName: String
)

class YwtbApi(private val login: YwtbLogin) {

    private suspend fun baseGet(url: String): String {
        suspend fun doGet(): String {
            val resp = login.client.get(url) {
                header("x-device-info", "PC")
                header("x-terminal-info", "PC")
                header("Referer", "https://ywtb.xjtu.edu.cn/main.html")
                login.idToken?.let { header("x-id-token", it) }
            }
            return resp.bodyAsText()
        }

        return try {
            val body = doGet()
            // 检查 token 过期
            if (body.contains("\"code\":401") || body.contains("\"code\":\"401\"") || !login.isTokenValid()) {
                if (login.reAuthenticate()) {
                    doGet()
                } else body
            } else body
        } catch (e: Exception) {
            if (login.reAuthenticate()) {
                doGet()
            } else throw e
        }
    }

    suspend fun getUserInfo(): UserInfo {
        val body = baseGet("https://authx-service.xjtu.edu.cn/personal/api/v1/personal/me/user")
        val json = body.safeParseJsonObject()

        val data = json["data"]?.jsonObject
        val attributes = data?.get("attributes")?.jsonObject

        return UserInfo(
            userName = attributes?.get("userName")?.jsonPrimitive?.content
                ?: data?.get("username")?.jsonPrimitive?.content ?: "",
            userUid = attributes?.get("userUid")?.jsonPrimitive?.content ?: "",
            identityTypeName = attributes?.get("identityTypeName")?.jsonPrimitive?.content ?: "",
            organizationName = attributes?.get("organizationName")?.jsonPrimitive?.content ?: ""
        )
    }

    /**
     * 获取今日的教学周信息
     * @return Triple(教学周数, 学期名, 学期ID)，假期返回 null
     */
    suspend fun getCurrentWeekOfTeaching(): Triple<Int, String, String>? {
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
        val todayStr = today.toString() // yyyy-MM-dd
        val url = URLBuilder("https://ywtb.xjtu.edu.cn/portal-api/v1/calendar/share/schedule/getWeekOfTeaching").apply {
            parameters.append("today", todayStr)
            parameters.append("random_number", Random.nextInt(100, 999).toString())
        }.buildString()

        Logger.d("YwtbWeek", "requesting: today=$todayStr")
        val responseBody = baseGet(url)
        Logger.d("YwtbWeek", "responseBody=${responseBody.take(500)}")
        val json = responseBody.safeParseJsonObject()
        val dataObj = json["data"]?.jsonObject?.get("data")?.jsonObject
        if (dataObj == null) {
            Logger.w("YwtbWeek", "data.data is null")
            return null
        }
        val dateArray = dataObj["date"]?.jsonArray
        if (dateArray == null || dateArray.isEmpty()) {
            Logger.w("YwtbWeek", "date array empty or null")
            return null
        }
        val weekStr = dateArray[0].jsonPrimitive.content
        val semesterAliArray = dataObj["semesterAlilist"]?.jsonArray
        val semesterArray = dataObj["semesterlist"]?.jsonArray
        val semesterName = if (semesterAliArray != null && semesterAliArray.isNotEmpty()) semesterAliArray[0].jsonPrimitive.content else ""
        val semesterId = if (semesterArray != null && semesterArray.isNotEmpty()) semesterArray[0].jsonPrimitive.content else ""

        val week = weekStr.toIntOrNull()
        Logger.d("YwtbWeek", "weekStr=$weekStr, week=$week, semesterName=$semesterName")
        if (week == null || week <= 0) return null
        return Triple(week, semesterName, semesterId)
    }

    suspend fun getStartOfTerm(timestamp: String): String {
        val parts = timestamp.split("-")
        require(parts.size == 3) { "格式错误，应为 YYYY-YYYY-S" }
        val yearStart = parts[0]
        val yearEnd = parts[1]
        val term = parts[2]

        val possibleStarts: List<String>
        val rightSemester: String

        if (term == "1") {
            possibleStarts = (1..30 step 7).map { "$yearStart-08-${it.toString().padStart(2, '0')}" } +
                    (1..30 step 7).map { "$yearStart-09-${it.toString().padStart(2, '0')}" }
            rightSemester = "第一学期"
        } else {
            possibleStarts = (1..28 step 7).map { "$yearEnd-02-${it.toString().padStart(2, '0')}" } +
                    (1..30 step 7).map { "$yearEnd-03-${it.toString().padStart(2, '0')}" }
            rightSemester = "第二学期"
        }

        val validDates = possibleStarts.filter { dateStr ->
            try { LocalDate.parse(dateStr); true } catch (_: Exception) { false }
        }

        val url = URLBuilder("https://ywtb.xjtu.edu.cn/portal-api/v1/calendar/share/schedule/getWeekOfTeaching").apply {
            parameters.append("today", validDates.joinToString(","))
            parameters.append("random_number", Random.nextInt(100, 999).toString())
        }.buildString()

        val responseBody = baseGet(url)
        val json = responseBody.safeParseJsonObject()
        val dataObj = json["data"]!!.jsonObject["data"]!!.jsonObject
        val dateArray = dataObj["date"]!!.jsonArray
        val semesterAliList = dataObj["semesterAlilist"]!!.jsonArray
        val semesterList = dataObj["semesterlist"]!!.jsonArray

        for (i in 0 until dateArray.size) {
            val weekStr = dateArray[i].jsonPrimitive.content
            val semesterName = semesterAliList[i].jsonPrimitive.content
            val semesterId = semesterList[i].jsonPrimitive.content
            val dateStr = validDates[i]

            if (semesterId == "$yearStart-$yearEnd" && semesterName == rightSemester && weekStr == "1") {
                val dateObj = LocalDate.parse(dateStr)
                // 回退到周一
                val dayOfWeekValue = dateObj.dayOfWeek.ordinal // Monday=0
                val startOfTerm = LocalDate.fromEpochDays(dateObj.toEpochDays() - dayOfWeekValue)
                return startOfTerm.toString()
            }
        }

        throw RuntimeException("无法确定学期开始时间")
    }
}
