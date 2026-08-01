package com.xjtu.toolbox.schedule

import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeDouble
import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "SchoolCourseApi"
private const val BASE_URL = "https://jwxt.xjtu.edu.cn"

// ── 数据模型 ────────────────────────────────────────

/** 学期选项 */
data class TermOption(
    val code: String,
    val name: String
)

/** 开课单位（院系）选项 */
data class DepartmentOption(
    val code: String,
    val name: String
)

/** 校区选项 */
data class CampusOption(
    val code: String,
    val name: String
)

/** 校公选课类别选项 */
data class ElectiveCategoryOption(
    val code: String,
    val name: String
)

/** 全校课程查询结果 */
data class SchoolCourse(
    val courseCode: String,
    val courseName: String,
    val sectionNumber: String,
    val teacher: String,
    val department: String,
    val credit: Double,
    val totalHours: Double,
    val lectureHours: Double,
    val labHours: Double,
    val practiceHours: Double,
    val enrollCount: Int,
    val capacity: Int,
    val className: String,
    val scheduleLocation: String,
    val campus: String,
    val isPublicElective: Boolean,
    val electiveCategory: String,
    val weeklyHours: Double,
    val maleEnrollCount: Int,
    val femaleEnrollCount: Int,
    val teachingClassId: String,
    val termCode: String
) {
    val remaining: Int get() = capacity - enrollCount
    val fillRatio: Float get() = if (capacity > 0) (enrollCount.toFloat() / capacity).coerceIn(0f, 1f) else 0f
}

/** 查询分页结果 */
data class SchoolCourseResult(
    val totalSize: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val courses: List<SchoolCourse>
) {
    val totalPages: Int get() = if (pageSize > 0) (totalSize + pageSize - 1) / pageSize else 0
}

// ── API ─────────────────────────────────────────────

class SchoolCourseApi(private val login: JwxtLogin) {

    private val appBase = "$BASE_URL/jwapp/sys/kcbcx"
    private var appInitialized = false

    private suspend fun ensureAppInitialized() {
        if (appInitialized) return
        try {
            login.client.get("$appBase/*default/index.do") {
                header("Accept", "text/html")
            }
            appInitialized = true
        } catch (e: Exception) {
            Logger.w(TAG, "ensureAppInitialized failed: ${e.message}")
        }
    }

    // ── 下拉选项查询 ──

    suspend fun getCurrentTerm(): String {
        ensureAppInitialized()
        val body = login.client.submitForm(
            url = "$appBase/modules/bjkcb/dqxnxq.do",
            formParameters = parameters { }
        ) {
            header("Accept", "application/json")
        }.bodyAsText()

        val json = body.safeParseJsonObject()
        return json["datas"]?.jsonObject
            ?.get("dqxnxq")?.jsonObject
            ?.get("rows")?.jsonArray?.get(0)?.jsonObject
            ?.get("DM")?.jsonPrimitive?.content ?: ""
    }

    suspend fun getTermList(): List<TermOption> {
        ensureAppInitialized()
        val body = login.client.submitForm(
            url = "$appBase/modules/bjkcb/xnxqcx.do",
            formParameters = parameters { append("*order", "-DM") }
        ) {
            header("Accept", "application/json")
        }.bodyAsText()

        val json = body.safeParseJsonObject()
        val rows = json["datas"]?.jsonObject
            ?.get("xnxqcx")?.jsonObject
            ?.get("rows")?.jsonArray
            ?: return emptyList()

        return rows.map { row ->
            val obj = row.jsonObject
            TermOption(
                code = obj["DM"]!!.jsonPrimitive.content,
                name = obj["MC"]?.jsonPrimitive?.content ?: obj["DM"]!!.jsonPrimitive.content
            )
        }
    }

    suspend fun getDepartments(): List<DepartmentOption> {
        ensureAppInitialized()
        val body = login.client.submitForm(
            url = "$BASE_URL/jwapp/code/44e02e19-e31b-4916-91b2-0a04380cbd3a.do",
            formParameters = parameters { }
        ) {
            header("Accept", "application/json")
        }.bodyAsText()

        val json = body.safeParseJsonObject()
        val rows = json["datas"]?.jsonObject
            ?.get("code")?.jsonObject
            ?.get("rows")?.jsonArray
            ?: return emptyList()

        return rows.map { row ->
            val obj = row.jsonObject
            DepartmentOption(
                code = obj["id"]!!.jsonPrimitive.content,
                name = obj["name"]!!.jsonPrimitive.content
            )
        }.sortedBy { it.name }
    }

    fun getCampusList(): List<CampusOption> {
        return listOf(
            CampusOption("1", "兴庆校区"),
            CampusOption("2", "雁塔校区"),
            CampusOption("3", "曲江校区"),
            CampusOption("4", "苏州校区"),
            CampusOption("5", "创新港校区")
        )
    }

    fun getElectiveCategories(): List<ElectiveCategoryOption> {
        return listOf(
            ElectiveCategoryOption("06", "基础通识类选修课"),
            ElectiveCategoryOption("07", "基础通识类核心课"),
            ElectiveCategoryOption("08", "钱学森学院特色课")
        )
    }

    // ── 核心查询 ──

    suspend fun queryCourses(
        termCode: String,
        courseName: String? = null,
        courseCode: String? = null,
        teacher: String? = null,
        departmentCode: String? = null,
        className: String? = null,
        campusCode: String? = null,
        isPublicElective: Boolean? = null,
        electiveCategoryCode: String? = null,
        weekday: Int? = null,
        startSection: Int? = null,
        endSection: Int? = null,
        pageSize: Int = 20,
        pageNumber: Int = 1
    ): SchoolCourseResult {
        ensureAppInitialized()

        // 构建 querySetting JSON 数组
        val queryParts = buildJsonArray {
            courseName?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("KCM", "课程名", "AND", "include", value))
            }
            courseCode?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("KCH", "课程号", "AND", "include", value))
            }
            teacher?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("SKJS", "上课教师", "AND", "include", value))
            }
            departmentCode?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("KKDWDM", "开课单位", "AND", "equal", value))
            }
            className?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("SKBJ", "上课班级", "AND", "include", value))
            }
            campusCode?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildCondition("XXXQDM", "学校校区", "AND", "equal", value))
            }
            isPublicElective?.let { value ->
                add(buildCondition("SFXGXK", "是否校公选课", "AND", "equal", if (value) "1" else "0"))
            }
            electiveCategoryCode?.takeIf { it.isNotBlank() }?.let { value ->
                add(buildConditionMValue("XGXKLBDM", "校公选课类别", "AND", value))
            }

            // 学期+任务状态
            add(buildJsonArray {
                add(buildSimpleCondition("XNXQDM", termCode, "and", "equal"))
                add(buildJsonArray {
                    add(buildSimpleCondition("RWZTDM", "1", "and", "equal"))
                    add(buildSimpleConditionNoValue("RWZTDM", "or", "isNull"))
                })
            })

            add(buildOrderCondition("+KKDWDM,+KCH,+KXH"))
        }

        val querySetting = queryParts.toString()
        Logger.d(TAG, "querySetting: $querySetting")

        val body = login.client.submitForm(
            url = "$appBase/modules/qxkcb/qxfbkccx.do",
            formParameters = parameters {
                append("querySetting", querySetting)
                append("*order", "+KKDWDM,+KCH,+KXH")
                append("SKXQ", weekday?.toString() ?: "")
                append("KSJC", startSection?.toString() ?: "")
                append("JSJC", endSection?.toString() ?: "")
                append("pageSize", pageSize.toString())
                append("pageNumber", pageNumber.toString())
            }
        ) {
            header("Accept", "application/json")
        }.bodyAsText()

        val json = body.safeParseJsonObject()
        val datas = json["datas"]?.jsonObject?.get("qxfbkccx")?.jsonObject

        val totalSize = datas?.get("totalSize")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val rows = datas?.get("rows")?.jsonArray ?: JsonArray(emptyList())

        val courses = rows.map { row ->
            val obj = row.jsonObject
            SchoolCourse(
                courseCode = obj["KCH"].safeString(),
                courseName = obj["KCM"].safeString(),
                sectionNumber = obj["KXH"].safeString(),
                teacher = obj["SKJS"].safeString(),
                department = obj["KKDWDM_DISPLAY"].safeString(),
                credit = obj["XF"].safeDouble(),
                totalHours = obj["XS"].safeDouble(),
                lectureHours = obj["SKXS"].safeDouble(),
                labHours = obj["SYXS"].safeDouble(),
                practiceHours = obj["SJXS"].safeDouble(),
                enrollCount = obj["XKZRS"].safeInt(),
                capacity = obj["KRL"].safeInt(),
                className = obj["SKBJ"].safeString(),
                scheduleLocation = obj["YPSJDD"].safeString(),
                campus = obj["XXXQDM_DISPLAY"].safeString(),
                isPublicElective = obj["SFXGXK"]?.jsonPrimitive?.content == "1",
                electiveCategory = obj["XGXKLBDM_DISPLAY"].safeString(),
                weeklyHours = obj["KNZXS"].safeDouble(),
                maleEnrollCount = obj["NSXKRS"].safeInt(),
                femaleEnrollCount = obj["NVSXKRS"].safeInt(),
                teachingClassId = obj["JXBID"].safeString(),
                termCode = obj["XNXQDM"].safeString()
            )
        }

        Logger.d(TAG, "queryCourses: totalSize=$totalSize, returned=${courses.size}, page=$pageNumber")
        return SchoolCourseResult(totalSize, pageNumber, pageSize, courses)
    }

    // ── JSON 辅助构建 ──

    private fun buildCondition(
        name: String, caption: String, linkOpt: String, builder: String, value: String
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("caption", caption)
        put("linkOpt", linkOpt)
        put("builderList", "cbl_String")
        put("builder", builder)
        put("value", value)
    }

    private fun buildConditionMValue(
        name: String, caption: String, linkOpt: String, value: String
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("caption", caption)
        put("linkOpt", linkOpt)
        put("builderList", "cbl_m_List")
        put("builder", "m_value_equal")
        put("value", value)
    }

    private fun buildSimpleCondition(
        name: String, value: String, linkOpt: String, builder: String
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("value", value)
        put("linkOpt", linkOpt)
        put("builder", builder)
    }

    private fun buildSimpleConditionNoValue(
        name: String, linkOpt: String, builder: String
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("linkOpt", linkOpt)
        put("builder", builder)
    }

    private fun buildOrderCondition(order: String): JsonObject = buildJsonObject {
        put("name", "*order")
        put("value", order)
        put("linkOpt", "AND")
        put("builder", "m_value_equal")
    }
}
