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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LmsApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun courses(ticket: AuthTicket): List<LmsCourseSummary> {
        val root = postJson(ticket, "$baseUrl/api/my-courses")
        val courses = root["courses"] as? JsonArray ?: return emptyList()
        return courses.mapNotNull { (it as? JsonObject)?.toCourseSummary() }
    }

    suspend fun dashboardCourses(ticket: AuthTicket): List<LmsCourse> =
        courses(ticket).map { course ->
            LmsCourse(
                title = course.name,
                teacher = course.instructorNames,
                unread = 0,
                nextTask = course.semesterLabel,
            )
        }

    suspend fun activities(ticket: AuthTicket, courseId: Int? = null): List<LmsActivity> {
        val targetCourseId = courseId ?: courses(ticket).firstOrNull()?.id ?: return emptyList()
        val root = getJson(ticket, "$baseUrl/api/courses/$targetCourseId/activities")
        val activities = root["activities"] as? JsonArray ?: return emptyList()
        return activities.mapNotNull { (it as? JsonObject)?.toActivityBrief() }
    }

    suspend fun activityDetail(ticket: AuthTicket, activityId: Int): LmsActivity {
        val root = getJson(ticket, "$baseUrl/api/activities/$activityId")
        return root.toActivityDetail()
    }

    private suspend fun getJson(ticket: AuthTicket, url: String): JsonObject {
        val text = client.get(url) {
            addLmsHeaders(ticket)
        }.bodyAsText()
        if (isAuthFailure(text)) error("思源学堂登录态已失效")
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun postJson(ticket: AuthTicket, url: String): JsonObject {
        val text = client.post(url) {
            addLmsHeaders(ticket)
            contentType(ContentType.Application.Json)
            setBody("")
        }.bodyAsText()
        if (isAuthFailure(text)) error("思源学堂登录态已失效")
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addLmsHeaders(ticket: AuthTicket) {
        header(HttpHeaders.Cookie, ticket.cookies.toCookieHeader())
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.UserAgent, browserUa)
        header("Referer", "$baseUrl/user/courses")
    }

    private fun JsonObject.toCourseSummary(): LmsCourseSummary {
        val department = this["department"] as? JsonObject
        val attributes = this["course_attributes"] as? JsonObject
        return LmsCourseSummary(
            id = int("id"),
            name = string("name"),
            courseCode = string("course_code"),
            credit = string("credit"),
            compulsory = boolean("compulsory"),
            startDate = stringOrNull("start_date"),
            endDate = stringOrNull("end_date"),
            department = LmsDepartment(
                id = department?.int("id") ?: 0,
                name = department?.string("name").orEmpty(),
                code = department?.string("code").orEmpty(),
            ),
            instructors = array("instructors").mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                LmsInstructor(id = obj.int("id"), name = obj.string("name"))
            },
            published = attributes?.boolean("published") ?: false,
            studentCount = attributes?.int("student_count") ?: 0,
        )
    }

    private fun JsonObject.toActivityBrief(): LmsActivity =
        LmsActivity(
            id = int("id"),
            courseId = int("course_id"),
            type = lmsActivityType(string("type")),
            title = string("title"),
            startTime = stringOrNull("start_time"),
            endTime = stringOrNull("end_time"),
            published = boolean("published"),
        )

    private fun JsonObject.toActivityDetail(): LmsActivity {
        val common = toActivityBrief()
        val uploads = array("uploads").mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            obj.toUpload()
        }
        val data = this["data"] as? JsonObject
        val external = data?.get("external_live_detail") as? JsonObject
        val replayVideos = external?.array("replay_videos")?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            ReplayVideo(
                cameraId = obj.int("camera_id"),
                cameraType = obj.string("camera_type").ifBlank { obj.string("label") },
                url = obj.string("url").ifBlank { obj.string("download_url") },
                mute = obj.int("mute"),
            )
        }.orEmpty()
        return common.copy(
            uploads = uploads,
            submission = LmsSubmission(status = string("status"), score = string("score")),
            replayCode = stringOrNull("replay_code") ?: external?.stringOrNull("replay_id"),
            liveRoomName = (external?.get("room") as? JsonObject)?.stringOrNull("room_name"),
            liveStatus = external?.stringOrNull("status"),
            replayVideos = replayVideos,
        )
    }

    private fun JsonObject.toUpload(): LmsUpload {
        val uploadId = int("id")
        val referenceId = int("reference_id")
        return LmsUpload(
            id = uploadId,
            name = string("name"),
            type = string("type"),
            size = int("size"),
            downloadUrl = if (uploadId > 0) "$baseUrl/api/uploads/$uploadId/blob" else "",
            previewUrl = if (referenceId > 0) "$baseUrl/api/uploads/reference/document/$referenceId/url" else "",
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

    private fun JsonObject.array(key: String): List<JsonElement> =
        (this[key] as? JsonArray)?.jsonArray.orEmpty()

    private fun Map<String, String>.toCookieHeader(): String =
        entries.joinToString("; ") { (key, value) -> "$key=$value" }

    private fun lmsActivityType(value: String): LmsActivityType =
        when (value) {
            LmsActivityType.Homework.value -> LmsActivityType.Homework
            LmsActivityType.Material.value -> LmsActivityType.Material
            LmsActivityType.Lesson.value -> LmsActivityType.Lesson
            LmsActivityType.LectureLive.value -> LmsActivityType.LectureLive
            else -> LmsActivityType.Unknown
        }

    private fun isAuthFailure(text: String): Boolean =
        text.contains("login.xjtu.edu.cn/cas/login", ignoreCase = true) ||
            text.contains("name=\"execution\"", ignoreCase = true) ||
            text.contains("统一身份认证", ignoreCase = true)

    companion object {
        const val baseUrl = "https://lms.xjtu.edu.cn"
        private const val browserUa =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    }
}
