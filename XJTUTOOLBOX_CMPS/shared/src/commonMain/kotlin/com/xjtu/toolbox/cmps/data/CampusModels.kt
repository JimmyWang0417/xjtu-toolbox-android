package com.xjtu.toolbox.cmps.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class BuildingOption(
    val campus: String,
    val code: String,
    val displayName: String,
    val aliases: List<String> = emptyList(),
)

data class EmptyRoomQuery(
    val campus: String,
    val buildings: Set<String>,
    val week: Int,
    val dayOfWeek: Int,
    val sections: IntRange,
)

data class EmptyRoomItem(
    val roomName: String,
    val buildingName: String,
    val capacity: Int?,
    val availableSections: String,
    val source: DataSource,
)

data class ScheduleCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val sections: IntRange,
    val weeks: String,
    val colorSeed: Int,
)

data class ExamItem(
    val courseName: String,
    val courseCode: String,
    val examDate: String,
    val examTime: String,
    val location: String,
    val seatNumber: String,
)

data class TextbookItem(
    val courseName: String,
    val textbookName: String,
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val price: String = "",
    val edition: String = "",
) {
    val hasSubstantiveTextbook: Boolean
        get() = textbookName.trim() != "无教材" &&
            (textbookName.trim().length >= 2 || isbn.any { it.isDigit() } || author.trim().length >= 2)
}

data class JiaocaiBook(
    val id: String,
    val appId: Int,
    val engineInstanceId: Int,
    val title: String,
    val author: String,
    val summary: String,
    val hasFullText: Boolean,
)

data class CustomCourseEntity(
    val id: Long = 0,
    val courseName: String,
    val teacher: String = "",
    val location: String = "",
    val weekBits: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val termCode: String,
    val note: String = "",
    val createdAt: Long = 0,
) {
    fun toScheduleCourse(): ScheduleCourse = ScheduleCourse(
        name = courseName,
        teacher = teacher,
        location = location,
        dayOfWeek = dayOfWeek,
        sections = startSection..endSection,
        weeks = weekBits.mapIndexedNotNull { index, char -> if (char == '1') index + 1 else null }
            .joinToString("、"),
        colorSeed = id.toInt(),
    )
}

data class CampusCardSummary(
    val holder: String,
    val balance: String,
    val subsidy: String,
    val updatedAt: String,
    val account: String = "",
    val studentNo: String = "",
    val pendingAmount: String = "",
    val status: String = "正常",
)

data class CardTransaction(
    val title: String,
    val location: String,
    val amount: String,
    val time: String,
    val balance: String = "",
    val type: String = "",
    val category: String = "",
)

data class CardInfo(
    val account: String,
    val name: String,
    val studentNo: String,
    val balance: Double,
    val pendingAmount: Double,
    val lostFlag: Boolean,
    val frozenFlag: Boolean,
    val expireDate: String,
    val cardType: String,
    val department: String = "",
) {
    val statusLabel: String get() = when {
        lostFlag -> "已挂失"
        frozenFlag -> "已冻结"
        else -> "正常"
    }
}

data class RawCardTransaction(
    val time: String,
    val merchant: String,
    val amount: Double,
    val balance: Double,
    val type: String,
    val description: String,
) {
    val spending: Double get() = if (amount < 0) -amount else 0.0
}

data class YearMonth(val year: Int, val monthNumber: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int {
        val yearCompare = year.compareTo(other.year)
        return if (yearCompare != 0) yearCompare else monthNumber.compareTo(other.monthNumber)
    }

    fun label(): String = "$year-${monthNumber.toString().padStart(2, '0')}"

    fun lengthOfMonth(): Int = when (monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 30
    }
}

data class MerchantStat(
    val name: String,
    val totalAmount: Double,
    val count: Int,
)

data class MonthlyCardStats(
    val month: YearMonth,
    val totalSpend: Double,
    val totalIncome: Double,
    val transactionCount: Int,
    val topMerchants: List<MerchantStat>,
    val avgDailySpend: Double = 0.0,
    val peakDay: String = "",
    val peakDayAmount: Double = 0.0,
)

data class CardInsight(
    val monthlyStats: List<MonthlyCardStats>,
    val categorySpend: Map<String, Double>,
    val mealStats: Map<String, Double>,
)

data class PaymentCodeState(
    val code: String,
    val refreshedAt: String,
    val expiresInSeconds: Int,
    val authenticated: Boolean,
    val message: String = "",
)

data class CouponFilterOption(
    val label: String,
    val status: String,
    val count: String,
    val expired: String,
    val emptyTitle: String,
)

data class CouponRecord(
    val sendId: String,
    val showCardId: String,
    val voucherName: String,
    val typeName: String,
    val amountFen: Long,
    val leftAmountFen: Long,
    val leftCount: Int,
    val startDate: String,
    val endDate: String,
    val imageUrl: String,
) {
    val amountYuan: Double get() = amountFen / 100.0
    val leftAmountYuan: Double get() = leftAmountFen / 100.0
}

data class CouponPage(
    val records: List<CouponRecord>,
    val total: Int,
)

data class CouponDetail(
    val showCardId: String,
    val voucherName: String,
    val title: String,
    val description: String,
    val amountFen: Long,
    val leftAmountFen: Long,
    val startDate: String,
    val endDate: String,
    val batchId: String,
    val imageUrl: String,
    val closedPacketImageUrl: String,
    val openPacketImageUrl: String,
) {
    val amountYuan: Double get() = amountFen / 100.0
}

data class CouponType(
    val id: Int,
    val name: String,
)

data class ScoreRecord(
    val courseName: String,
    val credit: Double,
    val score: String,
    val gpa: Double?,
    val term: String,
    val selectedForGpa: Boolean = true,
)

data class GmisScheduleItem(
    val name: String,
    val teacher: String,
    val classroom: String,
    val weeks: String,
    val dayOfWeek: Int,
    val periodStart: Int,
    val periodEnd: Int,
) {
    fun getWeekList(): List<Int> {
        val match = Regex("""(\d+)-(\d+)""").find(weeks) ?: return emptyList()
        val start = match.groupValues[1].toIntOrNull() ?: return emptyList()
        val end = match.groupValues[2].toIntOrNull() ?: return emptyList()
        return (start..end).toList()
    }

    fun isInWeek(week: Int): Boolean = week in getWeekList()
}

data class GmisScoreItem(
    val courseName: String,
    val coursePoint: Double,
    val score: Double,
    val type: String,
    val examDate: String,
    val gpa: Double,
)

enum class ScoreSource { JwApp, Report }

enum class CourseGroup(val label: String, val shortLabel: String) {
    GeneralCore("通核", "通核"),
    GeneralElective("通选", "通选"),
}

data class ScoreDetailItem(
    val itemName: String,
    val itemPercent: Double,
    val itemScore: String,
    val itemScoreValue: Double?,
)

data class ScoreDetail(
    val courseName: String,
    val coursePoint: Double,
    val examType: String,
    val majorFlag: String?,
    val examProp: String,
    val replaceFlag: Boolean,
    val score: String,
    val scoreValue: Double?,
    val gpa: Double,
    val passFlag: Boolean,
    val specificReason: String?,
    val itemList: List<ScoreDetailItem>,
)

data class TermScore(
    val termCode: String,
    val termName: String,
    val scoreList: List<ScoreRecord>,
)

data class TranscriptTypeOption(
    val name: String,
    val value: Int,
    val cancelled: Boolean = false,
)

data class TranscriptLinkageResult(
    val studentId: String,
    val enrollYear: String,
    val templatePath: String,
    val categoryName: String,
    val workflowIdField: String,
)

data class TranscriptDownloadInfo(
    val filename: String,
    val downloadUrl: String,
    val fileSize: String,
)

data class TranscriptWorkflowState(
    val workflowName: String,
    val workflowId: Int,
    val defaultRequestName: String,
    val defaultDate: String,
    val typeOptions: List<TranscriptTypeOption>,
    val linkage: TranscriptLinkageResult,
    val downloadInfo: TranscriptDownloadInfo?,
    val statusMessage: String,
)

data class ScoreDistRange(val range: String, val num: Int)

data class ScoreRank(
    val defeatPercent: Double?,
    val scoreHigh: Double?,
    val scoreAvg: Double?,
    val scoreLow: Double?,
    val scoreDist: List<ScoreDistRange>,
)

data class GpaInfo(
    val gpa: Double,
    val averageScore: Double,
    val totalCredits: Double,
    val courseCount: Int,
)

data class TermOption(
    val code: String,
    val name: String,
)

data class DepartmentOption(
    val code: String,
    val name: String,
)

data class CampusOption(
    val code: String,
    val name: String,
)

data class ElectiveCategoryOption(
    val code: String,
    val name: String,
)

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
    val termCode: String,
) {
    val remaining: Int get() = capacity - enrollCount
    val fillRatio: Float get() = if (capacity > 0) (enrollCount.toFloat() / capacity).coerceIn(0f, 1f) else 0f
}

data class SchoolCourseResult(
    val totalSize: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val courses: List<SchoolCourse>,
) {
    val totalPages: Int get() = if (pageSize > 0) (totalSize + pageSize - 1) / pageSize else 0
}

data class LmsCourse(
    val title: String,
    val teacher: String,
    val unread: Int,
    val nextTask: String,
)

data class FitnessYear(
    val yearNum: String,
    val name: String,
    val checked: Boolean,
)

data class FitnessItem(
    val name: String,
    val value: String,
    val grade: String,
    val tone: String,
)

data class FitnessScore(
    val studentNumber: String,
    val studentName: String,
    val totalScore: String,
    val totalGrade: String,
    val reportType: String,
    val reportStatus: String,
    val sex: String,
    val grade: String,
    val items: List<FitnessItem>,
)

sealed interface AgentWidgetModel {
    data class Schedule(val title: String, val courses: List<ScheduleCourse>) : AgentWidgetModel
    data class Exams(val exams: List<ExamItem>) : AgentWidgetModel
    data class Rooms(val condition: String, val rooms: List<EmptyRoomItem>, val currentPeriod: Int) : AgentWidgetModel
    data class Attendance(val records: List<AttendanceRecord>) : AgentWidgetModel
    data class Grades(val grades: List<ScoreRecord>, val gpa: Double?, val totalCredits: Double) : AgentWidgetModel
    data class Card(val summary: CampusCardSummary) : AgentWidgetModel
}

data class AgentToolDescriptor(
    val name: String,
    val routeKey: String,
    val description: String,
    val loginRequired: Boolean,
)

data class AgentDashboard(
    val greeting: String,
    val tools: List<AgentToolDescriptor>,
    val widgets: List<AgentWidgetModel>,
)

data class JiaoxiaozhiModel(
    val id: String,
    val label: String,
    val description: String,
)

data class JiaoxiaozhiMessage(
    val role: String,
    val content: String,
    val createdAt: Long,
)

data class JiaoxiaozhiSessionInfo(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val locked: Boolean = false,
)

data class JiaoxiaozhiConversation(
    val messages: List<JiaoxiaozhiMessage> = emptyList(),
)

data class JiaoxiaozhiDashboard(
    val models: List<JiaoxiaozhiModel>,
    val defaultModelId: String,
    val sessions: List<JiaoxiaozhiSessionInfo>,
    val activeConversation: JiaoxiaozhiConversation,
    val networkEnabled: Boolean,
    val authenticated: Boolean,
    val refererUrl: String,
)

data class DownloadTask(
    val id: Long,
    val activityId: Int,
    val courseName: String,
    val activityTitle: String,
    val cameraType: String,
    val videoUrl: String,
    val audioSource: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedSize: Long,
    val status: String,
    val createTime: Long,
    val completeTime: Long?,
    val errorMessage: String?,
    val downloadSpeed: Long = 0,
) {
    val progress: Float get() = if (fileSize > 0) downloadedSize.toFloat() / fileSize else 0f
    val statusLabel: String get() = when (status) {
        "pending" -> "等待中"
        "downloading" -> "下载中"
        "paused" -> "已暂停"
        "completed" -> "已完成"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> status
    }
    val isResumable: Boolean get() = status == "paused" || status == "failed"
    val isActive: Boolean get() = status == "downloading" || status == "paused" || status == "pending"
}

data class DownloadConfig(
    val activityId: Int,
    val courseName: String,
    val activityTitle: String,
    val cameraType: String,
    val videoUrl: String,
    val audioSource: String,
)

data class DownloadProgress(
    val taskId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val status: String,
    val speedBytesPerSec: Long = 0,
)

data class BrowserState(
    val initialUrl: String,
    val currentUrl: String,
    val editingUrl: String,
    val pageTitle: String,
    val isLoading: Boolean,
    val progress: Float,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val cookieDomains: List<String>,
)

data class CacheEntryInfo(
    val key: String,
    val accountScope: String,
    val ttlMs: Long,
    val ageMs: Long?,
    val staleAvailable: Boolean,
)

data class LmsDepartment(
    val id: Int = 0,
    val name: String = "",
    val code: String = "",
)

data class LmsInstructor(
    val id: Int = 0,
    val name: String = "",
)

data class LmsCourseSummary(
    val id: Int = 0,
    val name: String = "",
    val courseCode: String = "",
    val credit: String = "",
    val compulsory: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null,
    val department: LmsDepartment = LmsDepartment(),
    val instructors: List<LmsInstructor> = emptyList(),
    val published: Boolean = false,
    val studentCount: Int = 0,
) {
    val semesterLabel: String
        get() {
            val sd = startDate ?: return "未知"
            return runCatching {
                val parts = sd.split("-")
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                if (month >= 8) "${year}-${year + 1} 秋" else "${year - 1}-${year} 春"
            }.getOrDefault("未知")
        }

    val instructorNames: String get() = instructors.joinToString(" / ") { it.name }.ifEmpty { "未知" }
}

enum class LmsActivityType(val value: String, val label: String) {
    Homework("homework", "作业"),
    Material("material", "课件"),
    Lesson("lesson", "课堂"),
    LectureLive("lecture_live", "直播"),
    Unknown("unknown", "未知"),
}

data class LmsUpload(
    val id: Int = 0,
    val name: String = "",
    val type: String = "",
    val size: Int = 0,
    val downloadUrl: String = "",
    val previewUrl: String = "",
) {
    val readableSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${((size / (1024.0 * 1024.0)) * 10).toInt() / 10.0} MB"
        }
}

data class LmsSubmission(
    val status: String = "",
    val score: String = "",
    val submittedAt: String = "",
) {
    val statusLabel: String
        get() = when (status) {
            "submitted" -> "已提交"
            "not_submitted" -> "未提交"
            "returned" -> "已退回"
            "scored" -> "已评分"
            else -> status.ifBlank { "未知" }
        }
}

data class LmsActivity(
    val id: Int = 0,
    val courseId: Int = 0,
    val type: LmsActivityType = LmsActivityType.Unknown,
    val title: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val published: Boolean = false,
    val uploads: List<LmsUpload> = emptyList(),
    val submission: LmsSubmission? = null,
    val replayCode: String? = null,
    val liveRoomName: String? = null,
    val liveStatus: String? = null,
    val replayVideos: List<ReplayVideo> = emptyList(),
)

data class ClassReplayCourse(
    val id: Int,
    val name: String,
    val displayName: String,
    val courseCode: String,
    val department: String,
    val instructors: List<String>,
    val isStarted: Boolean,
    val isClosed: Boolean,
    val startDate: String?,
    val endDate: String?,
) {
    val semesterLabel: String get() {
        val sd = startDate ?: return "未知"
        return runCatching {
            val parts = sd.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            if (month >= 8) "${year}-${year + 1} 秋" else "${year - 1}-${year} 春"
        }.getOrDefault("未知")
    }
}

data class LiveActivity(
    val id: Int,
    val title: String,
    val type: String,
    val startTime: String,
    val endTime: String,
    val courseId: Int,
    val isClosed: Boolean,
    val externalLiveId: String?,
)

data class ReplayVideo(
    val cameraId: Int,
    val cameraType: String,
    val url: String,
    val mute: Int,
) {
    val label: String get() = when (cameraType) {
        "instructor" -> "教师直播"
        "encoder" -> "电脑屏幕"
        else -> cameraType.ifBlank { "视频" }
    }
}

data class ReplayDetail(
    val activityId: Int,
    val title: String,
    val startTime: String,
    val endTime: String,
    val roomName: String?,
    val instructorNames: List<String>,
    val replayVideos: List<ReplayVideo>,
)

data class LibraryArea(
    val floor: String,
    val name: String,
    val available: Int,
    val total: Int,
) {
    val ratioLabel: String get() = "$available/$total"
}

data class SeatInfo(
    val seatId: String,
    val available: Boolean,
)

data class AreaStats(
    val available: Int,
    val total: Int,
) {
    val isOpen: Boolean get() = total > 0
    val label: String get() = "$available/$total"
}

data class BookResult(
    val success: Boolean,
    val message: String,
    val finalUrl: String = "",
)

data class RecommendPrefs(
    val emptinessWeight: Int = 3,
    val allEmptyBonus: Int = 5,
    val isolationLevel: Int = 2,
    val avoidEntrancePenalty: Int = 2,
    val avoidExitPenalty: Int = 0,
    val avoidAdjacentBusyPenalty: Int = 3,
    val wallBias: Int = 1,
    val cornerBias: Int = 1,
    val corridorSidePenalty: Int = 2,
    val facingWallBias: Int = 0,
    val avoidFacingCrowdBias: Int = 0,
    val enableTimeSlotAdjust: Boolean = false,
    val historyBias: Int = 0,
)

data class MyBookingInfo(
    val seatId: String?,
    val area: String?,
    val statusText: String?,
    val actionUrls: Map<String, String>,
)

sealed interface SeatResult {
    data class Success(
        val seats: List<SeatInfo>,
        val areaStatsMap: Map<String, AreaStats> = emptyMap(),
    ) : SeatResult

    data class AuthError(
        val message: String,
        val htmlPreview: String = "",
    ) : SeatResult

    data class Error(val message: String) : SeatResult
}

data class VenueSlot(
    val venue: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val available: Boolean,
)

data class Venue(
    val id: Int,
    val name: String,
    val address: String? = null,
    val iconType: String? = null,
)

data class VenueAreaSlot(
    val areaId: Long,
    val areaName: String,
    val stockId: Long,
    val timeSlot: String,
    val price: Double,
    val date: String,
    val status: Int,
    val allCount: Int,
    val usingNum: Int,
    val serviceId: String,
) {
    val isAvailable: Boolean get() = status == 1
    val remainCount: Int get() = (allCount - usingNum).coerceAtLeast(0)
}

data class CaptchaData(
    val id: String,
    val backgroundImage: String,
    val sliderImage: String,
    val bgWidth: Int,
    val bgHeight: Int,
    val sliderWidth: Int,
    val sliderHeight: Int,
)

data class VenueBookingResult(
    val success: Boolean,
    val orderId: String? = null,
    val price: Double = 0.0,
    val message: String = "",
)

data class NotificationItem(
    val title: String,
    val source: String,
    val date: String,
    val important: Boolean = false,
    val link: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
)

enum class AttendanceFlowType(val label: String) {
    Invalid("无效"),
    Valid("有效"),
    Repeated("重复"),
}

enum class AttendanceStatus(val label: String) {
    Normal("正常"),
    Late("迟到"),
    Absence("缺勤"),
    Leave("请假"),
}

data class AttendanceFlow(
    val id: String,
    val place: String,
    val waterTime: String,
    val type: AttendanceFlowType,
)

data class AttendanceRecord(
    val id: String,
    val termName: String,
    val startSection: Int,
    val endSection: Int,
    val week: Int,
    val location: String,
    val courseName: String,
    val teacher: String,
    val status: AttendanceStatus,
    val date: String,
)

data class AttendanceTerm(
    val code: String,
    val name: String,
    val startDate: String = "",
    val endDate: String = "",
)

data class CourseAttendanceStat(
    val subjectName: String,
    val subjectCode: String,
    val normalCount: Int,
    val lateCount: Int,
    val absenceCount: Int,
    val leaveEarlyCount: Int,
    val leaveCount: Int,
    val total: Int,
) {
    val actualCount: Int get() = normalCount + leaveCount
    val abnormalCount: Int get() = lateCount + absenceCount + leaveEarlyCount
    val attendanceRate: Int get() = if (total <= 0) 0 else (actualCount * 100 / total)
}

data class AttendanceSummary(
    val studentName: String,
    val studentNo: String,
    val departmentName: String,
    val campusName: String,
    val terms: List<AttendanceTerm>,
    val records: List<AttendanceRecord>,
    val flows: List<AttendanceFlow>,
    val courseStats: List<CourseAttendanceStat>,
) {
    val totalNormal: Int get() = records.count { it.status == AttendanceStatus.Normal }
    val totalLate: Int get() = records.count { it.status == AttendanceStatus.Late }
    val totalAbsence: Int get() = records.count { it.status == AttendanceStatus.Absence }
    val totalLeave: Int get() = records.count { it.status == AttendanceStatus.Leave }
    val attendanceRate: Int
        get() = if (records.isEmpty()) 0 else ((totalNormal + totalLeave) * 100 / records.size)
}

data class JudgeQuestionnaire(
    val id: String,
    val courseName: String,
    val teacherName: String,
    val termCode: String,
    val startTime: String,
    val endTime: String,
    val typeCode: String,
    val typeName: String,
    val finished: Boolean,
)

enum class JudgeQuestionType(val label: String) {
    Objective("客观题"),
    Subjective("主观题"),
    Score("分值题"),
    Text("文本题"),
    Select("下拉题"),
}

data class JudgeOption(
    val id: String,
    val label: String,
    val rank: String,
)

data class JudgeQuestion(
    val id: String,
    val title: String,
    val type: JudgeQuestionType,
    val answer: String = "",
    val subjectiveAnswer: String = "",
    val maxScore: Int? = null,
    val required: Boolean = true,
    val options: List<JudgeOption> = emptyList(),
)

data class JudgeAutoFillPlan(
    val questionnaireId: String,
    val targetScoreLabel: String,
    val subjectiveText: String,
    val firstObjectiveDowngraded: Boolean,
    val answers: Map<String, String>,
)

data class GraduateJudgeQuestionnaire(
    val assessment: String,
    val classId: String,
    val className: String,
    val teachingClassId: Int,
    val teacherId: String,
    val teacherName: String,
    val courseCode: String,
    val courseName: String,
    val department: String,
    val termCode: String,
    val termName: String,
    val status: String,
) {
    val pending: Boolean get() = status == "allow"
}

data class JudgeDashboard(
    val currentTerm: String,
    val undergraduate: List<JudgeQuestionnaire>,
    val graduate: List<GraduateJudgeQuestionnaire>,
    val sampleQuestions: List<JudgeQuestion>,
    val autoFillPlan: JudgeAutoFillPlan,
)

data class YellowPageCategory(
    val id: Int,
    val name: String,
    val status: Int,
    val sort: Int,
)

data class YellowPageDepartment(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val phone: String,
    val sort: Int,
    val status: Int,
) {
    val phoneItems: List<String>
        get() = phone.split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun dialNumber(item: String): String =
        Regex("""\d{7,}""").find(item)?.value.orEmpty()
}

data class YellowPageData(
    val categories: List<YellowPageCategory>,
    val departments: List<YellowPageDepartment>,
    val updateTime: String = "",
)

data class YwtbUserInfo(
    val userName: String,
    val userUid: String,
    val identityTypeName: String,
    val organizationName: String,
)

data class TeachingWeekInfo(
    val week: Int,
    val semesterName: String,
    val semesterId: String,
    val startOfTerm: String,
)

data class MobileJiaodaState(
    val launchUrl: String,
    val cookieDomains: List<String>,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loadingProgress: Float = 0f,
    val casHandoffEnabled: Boolean = true,
)

data class WebVpnConversionState(
    val inputUrl: String,
    val convertedUrl: String,
    val reversed: Boolean,
    val webVpnReady: Boolean,
    val error: String? = null,
)

data class AppSettingsState(
    val darkMode: String,
    val homeTheme: String,
    val navBarStyle: String,
    val defaultTab: String,
    val networkMode: String,
    val autoCheckUpdate: Boolean,
    val updateChannel: String,
    val showQuickActions: Boolean,
    val cacheSizeText: String,
    val versionText: String,
)

data class CalendarEvent(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val name: String,
    val remark: String,
    val days: Int,
    val colorHex: String,
)

data class SchoolTerm(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val termName: String,
    val yearName: String,
    val totalWeeks: Int,
    val workDays: Int,
    val events: List<CalendarEvent>,
) {
    fun currentWeek(today: LocalDate): Int {
        if (today < startDate || today > endDate) return 0
        return ((today.toEpochDays() - startDate.toEpochDays()) / 7 + 1)
    }

    fun daysRemaining(today: LocalDate): Int {
        if (today > endDate) return 0
        val from = if (today < startDate) startDate else today
        return endDate.toEpochDays() - from.toEpochDays()
    }

    fun progress(today: LocalDate): Float {
        if (today <= startDate) return 0f
        if (today >= endDate) return 1f
        val total = (endDate.toEpochDays() - startDate.toEpochDays() + 1).toFloat()
        val elapsed = (today.toEpochDays() - startDate.toEpochDays() + 1).toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    fun todayEvent(today: LocalDate): CalendarEvent? =
        events.firstOrNull { today >= it.startDate && today <= it.endDate }
}

data class ClassTimeSlot(
    val section: Int,
    val start: String,
    val end: String,
    val attendanceStart: String,
    val attendanceEnd: String,
) {
    val range: String get() = "$start-$end"
}

enum class DataSource(val label: String) {
    Cache("缓存"),
    JwApp("教务"),
    WebVpn("WebVPN"),
    Demo("演示"),
}
