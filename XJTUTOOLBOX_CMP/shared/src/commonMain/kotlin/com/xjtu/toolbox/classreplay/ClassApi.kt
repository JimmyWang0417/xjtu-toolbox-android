package com.xjtu.toolbox.classreplay

import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TAG = "ClassApi"

data class Course(
    val id: Int, val name: String, val displayName: String,
    val courseCode: String, val department: String,
    val instructors: List<String>, val isStarted: Boolean, val isClosed: Boolean,
    val startDate: String?, val endDate: String?, val semesterId: Int = 0
) {
    val semesterLabel: String get() {
        val sd = startDate ?: return "未知"
        return try {
            val parts = sd.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            if (month >= 8) "${year}-${year + 1} 秋" else "${year - 1}-${year} 春"
        } catch (_: Exception) { "未知" }
    }
}

data class LiveActivity(
    val id: Int, val title: String, val type: String,
    val startTime: String, val endTime: String,
    val courseId: Int, val isClosed: Boolean, val externalLiveId: String?
)

data class ReplayVideo(
    val cameraId: Int, val cameraType: String, val url: String, val mute: Int
) {
    val label: String get() = when (cameraType) {
        "instructor" -> "教师直播"; "encoder" -> "电脑屏幕"; else -> cameraType
    }
}

data class ReplayDetail(
    val activityId: Int, val title: String,
    val startTime: String, val endTime: String,
    val roomName: String?, val instructorNames: List<String>,
    val replayVideos: List<ReplayVideo>
)

suspend fun fetchCourses(
    login: ClassLogin, page: Int = 1, pageSize: Int = 50, keyword: String = ""
): Pair<List<Course>, Int> {
    val body = buildJsonObject {
        put("fields", "id,name,course_code,department(id,name),display_name,start_date,end_date,is_started,is_closed,instructors(id,name)")
        put("page", page)
        put("page_size", pageSize)
        putJsonObject("conditions") {
            put("keyword", keyword)
            put("classify_type", "recently_started")
            put("display_studio_list", false)
        }
        put("showScorePassedStatus", false)
    }.toString()

    val responseBody = login.executeWithReAuth {
        val resp = login.client.post("${ClassLogin.BASE_URL}/api/my-courses") {
            contentType(ContentType.Application.Json)
            header("Referer", "${ClassLogin.BASE_URL}/user/courses")
            header("Accept", "application/json, text/plain, */*")
            setBody(body)
        }
        resp.status.value to resp.bodyAsText()
    }

    val json = responseBody.safeParseJsonObject()
    val total = json["total"]?.jsonPrimitive?.int ?: 0
    val courses = mutableListOf<Course>()

    json["courses"]?.jsonArray?.forEach { elem ->
        try {
            val c = elem.jsonObject
            val deptElem = c["department"]
            val deptName = if (deptElem is JsonObject) deptElem["name"]?.jsonPrimitive?.content ?: "" else ""
            val instrList = c["instructors"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()

            courses.add(Course(
                id = c["id"]?.jsonPrimitive?.int ?: 0,
                name = c["name"]?.jsonPrimitive?.content ?: "",
                displayName = c["display_name"]?.jsonPrimitive?.content
                    ?: c["name"]?.jsonPrimitive?.content ?: "",
                courseCode = c["course_code"]?.jsonPrimitive?.content ?: "",
                department = deptName,
                instructors = instrList,
                isStarted = c["is_started"]?.jsonPrimitive?.boolean ?: false,
                isClosed = c["is_closed"]?.jsonPrimitive?.boolean ?: false,
                startDate = c["start_date"]?.jsonPrimitive?.content,
                endDate = c["end_date"]?.jsonPrimitive?.content,
                semesterId = c["semester_id"]?.jsonPrimitive?.int ?: 0
            ))
        } catch (e: Exception) {
            Logger.w(TAG, "parseCourse error: ${e.message}")
        }
    }

    Logger.d(TAG, "fetchCourses: ${courses.size} courses, total=$total")
    return courses to total
}

suspend fun fetchLiveActivities(
    login: ClassLogin, courseId: Int, page: Int = 1, pageSize: Int = 20
): Pair<List<LiveActivity>, Int> {
    val url = "${ClassLogin.BASE_URL}/api/courses/$courseId/live-activities?" +
        "status=&types[]=tencent_meeting&types[]=lecture_live&types[]=third_party_live&" +
        "page=$page&page_size=$pageSize"

    val responseBody = login.executeWithReAuth {
        val resp = login.client.get(url) {
            header("Referer", "${ClassLogin.BASE_URL}/user/courses")
            header("Accept", "application/json, text/plain, */*")
        }
        resp.status.value to resp.bodyAsText()
    }

    val json = responseBody.safeParseJsonObject()
    val total = json["total"]?.jsonPrimitive?.int ?: 0
    val activities = mutableListOf<LiveActivity>()

    json["items"]?.jsonArray?.forEach { elem ->
        try {
            val a = elem.jsonObject
            val data = a["data"]?.jsonObject
            activities.add(LiveActivity(
                id = a["id"]!!.jsonPrimitive.int,
                title = a["title"]?.jsonPrimitive?.content ?: "",
                type = a["type"]?.jsonPrimitive?.content ?: "",
                startTime = a["start_time"]?.jsonPrimitive?.content ?: "",
                endTime = a["end_time"]?.jsonPrimitive?.content ?: "",
                courseId = a["course_id"]?.jsonPrimitive?.int ?: courseId,
                isClosed = a["is_closed"]?.jsonPrimitive?.boolean ?: false,
                externalLiveId = data?.get("external_live_id")?.jsonPrimitive?.content
            ))
        } catch (e: Exception) {
            Logger.w(TAG, "parseLiveActivity error: ${e.message}")
        }
    }

    Logger.d(TAG, "fetchLiveActivities: courseId=$courseId, ${activities.size} activities, total=$total")
    return activities to total
}

suspend fun fetchReplayDetail(login: ClassLogin, activityId: Int): ReplayDetail? {
    val responseBody = login.executeWithReAuth {
        val resp = login.client.get("${ClassLogin.BASE_URL}/api/activities/$activityId") {
            header("Referer", "${ClassLogin.BASE_URL}/user/courses")
            header("Accept", "application/json, text/plain, */*")
        }
        resp.status.value to resp.bodyAsText()
    }

    return try {
        val json = responseBody.safeParseJsonObject()
        val data = json["data"]?.jsonObject
        val liveDetail = data?.get("external_live_detail")?.jsonObject ?: return null

        val videos = liveDetail["replay_videos"]?.jsonArray?.mapNotNull { v ->
            try {
                val vo = v.jsonObject
                ReplayVideo(
                    cameraId = vo["camera_id"]!!.jsonPrimitive.int,
                    cameraType = vo["camera_type"]?.jsonPrimitive?.content ?: "",
                    url = vo["url"]?.jsonPrimitive?.content ?: "",
                    mute = vo["mute"]?.jsonPrimitive?.int ?: 0
                )
            } catch (e: Exception) {
                Logger.w(TAG, "parseReplayVideo error: ${e.message}")
                null
            }
        } ?: emptyList()

        val room = liveDetail["room"]?.jsonObject
        val roomName = room?.get("room_name")?.jsonPrimitive?.content
        val instructorNames = liveDetail["instructor_names"]?.jsonArray
            ?.map { it.jsonPrimitive.content } ?: emptyList()

        ReplayDetail(
            activityId = activityId,
            title = json["title"]?.jsonPrimitive?.content ?: "",
            startTime = liveDetail["start_time"]?.jsonPrimitive?.content ?: "",
            endTime = liveDetail["end_time"]?.jsonPrimitive?.content ?: "",
            roomName = roomName,
            instructorNames = instructorNames,
            replayVideos = videos
        ).also { Logger.d(TAG, "fetchReplayDetail: activityId=$activityId, ${it.replayVideos.size} videos") }
    } catch (e: Exception) {
        Logger.e(TAG, "fetchReplayDetail error", e)
        null
    }
}

suspend fun resolveVideoUrl(login: ClassLogin, previewUrl: String): String? {
    return try {
        val resp = login.client.get(previewUrl) {
            header("Accept", "video/webm,video/ogg,video/*;q=0.9,*/*;q=0.5")
            header("Origin", "https://class.xjtu.edu.cn")
            header("Referer", "https://class.xjtu.edu.cn/")
        }
        val location = resp.headers["Location"]
        val code = resp.status.value
        Logger.d(TAG, "resolveVideoUrl: $code, location=${location?.take(100)}")
        if (code == 302 && location != null) location else null
    } catch (e: Exception) {
        Logger.e(TAG, "resolveVideoUrl error", e)
        null
    }
}
