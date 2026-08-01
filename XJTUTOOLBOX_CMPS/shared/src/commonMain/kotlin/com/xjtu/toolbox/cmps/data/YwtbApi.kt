package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.time.Clock

class YwtbApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun userInfo(ticket: AuthTicket): YwtbUserInfo {
        val root = Json.parseToJsonElement(
            client.get("https://authx-service.xjtu.edu.cn/personal/api/v1/personal/me/user") {
                header("x-id-token", ticket.cookies["x_id_token"].orEmpty())
                header("x-device-info", "PC")
                header("x-terminal-info", "PC")
                header("Referer", "$baseUrl/main.html")
            }.bodyAsText()
        ).jsonObject
        val data = root["data"]?.jsonObject ?: error(root["message"]?.jsonPrimitive?.content ?: "一网通办用户信息为空")
        val attributes = data["attributes"]?.jsonObject
        return YwtbUserInfo(
            userName = attributes?.get("userName")?.jsonPrimitive?.content ?: data["username"]?.jsonPrimitive?.content.orEmpty(),
            userUid = attributes?.get("userUid")?.jsonPrimitive?.content.orEmpty(),
            identityTypeName = attributes?.get("identityTypeName")?.jsonPrimitive?.content.orEmpty(),
            organizationName = attributes?.get("organizationName")?.jsonPrimitive?.content.orEmpty(),
        )
    }

    suspend fun currentTeachingWeek(): TeachingWeekInfo? {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val url = URLBuilder("$baseUrl/portal-api/v1/calendar/share/schedule/getWeekOfTeaching").apply {
            parameters.append("today", today)
            parameters.append("random_number", Random.nextInt(100, 999).toString())
        }.buildString()
        val root = Json.parseToJsonElement(
            client.get(url) {
                header("x-device-info", "PC")
                header("x-terminal-info", "PC")
                header("Referer", "$baseUrl/main.html")
            }.bodyAsText()
        ).jsonObject
        val data = root["data"]?.jsonObject?.get("data")?.jsonObject ?: return null
        val week = data["date"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        if (week <= 0) return null
        val semesterName = data["semesterAlilist"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        val semesterId = data["semesterlist"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        return TeachingWeekInfo(
            week = week,
            semesterName = semesterName,
            semesterId = semesterId,
            startOfTerm = "",
        )
    }

    companion object {
        private const val baseUrl = "https://ywtb.xjtu.edu.cn"
    }
}
