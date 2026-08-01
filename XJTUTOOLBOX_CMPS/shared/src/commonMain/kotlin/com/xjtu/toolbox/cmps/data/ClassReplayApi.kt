package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClassReplayApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun courses(ticket: AuthTicket, page: Int = 1, pageSize: Int = 50, keyword: String = ""): List<ClassReplayCourse> {
        val body = """
            {
              "fields":"id,name,course_code,department(id,name),display_name,start_date,end_date,is_started,is_closed,instructors(id,name)",
              "page":$page,
              "page_size":$pageSize,
              "conditions":{"keyword":"${keyword.escapeJson()}","classify_type":"recently_started","display_studio_list":false},
              "showScorePassedStatus":false
            }
        """.trimIndent()
        val root = postJson(ticket, "$baseUrl/api/my-courses", body)
        val array = root["courses"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.toCourse() }
    }

    suspend fun liveActivities(ticket: AuthTicket, courseId: Int? = null): List<LiveActivity> {
        val targetCourseId = courseId ?: courses(ticket).firstOrNull()?.id ?: return emptyList()
        val root = getJson(
            ticket,
            "$baseUrl/api/courses/$targetCourseId/live-activities?status=&types[]=tencent_meeting&types[]=lecture_live&types[]=third_party_live&page=1&page_size=50",
        )
        val array = root["items"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.toLiveActivity(targetCourseId) }
    }

    suspend fun replayDetail(ticket: AuthTicket, activityId: Int): ReplayDetail? {
        val root = getJson(ticket, "$baseUrl/api/activities/$activityId")
        val data = root["data"] as? JsonObject ?: return null
        val liveDetail = data["external_live_detail"] as? JsonObject ?: return null
        val videos = (liveDetail["replay_videos"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            ReplayVideo(
                cameraId = obj.int("camera_id"),
                cameraType = obj.string("camera_type"),
                url = obj.string("url"),
                mute = obj.int("mute"),
            )
        }
        val room = liveDetail["room"] as? JsonObject
        val instructors = (liveDetail["instructor_names"] as? JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
        return ReplayDetail(
            activityId = activityId,
            title = root.string("title"),
            startTime = liveDetail.string("start_time"),
            endTime = liveDetail.string("end_time"),
            roomName = room?.stringOrNull("room_name"),
            instructorNames = instructors,
            replayVideos = videos,
        )
    }

    suspend fun replayDetails(ticket: AuthTicket): List<ReplayDetail> {
        val activities = liveActivities(ticket)
        return activities.mapNotNull { replayDetail(ticket, it.id) }
    }

    private suspend fun getJson(ticket: AuthTicket, url: String): JsonObject {
        val text = client.get(url) {
            addClassHeaders(ticket)
        }.bodyAsText()
        if (isAuthFailure(text)) error("课堂回放登录态已失效")
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun postJson(ticket: AuthTicket, url: String, body: String): JsonObject {
        val text = client.post(url) {
            addClassHeaders(ticket)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
        if (isAuthFailure(text)) error("课堂回放登录态已失效")
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addClassHeaders(ticket: AuthTicket) {
        header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.UserAgent, browserUa)
        header("Referer", "$baseUrl/user/index")
    }

    private fun JsonObject.toCourse(): ClassReplayCourse {
        val department = this["department"] as? JsonObject
        val instructors = (this["instructors"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.stringOrNull("name")
        }
        return ClassReplayCourse(
            id = int("id"),
            name = string("name"),
            displayName = string("display_name").ifBlank { string("name") },
            courseCode = string("course_code"),
            department = department?.string("name").orEmpty(),
            instructors = instructors,
            isStarted = boolean("is_started"),
            isClosed = boolean("is_closed"),
            startDate = stringOrNull("start_date"),
            endDate = stringOrNull("end_date"),
        )
    }

    private fun JsonObject.toLiveActivity(defaultCourseId: Int): LiveActivity {
        val data = this["data"] as? JsonObject
        return LiveActivity(
            id = int("id"),
            title = string("title"),
            type = string("type"),
            startTime = string("start_time"),
            endTime = string("end_time"),
            courseId = int("course_id").takeIf { it > 0 } ?: defaultCourseId,
            isClosed = boolean("is_closed"),
            externalLiveId = data?.stringOrNull("external_live_id"),
        )
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.boolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: false

    private fun Map<String, String>.toCookieHeader(): String =
        entries.joinToString("; ") { (key, value) -> "$key=$value" }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun isAuthFailure(text: String): Boolean =
        text.contains("login.xjtu.edu.cn/cas/login", ignoreCase = true) ||
            text.contains("name=\"execution\"", ignoreCase = true) ||
            text.contains("统一身份认证", ignoreCase = true)

    companion object {
        const val baseUrl = "https://class.xjtu.edu.cn"
        private const val browserUa =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    }
}
