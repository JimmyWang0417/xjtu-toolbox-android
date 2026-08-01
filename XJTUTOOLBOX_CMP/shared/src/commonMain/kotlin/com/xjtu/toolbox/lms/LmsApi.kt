package com.xjtu.toolbox.lms

import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "LmsApi"

// ════════════════════════════════════════
//  kotlinx-serialization 安全扩展
// ════════════════════════════════════════

private fun JsonElement?.kSafeString(): String? =
    if (this == null || this is JsonNull) null
    else try { this.jsonPrimitive.content } catch (_: Exception) { null }

private fun JsonElement?.kSafeInt(default: Int = 0): Int =
    if (this == null || this is JsonNull) default
    else try { this.jsonPrimitive.content.toInt() } catch (_: Exception) { default }

private fun JsonElement?.kSafeBoolean(default: Boolean = false): Boolean =
    if (this == null || this is JsonNull) default
    else try { this.jsonPrimitive.content.toBooleanStrictOrNull() ?: default } catch (_: Exception) { default }

private fun JsonElement?.kSafeArray(): JsonArray =
    if (this == null || this is JsonNull) JsonArray(emptyList())
    else try { this.jsonArray } catch (_: Exception) { JsonArray(emptyList()) }

private fun JsonElement?.kSafeObject(): JsonObject? =
    if (this == null || this is JsonNull) null
    else try { this.jsonObject } catch (_: Exception) { null }

// ════════════════════════════════════════
//  LmsApi — 思源学堂 API 封装
// ════════════════════════════════════════

class LmsApi(private val login: LmsLogin) {

    private val baseUrl = LmsLogin.BASE_URL
    private val rmsBaseUrl = LmsLogin.RMS_BASE_URL

    // 缓存
    private var cachedUserInfo: LmsUserInfo? = null
    private val replayVideoCache = mutableMapOf<String, List<LmsReplayVideo>>()
    private val playerTokenCache = mutableMapOf<Int, String>()
    private val rmsTokenCache = mutableMapOf<Int, String>()

    // ── 公有接口 ──────────────────────────

    /**
     * 获取当前登录用户基本信息
     * 返回值来自 /user/index 页面中的 globalData.user
     */
    suspend fun getUserInfo(refresh: Boolean = false): LmsUserInfo {
        if (cachedUserInfo != null && !refresh) return cachedUserInfo!!

        val page = getIndexPage()
        val userBlock = extractJsBlock(page, "user", "dept")
        val deptBlock = extractJsBlock(page, "dept", "locale")

        val info = LmsUserInfo(
            id = extractJsKeyValue(userBlock, "id")?.toIntOrNull() ?: 0,
            name = extractJsKeyValue(userBlock, "name") ?: "",
            userNo = extractJsKeyValue(userBlock, "userNo") ?: "",
            orgId = extractJsKeyValue(userBlock, "orgId")?.toIntOrNull() ?: 0,
            mobile = extractJsKeyValue(userBlock, "mobile") ?: "",
            orgName = extractJsKeyValue(userBlock, "orgName") ?: "",
            orgCode = extractJsKeyValue(userBlock, "orgCode") ?: "",
            role = extractJsKeyValue(userBlock, "role") ?: "",
            hasAiAbility = extractJsKeyValue(userBlock, "hasAiAbility") == "true",
            dept = LmsDepartment(
                id = extractJsKeyValue(deptBlock, "id")?.toIntOrNull() ?: 0,
                name = extractJsKeyValue(deptBlock, "name") ?: "",
                code = extractJsKeyValue(deptBlock, "code") ?: ""
            )
        )
        cachedUserInfo = info
        return info
    }

    /**
     * 获取我的课程列表
     */
    suspend fun getMyCourses(): List<LmsCourseSummary> {
        val data = postJson("$baseUrl/api/my-courses")
        val courses = data?.get("courses")?.kSafeArray() ?: return emptyList()
        return courses.mapNotNull { elem ->
            try {
                extractCourseSummary(elem.jsonObject)
            } catch (e: Exception) {
                Logger.w(TAG, "getMyCourses: skip bad course: ${e.message}")
                null
            }
        }
    }

    /**
     * 获取课程详细信息
     */
    suspend fun getCourseDetail(courseId: Int): LmsCourseDetail {
        val data = getJson("$baseUrl/api/courses/$courseId")
            ?: throw RuntimeException("获取课程详情失败")
        val summary = extractCourseSummary(data)
        return LmsCourseDetail(
            summary = summary,
            subjectCode = data["subject_code"].kSafeString() ?: "",
            displayName = data["display_name"].kSafeString() ?: "",
            publicScope = data["public_scope"].kSafeString() ?: "",
            cover = data["cover"].kSafeString() ?: ""
        )
    }

    /**
     * 获取课程活动列表
     */
    suspend fun getCourseActivities(courseId: Int): List<LmsActivity> {
        val data = getJson("$baseUrl/api/courses/$courseId/activities")
            ?: return emptyList()
        val activities = data["activities"]?.kSafeArray() ?: return emptyList()
        return activities.mapNotNull { elem ->
            try {
                extractActivityBrief(elem.jsonObject)
            } catch (e: Exception) {
                Logger.w(TAG, "getCourseActivities: skip bad activity: ${e.message}")
                null
            }
        }
    }

    /**
     * 获取活动详细信息
     * - homework 类型自动注入 submissionList
     * - lesson 类型自动注入 replayVideos + replayDownloadUrls
     */
    suspend fun getActivityDetail(activityId: Int): LmsActivity {
        val data = getJson("$baseUrl/api/activities/$activityId")
            ?: throw RuntimeException("获取活动详情失败")
        var detail = extractActivityDetail(data)

        // homework → 自动注入提交列表
        if (detail.type == LmsActivityType.HOMEWORK) {
            try {
                val submissionList = getSubmissionList(activityId, activityDetail = data)
                detail = detail.copy(submissionList = submissionList)
            } catch (e: Exception) {
                Logger.w(TAG, "getActivityDetail: failed to get submissions for $activityId: ${e.message}")
            }
        }

        return detail
    }

    // ── 内部方法 ──────────────────────────

    /** 下载任意 URL 的字节数组（带认证），用于附件预览 */
    suspend fun downloadBytes(url: String): ByteArray? {
        return try {
            login.executeWithReAuth { client ->
                val resp = client.get(url) {
                    header("Referer", "$baseUrl/user/courses")
                    header("Accept", "application/json, text/plain, */*")
                }
                resp.bodyAsText().encodeToByteArray()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "downloadBytes failed: $url", e)
            null
        }
    }

    private suspend fun getIndexPage(): String {
        return login.executeWithReAuth { client ->
            client.get("$baseUrl/user/index") {
                header("Referer", "$baseUrl/user/courses")
                header("Accept", "application/json, text/plain, */*")
            }.bodyAsText()
        }
    }

    private suspend fun getJson(url: String, headers: Map<String, String>? = null): JsonObject? {
        return try {
            login.executeWithReAuth { client ->
                val body = client.get(url) {
                    header("Referer", "$baseUrl/user/courses")
                    header("Accept", "application/json, text/plain, */*")
                    headers?.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()
                body.safeParseJsonObject()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getJson: failed for $url: ${e.message}")
            null
        }
    }

    private suspend fun postJson(url: String): JsonObject? {
        return try {
            login.executeWithReAuth { client ->
                val body = client.post(url) {
                    header("Referer", "$baseUrl/user/courses")
                    header("Accept", "application/json, text/plain, */*")
                    contentType(ContentType.Application.Json)
                    setBody("")
                }.bodyAsText()
                body.safeParseJsonObject()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "postJson: failed for $url: ${e.message}")
            null
        }
    }

    // ── 作业提交列表 ──────────────────────

    private suspend fun getSubmissionList(
        activityId: Int,
        userId: Int? = null,
        groupId: Int? = null,
        submitByGroup: Boolean? = null,
        activityDetail: JsonObject? = null
    ): LmsSubmissionListResponse {
        val detail = activityDetail ?: getJson("$baseUrl/api/activities/$activityId")
            ?: throw RuntimeException("获取活动详情失败")

        val isByGroup = submitByGroup ?: detail["submit_by_group"].kSafeBoolean()

        val url = if (isByGroup) {
            val gid = groupId ?: detail["group_id"].kSafeInt()
            if (gid == 0) throw RuntimeException("小组作业但找不到 group_id")
            "$baseUrl/api/activities/$activityId/groups/$gid/submission_list"
        } else {
            val uid = userId ?: getUserInfo().id
            if (uid == 0) throw RuntimeException("无法获取 user_id")
            "$baseUrl/api/activities/$activityId/students/$uid/submission_list"
        }

        val data = getJson(url) ?: throw RuntimeException("获取提交列表失败")
        return extractSubmissionList(data)
    }

    // ── 课堂回放视频 ──────────────────────

    private suspend fun getLessonPlayerUrl(lessonActivityId: Int): String {
        val data = getJson("$baseUrl/api/lessons/$lessonActivityId/player-url?from_page=course")
            ?: throw RuntimeException("获取播放器 URL 失败")
        return data["url"].kSafeString()
            ?.takeIf { it.isNotEmpty() }
            ?: throw RuntimeException("播放器 URL 为空")
    }

    private suspend fun getLessonPlayerToken(lessonActivityId: Int): String {
        playerTokenCache[lessonActivityId]?.let { return it }
        val playerUrl = getLessonPlayerUrl(lessonActivityId)
        val token = try {
            val queryStr = playerUrl.substringAfter("?", "")
            queryStr.split("&")
                .associate { it.split("=", limit = 2).let { parts -> parts[0] to (parts.getOrNull(1) ?: "") } }
                .get("token")
        } catch (_: Exception) { null }
            ?: throw RuntimeException("播放器 URL 中找不到 token")
        playerTokenCache[lessonActivityId] = token
        return token
    }

    private suspend fun exchangeEmbedToken(playerToken: String): String {
        val data = getJson("$rmsBaseUrl/api/v1/auth/embed-token?token=$playerToken")
            ?: throw RuntimeException("embed-token 交换失败")

        data.kSafeObject()?.let { obj ->
            obj["error"].kSafeObject()?.let { error ->
                val code = error["code"].kSafeInt()
                if (code != 0) {
                    throw RuntimeException("embed-token 交换失败: code=$code, message=${error["message"].kSafeString()}")
                }
            }
        }

        return data["data"].kSafeString()
            ?.takeIf { it.isNotEmpty() }
            ?: throw RuntimeException("embed-token 返回空 rms_token")
    }

    private suspend fun getLessonRmsToken(lessonActivityId: Int): String {
        rmsTokenCache[lessonActivityId]?.let { return it }
        val playerToken = getLessonPlayerToken(lessonActivityId)
        val rmsToken = exchangeEmbedToken(playerToken)
        rmsTokenCache[lessonActivityId] = rmsToken
        return rmsToken
    }

    private suspend fun getReplayVideos(
        replayCode: String,
        lessonActivityId: Int? = null
    ): List<LmsReplayVideo> {
        replayVideoCache[replayCode]?.let { return it }

        val headers = mutableMapOf<String, String>()
        if (lessonActivityId != null) {
            try {
                val rmsToken = getLessonRmsToken(lessonActivityId)
                headers["Authorization"] = "Bearer $rmsToken"
            } catch (e: Exception) {
                Logger.w(TAG, "getReplayVideos: failed to get RMS token: ${e.message}")
            }
        }

        val data = getJson("$rmsBaseUrl/api/embed/lesson-activities/captures/$replayCode", headers)
            ?: return emptyList()

        // 检查错误
        data["error"].kSafeObject()?.let { error ->
            val code = error["code"].kSafeInt()
            if (code != 0) {
                Logger.w(TAG, "getReplayVideos: error code=$code, message=${error["message"].kSafeString()}")
                return emptyList()
            }
        }

        var videosArray = data["lesson_videos"].kSafeArray()
        if (videosArray.isEmpty()) {
            // 尝试嵌套结构
            data["data"].kSafeObject()?.let { inner ->
                videosArray = inner["lesson_videos"].kSafeArray()
            }
        }

        val videos = videosArray.mapNotNull { elem ->
            try {
                val obj = elem.jsonObject
                LmsReplayVideo(
                    id = obj["id"].kSafeInt(),
                    label = obj["label"].kSafeString() ?: "",
                    mute = obj["mute"].kSafeBoolean(),
                    isBestAudio = obj["is_best_audio"].kSafeBoolean(),
                    playType = obj["play_type"].kSafeString() ?: "",
                    downloadUrl = obj["download_url"].kSafeString() ?: "",
                    fileKey = obj["file_key"].kSafeString() ?: "",
                    size = obj["size"].kSafeInt()
                )
            } catch (e: Exception) {
                Logger.w(TAG, "getReplayVideos: skip bad video: ${e.message}")
                null
            }
        }
        replayVideoCache[replayCode] = videos
        return videos
    }

    // ── 数据提取 ──────────────────────────

    private fun extractCourseSummary(obj: JsonObject): LmsCourseSummary {
        val instructors = obj["instructors"].kSafeArray().mapNotNull { elem ->
            elem.kSafeObject()?.let { LmsInstructor(it["id"].kSafeInt(), it["name"].kSafeString() ?: "") }
        }
        val ay = obj["academic_year"].kSafeObject()
        val sem = obj["semester"].kSafeObject()
        val dept = obj["department"].kSafeObject()
        val attrs = obj["course_attributes"].kSafeObject()

        return LmsCourseSummary(
            id = obj["id"].kSafeInt(),
            name = obj["name"].kSafeString() ?: "",
            courseCode = obj["course_code"].kSafeString() ?: "",
            courseType = obj["course_type"].kSafeInt(),
            credit = obj["credit"].kSafeString() ?: "",
            compulsory = obj["compulsory"].kSafeBoolean(),
            startDate = obj["start_date"].kSafeString(),
            endDate = obj["end_date"].kSafeString(),
            academicYear = LmsAcademicYear(
                id = ay?.get("id").kSafeInt(),
                code = ay?.get("code").kSafeString() ?: "",
                name = ay?.get("name").kSafeString() ?: "",
                sort = ay?.get("sort").kSafeInt()
            ),
            semester = LmsSemester(
                id = sem?.get("id").kSafeInt(),
                code = sem?.get("code").kSafeString() ?: "",
                name = sem?.get("name").kSafeString(),
                realName = sem?.get("real_name").kSafeString(),
                sort = sem?.get("sort").kSafeInt()
            ),
            department = LmsDepartment(
                id = dept?.get("id").kSafeInt(),
                name = dept?.get("name").kSafeString() ?: ""
            ),
            instructors = instructors,
            courseAttributes = LmsCourseAttributes(
                published = attrs?.get("published").kSafeBoolean() ?: false,
                studentCount = attrs?.get("student_count").kSafeInt() ?: 0,
                teachingClassName = attrs?.get("teaching_class_name").kSafeString() ?: ""
            )
        )
    }

    private fun extractActivityBrief(obj: JsonObject): LmsActivity {
        return LmsActivity(
            id = obj["id"].kSafeInt(),
            courseId = obj["course_id"].kSafeInt(),
            type = LmsActivityType.fromString(obj["type"].kSafeString() ?: ""),
            title = obj["title"].kSafeString() ?: "",
            moduleId = obj["module_id"]?.let { if (it is JsonNull) null else it.kSafeInt() },
            startTime = obj["start_time"].kSafeString(),
            endTime = obj["end_time"].kSafeString(),
            submitByGroup = obj["submit_by_group"].kSafeBoolean(),
            published = obj["published"].kSafeBoolean(),
            createdAt = obj["created_at"].kSafeString() ?: "",
            updatedAt = obj["updated_at"].kSafeString() ?: ""
        )
    }

    private fun extractUpload(obj: JsonObject): LmsUpload {
        val uploadId = obj["id"].kSafeInt()
        val refId = obj["reference_id"].kSafeInt()
        return LmsUpload(
            id = uploadId,
            name = obj["name"].kSafeString() ?: "",
            type = obj["type"].kSafeString() ?: "",
            size = obj["size"].kSafeInt(),
            referenceId = refId,
            status = obj["status"].kSafeString() ?: "",
            createdAt = obj["created_at"].kSafeString() ?: "",
            updatedAt = obj["updated_at"].kSafeString() ?: "",
            downloadUrl = if (uploadId > 0) "$baseUrl/api/uploads/$uploadId/blob" else "",
            previewUrl = if (refId > 0) "$baseUrl/api/uploads/reference/document/$refId/url" else ""
        )
    }

    private suspend fun extractActivityDetail(obj: JsonObject): LmsActivity {
        val typeStr = obj["type"].kSafeString() ?: ""
        val type = LmsActivityType.fromString(typeStr)
        val dataObj = obj["data"].kSafeObject()
        val lessonResource = obj["lesson_resource"].kSafeObject()
        val lessonProperties = lessonResource?.get("properties").kSafeObject()

        val uploads = obj["uploads"].kSafeArray().mapNotNull { elem ->
            try { extractUpload(elem.jsonObject) } catch (_: Exception) { null }
        }

        val common = LmsActivity(
            id = obj["id"].kSafeInt(),
            courseId = obj["course_id"].kSafeInt(),
            type = type,
            title = obj["title"].kSafeString() ?: "",
            moduleId = obj["module_id"]?.let { if (it is JsonNull) null else it.kSafeInt() },
            startTime = obj["start_time"].kSafeString(),
            endTime = obj["end_time"].kSafeString(),
            published = obj["published"].kSafeBoolean(),
            createdAt = obj["created_at"].kSafeString() ?: "",
            updatedAt = obj["updated_at"].kSafeString() ?: "",
            uploads = uploads
        )

        return when (type) {
            LmsActivityType.HOMEWORK -> common.copy(
                submitByGroup = obj["submit_by_group"].kSafeBoolean(),
                groupId = obj["group_id"]?.let { if (it is JsonNull) null else it.kSafeInt() },
                groupSetName = obj["group_set_name"].kSafeString(),
                userSubmitCount = obj["user_submit_count"].kSafeInt(),
                description = dataObj?.get("description").kSafeString()
            )

            LmsActivityType.MATERIAL -> common.copy(
                description = dataObj?.get("description").kSafeString()
            )

            LmsActivityType.LESSON -> {
                var replayCode: String? = obj["replay_code"].kSafeString()?.takeIf { it.isNotEmpty() }
                if (replayCode == null) {
                    replayCode = lessonProperties?.get("replay_code").kSafeString()?.takeIf { it.isNotEmpty() }
                }
                if (replayCode == null) {
                    replayCode = dataObj?.get("external_live_detail").kSafeObject()
                        ?.get("replay_id").kSafeString()?.takeIf { it.isNotEmpty() }
                }

                val lessonActivityId = obj["id"].kSafeInt()
                var replayVideos = emptyList<LmsReplayVideo>()
                var replayDownloadUrls = emptyList<String>()

                if (replayCode != null) {
                    try {
                        replayVideos = getReplayVideos(replayCode, lessonActivityId)
                        replayDownloadUrls = replayVideos.mapNotNull { it.downloadUrl.takeIf { url -> url.isNotEmpty() } }
                    } catch (e: Exception) {
                        Logger.w(TAG, "extractActivityDetail: failed to get replay videos for $lessonActivityId: ${e.message}")
                    }
                }

                common.copy(
                    replayCode = replayCode,
                    lessonStart = dataObj?.get("lesson_start").kSafeString(),
                    lessonEnd = dataObj?.get("lesson_end").kSafeString(),
                    replayVideos = replayVideos,
                    replayDownloadUrls = replayDownloadUrls,
                    replayVideoCount = replayVideos.size
                )
            }

            LmsActivityType.LECTURE_LIVE -> {
                val external = dataObj?.get("external_live_detail").kSafeObject()

                val roomObj = external?.get("room").kSafeObject()
                val roomName = roomObj?.get("room_name").kSafeString()
                val roomCode = roomObj?.get("room_code").kSafeString()

                val instructorNames = external?.get("instructor_names").kSafeArray()
                    ?.mapNotNull { it.kSafeString() } ?: emptyList()

                val streams = external?.get("streams").kSafeArray()?.mapNotNull { elem ->
                    try {
                        val s = elem.jsonObject
                        LmsLiveStream(
                            label = s["label"].kSafeString() ?: "",
                            src = s["src"].kSafeString()
                                ?: s["stream_url"].kSafeString() ?: "",
                            mute = s["mute"].kSafeBoolean() || s["muted"].kSafeBoolean(),
                            type = s["type"].kSafeString() ?: "application/x-mpegURL"
                        )
                    } catch (e: Exception) {
                        Logger.w(TAG, "extractActivityDetail: skip bad stream: ${e.message}")
                        null
                    }
                }?.filter { it.src.isNotEmpty() } ?: emptyList()

                val replayVideosArr = external?.get("replay_videos").kSafeArray()
                val liveReplayVideos = replayVideosArr?.mapNotNull { elem ->
                    try {
                        val v = elem.jsonObject
                        LmsReplayVideo(
                            id = v["id"].kSafeInt(),
                            label = v["label"].kSafeString()
                                ?: v["camera_type"].kSafeString() ?: "",
                            mute = v["mute"].kSafeBoolean(),
                            isBestAudio = v["is_best_audio"].kSafeBoolean(),
                            playType = v["play_type"].kSafeString() ?: "",
                            downloadUrl = v["download_url"].kSafeString()
                                ?: v["url"].kSafeString() ?: "",
                            fileKey = v["file_key"].kSafeString() ?: "",
                            size = v["size"].kSafeInt()
                        )
                    } catch (e: Exception) {
                        Logger.w(TAG, "extractActivityDetail: skip bad live replay video: ${e.message}")
                        null
                    }
                } ?: emptyList()

                val replayId = external?.get("replay_id")?.let {
                    if (it is JsonNull) null
                    else it.kSafeString()?.takeIf { s -> s.isNotEmpty() }
                        ?: it.kSafeInt().takeIf { i -> i > 0 }?.toString()
                }
                val finalReplayVideos = if (liveReplayVideos.isEmpty() && replayId != null) {
                    try {
                        getReplayVideos(replayId, common.id)
                    } catch (e: Exception) {
                        Logger.w(TAG, "extractActivityDetail: failed to get RMS replay videos for LECTURE_LIVE: ${e.message}")
                        emptyList()
                    }
                } else liveReplayVideos

                common.copy(
                    replayCode = replayId,
                    liveRoomName = roomName,
                    liveRoomCode = roomCode,
                    liveStatus = external?.get("status").kSafeString(),
                    liveInstructorNames = instructorNames,
                    liveStreams = streams,
                    liveReplayVideos = finalReplayVideos,
                    externalLiveId = external?.get("id").kSafeInt(),
                    viewLive = external?.get("view_live").kSafeBoolean(),
                    viewRecord = external?.get("view_record").kSafeBoolean()
                )
            }

            else -> common
        }
    }

    private fun extractSubmissionList(data: JsonObject): LmsSubmissionListResponse {
        val items = data["list"].kSafeArray().mapNotNull { elem ->
            try {
                val obj = elem.jsonObject
                val uploads = obj["uploads"].kSafeArray().mapNotNull { u ->
                    try { extractUpload(u.jsonObject) } catch (_: Exception) { null }
                }
                val createdBy = obj["created_by"].kSafeObject()
                val sc = obj["submission_correct"].kSafeObject()
                val scUploads = sc?.get("uploads").kSafeArray()?.mapNotNull { u ->
                    try { extractUpload(u.jsonObject) } catch (_: Exception) { null }
                } ?: emptyList()

                LmsSubmissionItem(
                    id = obj["id"].kSafeInt(),
                    activityId = obj["activity_id"].kSafeInt(),
                    studentId = obj["student_id"].kSafeInt(),
                    groupId = obj["group_id"].kSafeInt(),
                    canRetract = obj["can_retract"].kSafeBoolean(),
                    comment = obj["comment"].kSafeString() ?: "",
                    createdAt = obj["created_at"].kSafeString(),
                    createdBy = LmsSubmissionCreator(
                        id = createdBy?.get("id").kSafeInt() ?: 0,
                        name = createdBy?.get("name").kSafeString() ?: "",
                        userNo = (createdBy?.get("user_no").kSafeString() ?: createdBy?.get("userNo").kSafeString()) ?: ""
                    ),
                    instructorComment = obj["instructor_comment"].kSafeString() ?: "",
                    isLatestVersion = obj["is_latest_version"].kSafeBoolean(),
                    isResubmitted = obj["is_resubmitted"].kSafeBoolean(),
                    isRedo = obj["is_redo"].kSafeBoolean(),
                    mode = obj["mode"].kSafeString() ?: "",
                    status = obj["status"].kSafeString() ?: "",
                    score = obj["score"].kSafeString(),
                    scoreAt = obj["score_at"].kSafeString(),
                    submittedAt = obj["submitted_at"].kSafeString(),
                    submitByInstructor = obj["submit_by_instructor"].kSafeBoolean(),
                    submissionCorrect = LmsSubmissionCorrect(
                        id = sc?.get("id").kSafeInt() ?: 0,
                        comment = sc?.get("comment").kSafeString() ?: "",
                        instructorScore = sc?.get("instructor_score").kSafeString(),
                        score = sc?.get("score").kSafeString(),
                        updatedAt = sc?.get("updated_at").kSafeString() ?: "",
                        uploads = scUploads
                    ),
                    updatedAt = obj["updated_at"].kSafeString(),
                    content = obj["content"].kSafeString() ?: "",
                    uploads = uploads
                )
            } catch (e: Exception) {
                Logger.w(TAG, "extractSubmissionList: skip bad item: ${e.message}")
                null
            }
        }

        val topUploads = data["uploads"].kSafeArray().mapNotNull { u ->
            try { extractUpload(u.jsonObject) } catch (_: Exception) { null }
        }

        return LmsSubmissionListResponse(list = items, uploads = topUploads)
    }

    // ── JavaScript 解析工具 ──────────────

    /**
     * 从 HTML 页面中提取 globalData 的 JS 对象块
     */
    private fun extractJsBlock(page: String, key: String, nextKey: String): String {
        val pattern = Regex(
            """${Regex.escape(key)}\s*:\s*\{(.*?)\}\s*,\s*${Regex.escape(nextKey)}\s*:""",
            setOf(RegexOption.MULTILINE)
        )
        return pattern.find(page.replace("\n", " "))?.groupValues?.getOrNull(1) ?: ""
    }

    /**
     * 从 JS 对象块中提取键值对
     */
    private fun extractJsKeyValue(block: String, key: String): String? {
        if (block.isEmpty()) return null
        val pattern = Regex(
            """${Regex.escape(key)}\s*:\s*("(?:\\.|[^"])*"|true|false|null|None|-?\d+(?:\.\d+)?)"""
        )
        val match = pattern.find(block) ?: return null
        return parseJsScalar(match.groupValues[1])
    }

    private fun parseJsScalar(raw: String): String? {
        val value = raw.trim()
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        if (value == "null" || value == "None") return null
        return value
    }
}
