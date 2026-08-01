package com.xjtu.toolbox.cmps.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

interface CampusRepository {
    fun buildingOptions(campus: String): List<BuildingOption>
    suspend fun emptyRooms(query: EmptyRoomQuery): List<EmptyRoomItem>
    suspend fun weeklySchedule(): List<ScheduleCourse>
    suspend fun examSchedule(): List<ExamItem>
    suspend fun textbooks(): List<TextbookItem>
    suspend fun jiaocaiBooks(keyword: String = ""): List<JiaocaiBook>
    suspend fun customCourses(): List<CustomCourseEntity>
    suspend fun campusCard(): Pair<CampusCardSummary, List<CardTransaction>>
    suspend fun campusCardInsight(): CardInsight
    suspend fun paymentCode(): PaymentCodeState
    suspend fun couponFilters(): List<CouponFilterOption>
    suspend fun couponTypes(): List<CouponType>
    suspend fun couponPage(filter: CouponFilterOption? = null): CouponPage
    suspend fun couponDetail(showCardId: String): CouponDetail
    suspend fun scores(): List<ScoreRecord>
    suspend fun termScores(): List<TermScore>
    suspend fun transcriptWorkflow(): TranscriptWorkflowState
    suspend fun gmisSchedule(): List<GmisScheduleItem>
    suspend fun gmisScores(): List<GmisScoreItem>
    suspend fun schoolCourseOptions(): Triple<List<TermOption>, List<DepartmentOption>, List<CampusOption>>
    suspend fun querySchoolCourses(keyword: String = ""): SchoolCourseResult
    suspend fun fitnessYears(): List<FitnessYear>
    suspend fun fitnessScore(yearNum: String? = null): FitnessScore
    suspend fun scoreRank(): ScoreRank
    suspend fun gpaInfo(): GpaInfo
    suspend fun lmsCourses(): List<LmsCourse>
    suspend fun lmsCourseSummaries(): List<LmsCourseSummary>
    suspend fun lmsActivities(courseId: Int? = null): List<LmsActivity>
    suspend fun classReplayCourses(): List<ClassReplayCourse>
    suspend fun liveActivities(courseId: Int? = null): List<LiveActivity>
    suspend fun replayDetails(): List<ReplayDetail>
    suspend fun libraryAreas(): List<LibraryArea>
    suspend fun librarySeats(areaCode: String? = null): SeatResult
    suspend fun libraryBooking(): MyBookingInfo?
    suspend fun libraryRecommendations(): List<SeatInfo>
    suspend fun venueSlots(): List<VenueSlot>
    suspend fun venues(): List<Venue>
    suspend fun venueAreaSlots(): List<VenueAreaSlot>
    suspend fun venueBookingPreview(): VenueBookingResult
    suspend fun attendanceSummary(postgraduate: Boolean = false): AttendanceSummary
    suspend fun judgeDashboard(): JudgeDashboard
    suspend fun yellowPage(): YellowPageData
    suspend fun ywtbUserInfo(): YwtbUserInfo
    suspend fun teachingWeekInfo(): TeachingWeekInfo?
    suspend fun mobileJiaodaState(): MobileJiaodaState
    fun convertWebVpnUrl(input: String, reversed: Boolean, webVpnReady: Boolean = false): WebVpnConversionState
    fun settingsState(): AppSettingsState
    suspend fun agentDashboard(): AgentDashboard
    suspend fun jiaoxiaozhiDashboard(): JiaoxiaozhiDashboard
    suspend fun downloadTasks(): List<DownloadTask>
    suspend fun browserState(initialUrl: String = ""): BrowserState
    suspend fun cacheEntries(): List<CacheEntryInfo>
    suspend fun notifications(): List<NotificationItem>
    suspend fun schoolTerms(): List<SchoolTerm>
    fun classTimeSlots(): List<ClassTimeSlot>
}

class DemoCampusRepository(
    private val localStore: CampusLocalStore = CampusLocalStore(),
    private val webVpnUrlCodec: WebVpnUrlCodec = WebVpnUrlCodec(),
) : CampusRepository {
    private val buildings = listOf(
        BuildingOption("兴庆校区", "MAIN_A", "主楼A"),
        BuildingOption("兴庆校区", "MAIN_B", "主楼B"),
        BuildingOption("兴庆校区", "EAST_2", "东二楼", listOf("东2")),
        BuildingOption("兴庆校区", "WEST_2", "西二楼", listOf("西2")),
        BuildingOption("雁塔校区", "MED_1", "医学教学楼"),
        BuildingOption("雁塔校区", "FINANCE", "财经教学楼"),
        BuildingOption("曲江校区", "QJ_WEST_1", "西一楼"),
        BuildingOption("曲江校区", "QJ_WEST_5", "西五楼"),
        BuildingOption("曲江校区", "QJ_WEST_4", "西四楼"),
        BuildingOption("曲江校区", "QJ_WEST_6", "西六楼"),
        BuildingOption("创新港校区", "INNOVATION_1", "1号巨构", listOf("1")),
        BuildingOption("创新港校区", "INNOVATION_2", "2号巨构", listOf("2")),
        BuildingOption("创新港校区", "INNOVATION_3", "3号巨构", listOf("3")),
        BuildingOption("创新港校区", "INNOVATION_4", "4号巨构", listOf("4")),
        BuildingOption("创新港校区", "INNOVATION_5", "5号巨构", listOf("5")),
        BuildingOption("创新港校区", "INNOVATION_9", "9号巨构", listOf("9")),
        BuildingOption("创新港校区", "INNOVATION_18", "18号巨构", listOf("18")),
        BuildingOption("创新港校区", "INNOVATION_19", "19号巨构", listOf("19")),
        BuildingOption("创新港校区", "INNOVATION_20", "20号巨构", listOf("20")),
        BuildingOption("创新港校区", "INNOVATION_21", "21号巨构", listOf("21")),
        BuildingOption("创新港校区", "INNOVATION_LIBRARY", "图书馆"),
        BuildingOption("创新港校区", "INNOVATION_GREEN_2", "2号绿楔"),
        BuildingOption("创新港校区", "INNOVATION_GREEN_3", "3号绿楔"),
        BuildingOption("创新港校区", "INNOVATION_STADIUM", "主楼运动场"),
        BuildingOption("创新港校区", "INNOVATION_MUSEUM", "工程博物馆-创新港"),
    )

    override fun buildingOptions(campus: String): List<BuildingOption> =
        buildings.filter { it.campus == campus }.ifEmpty { buildings.filter { it.campus == "兴庆校区" } }

    override suspend fun emptyRooms(query: EmptyRoomQuery): List<EmptyRoomItem> =
        query.buildings.flatMapIndexed { index, building ->
            listOf(
                EmptyRoomItem("${building}${101 + index}", building, 80 + index * 16, "第${query.sections.first}-${query.sections.last}节", DataSource.Demo),
                EmptyRoomItem("${building}${203 + index}", building, 120, "全天可用", DataSource.Cache),
            )
        }

    override suspend fun weeklySchedule(): List<ScheduleCourse> = listOf(
        ScheduleCourse("高等数学", "王老师", "主楼A203", 1, 1..2, "1-16周", 1),
        ScheduleCourse("程序设计", "李老师", "西二楼301", 2, 3..4, "1-14周", 2),
        ScheduleCourse("大学物理", "赵老师", "涵英楼2-105", 4, 5..6, "3-16周", 3),
    )

    override suspend fun examSchedule(): List<ExamItem> = listOf(
        ExamItem("高等数学", "MATH101", "2026-07-03", "09:00-11:00", "主楼A203", "18"),
        ExamItem("大学物理", "PHYS101", "2026-07-06", "14:30-16:30", "涵英楼2-105", "32"),
    )

    override suspend fun textbooks(): List<TextbookItem> = listOf(
        TextbookItem("高等数学", "高等数学（第八版）", "同济大学数学系", "高等教育出版社", "9787040396638", "42.00"),
        TextbookItem("程序设计", "Kotlin 程序设计实践", "课程组", "校内教材", price = "36.00"),
    )

    override suspend fun jiaocaiBooks(keyword: String): List<JiaocaiBook> = listOf(
        JiaocaiBook("general_55258665", 17071, 10700, "高等数学（第八版）", "同济大学数学系", "课程：高等数学；获取方式：馆藏/电子教材", true),
        JiaocaiBook("general_55258666", 17071, 10700, "大学物理学", "物理教学中心", "课程：大学物理；获取方式：图书馆教材平台", true),
        JiaocaiBook("general_55258667", 17071, 10700, "Kotlin 程序设计实践", "课程组", "课程：程序设计；校内讲义", false),
    ).filter { keyword.isBlank() || it.title.contains(keyword, ignoreCase = true) || it.summary.contains(keyword, ignoreCase = true) }

    override suspend fun customCourses(): List<CustomCourseEntity> = listOf(
        CustomCourseEntity(
            id = 1,
            courseName = "自习计划",
            location = "图书馆",
            weekBits = "11111111111111111111",
            dayOfWeek = 5,
            startSection = 9,
            endSection = 10,
            termCode = "2025-2026-2",
            note = "手动添加",
        )
    )

    private val demoCardInfo = CardInfo(
        account = "10000001",
        name = "西迁人",
        studentNo = "local",
        balance = 86.40,
        pendingAmount = 12.00,
        lostFlag = false,
        frozenFlag = false,
        expireDate = "2030-12-31",
        cardType = "学生卡",
        department = "创新港",
    )

    private val demoRawTransactions = listOf(
        RawCardTransaction("2026-06-29 12:08:00", "康桥苑餐厅", -12.50, 86.40, "午餐消费", "餐饮"),
        RawCardTransaction("2026-06-28 20:41:00", "教育超市", -8.00, 98.90, "超市消费", "购物"),
        RawCardTransaction("2026-06-28 18:02:00", "梧桐苑餐厅", 5.00, 106.90, "加餐券抵扣", "补贴"),
        RawCardTransaction("2026-06-27 22:18:00", "浴室水控", -3.20, 101.90, "洗浴", "水控"),
        RawCardTransaction("2026-06-27 08:15:00", "咖啡吧台", -9.90, 105.10, "早餐消费", "餐饮"),
    )

    override suspend fun campusCard(): Pair<CampusCardSummary, List<CardTransaction>> =
        CampusCardToolkit.summarize(demoCardInfo, "刚刚") to CampusCardToolkit.presentTransactions(demoRawTransactions)

    override suspend fun campusCardInsight(): CardInsight =
        CampusCardToolkit.insight(demoRawTransactions)

    override suspend fun paymentCode(): PaymentCodeState =
        PaymentCodeState(
            code = "6222 0000 0000 0000 000",
            refreshedAt = "演示",
            expiresInSeconds = 60,
            authenticated = false,
            message = "需要校园卡认证后刷新付款码",
        )

    override suspend fun couponFilters(): List<CouponFilterOption> = listOf(
        CouponFilterOption("可领取", "0", "", "3", "暂无可领取加餐券"),
        CouponFilterOption("可使用", "1", "1", "3", "暂无可使用加餐券"),
        CouponFilterOption("已用完", "", "0", "", "暂无已用完加餐券"),
        CouponFilterOption("已过期", "", "1", "2", "暂无已过期加餐券"),
    )

    override suspend fun couponTypes(): List<CouponType> =
        listOf(CouponType(4, "加餐券"), CouponType(5, "餐补券"))

    override suspend fun couponPage(filter: CouponFilterOption?): CouponPage {
        val records = listOf(
            CouponRecord("send-1", "card-1", "夜宵加餐券", "加餐券", 500, 500, 1, "2026-06-01", "2026-07-01", ""),
            CouponRecord("send-2", "card-2", "运动补给券", "餐补券", 300, 0, 0, "2026-05-01", "2026-06-01", ""),
        )
        val filtered = when (filter?.label) {
            "可使用", "可领取" -> records.filter { it.leftAmountFen > 0 && it.leftCount > 0 }
            "已用完" -> records.filter { it.leftAmountFen <= 0 || it.leftCount <= 0 }
            else -> records
        }
        return CouponPage(filtered, filtered.size)
    }

    override suspend fun couponDetail(showCardId: String): CouponDetail =
        CouponDetail(
            showCardId = showCardId,
            voucherName = "夜宵加餐券",
            title = "校园餐饮补贴",
            description = "可在指定餐厅消费时抵扣。",
            amountFen = 500,
            leftAmountFen = 500,
            startDate = "2026-06-01",
            endDate = "2026-07-01",
            batchId = "batch-demo",
            imageUrl = "",
            closedPacketImageUrl = "",
            openPacketImageUrl = "",
        )

    override suspend fun scores(): List<ScoreRecord> = listOf(
        ScoreRecord("高等数学", 5.0, "92", 4.0, "2025-2026-1"),
        ScoreRecord("大学物理", 4.0, "88", 3.7, "2025-2026-1"),
        ScoreRecord("程序设计", 3.5, "96", 4.0, "2025-2026-1"),
        ScoreRecord("思政实践", 1.0, "优秀", null, "2025-2026-1", selectedForGpa = false),
    )

    override suspend fun termScores(): List<TermScore> =
        scores().groupBy { it.term }.map { (term, list) -> TermScore(term, term, list) }

    override suspend fun transcriptWorkflow(): TranscriptWorkflowState =
        TranscriptWorkflowState(
            workflowName = "在校本科生",
            workflowId = 29,
            defaultRequestName = "电子成绩单申请",
            defaultDate = "2026-06-29",
            typeOptions = listOf(
                TranscriptTypeOption("本科生中文成绩单", 0),
                TranscriptTypeOption("本科生英文成绩单", 1),
                TranscriptTypeOption("本科生中文在读证明", 2),
            ),
            linkage = TranscriptLinkageResult(
                studentId = "local",
                enrollYear = "2022",
                templatePath = "/cpt/transcript_undergraduate.cpt",
                categoryName = "本科生成绩证明",
                workflowIdField = "29",
            ),
            downloadInfo = TranscriptDownloadInfo("transcript-preview.pdf", "https://dzpz.xjtu.edu.cn/download/demo", "256 KB"),
            statusMessage = "表单加载、联动、预览 PDF、双阶段提交、下载信息已抽象为跨端流程状态",
        )

    override suspend fun gmisSchedule(): List<GmisScheduleItem> = listOf(
        GmisScheduleItem("矩阵分析", "刘老师", "创新港涵英楼", "1-16周", 1, 1, 2),
        GmisScheduleItem("学术写作", "周老师", "创新港泓理楼", "3-12周", 3, 5, 6),
    )

    override suspend fun gmisScores(): List<GmisScoreItem> = listOf(
        GmisScoreItem("矩阵分析", 3.0, 91.0, "学位课程", "2026-01-08", 4.0),
        GmisScoreItem("学术写作", 2.0, 88.0, "选修课程", "2026-01-10", 3.7),
    )

    override suspend fun schoolCourseOptions(): Triple<List<TermOption>, List<DepartmentOption>, List<CampusOption>> =
        Triple(
            listOf(TermOption("2025-2026-2", "2025-2026学年 第二学期"), TermOption("2025-2026-1", "2025-2026学年 第一学期")),
            listOf(DepartmentOption("13028000", "物理学院"), DepartmentOption("13068000", "计算机科学与技术学院")),
            listOf(CampusOption("1", "兴庆校区"), CampusOption("5", "创新港校区")),
        )

    override suspend fun querySchoolCourses(keyword: String): SchoolCourseResult {
        val courses = listOf(
            SchoolCourse(
                courseCode = "MATH101",
                courseName = "高等数学",
                sectionNumber = "01",
                teacher = "王老师",
                department = "数学与统计学院",
                credit = 5.0,
                totalHours = 80.0,
                lectureHours = 80.0,
                labHours = 0.0,
                practiceHours = 0.0,
                enrollCount = 96,
                capacity = 120,
                className = "工科试验班",
                scheduleLocation = "周一 1-2 主楼A203",
                campus = "兴庆校区",
                isPublicElective = false,
                electiveCategory = "",
                weeklyHours = 5.0,
                maleEnrollCount = 65,
                femaleEnrollCount = 31,
                teachingClassId = "JXB-MATH101-01",
                termCode = "2025-2026-2",
            ),
            SchoolCourse(
                courseCode = "GELECT06",
                courseName = "中国传统文化",
                sectionNumber = "03",
                teacher = "陈老师",
                department = "人文学院",
                credit = 2.0,
                totalHours = 32.0,
                lectureHours = 32.0,
                labHours = 0.0,
                practiceHours = 0.0,
                enrollCount = 188,
                capacity = 200,
                className = "全校公选",
                scheduleLocation = "周四 9-10 涵英楼2-105",
                campus = "创新港校区",
                isPublicElective = true,
                electiveCategory = "基础通识类选修课",
                weeklyHours = 2.0,
                maleEnrollCount = 90,
                femaleEnrollCount = 98,
                teachingClassId = "JXB-GELECT06-03",
                termCode = "2025-2026-2",
            ),
        ).filter { keyword.isBlank() || it.courseName.contains(keyword, ignoreCase = true) || it.teacher.contains(keyword, ignoreCase = true) }
        return SchoolCourseResult(courses.size, 1, 20, courses)
    }

    override suspend fun fitnessYears(): List<FitnessYear> = listOf(
        FitnessYear("2025", "2025 学年", true),
        FitnessYear("2024", "2024 学年", false),
    )

    override suspend fun fitnessScore(yearNum: String?): FitnessScore =
        FitnessScore(
            studentNumber = "local",
            studentName = "西迁人",
            totalScore = "87.50",
            totalGrade = "良好",
            reportType = "年度体测",
            reportStatus = "已发布",
            sex = "男",
            grade = "2022级",
            items = listOf(
                FitnessItem("身高 / 体重", "22.1", "优秀", "success"),
                FitnessItem("肺活量", "4650", "优秀", "success"),
                FitnessItem("立定跳远", "245", "良好", "primary"),
                FitnessItem("坐位体前屈", "18.5", "良好", "primary"),
                FitnessItem("引体向上", "10", "及格", "warning"),
                FitnessItem("50 米", "7.2", "良好", "primary"),
                FitnessItem("1000 米", "3:52", "良好", "primary"),
            ),
        )

    override suspend fun scoreRank(): ScoreRank = ScoreRank(
        defeatPercent = 91.2,
        scoreHigh = 98.0,
        scoreAvg = 83.6,
        scoreLow = 61.0,
        scoreDist = listOf(
            ScoreDistRange("90-100", 21),
            ScoreDistRange("80-89", 43),
            ScoreDistRange("70-79", 18),
            ScoreDistRange("60-69", 7),
        ),
    )

    override suspend fun gpaInfo(): GpaInfo {
        val records = scores().filter { it.selectedForGpa && it.gpa != null }
        val totalCredits = records.sumOf { it.credit }
        val weightedGpa = records.sumOf { (it.gpa ?: 0.0) * it.credit }
        val numericScores = records.mapNotNull { it.score.toDoubleOrNull() }
        return GpaInfo(
            gpa = if (totalCredits > 0) weightedGpa / totalCredits else 0.0,
            averageScore = numericScores.average().takeIf { !it.isNaN() } ?: 0.0,
            totalCredits = totalCredits,
            courseCount = records.size,
        )
    }

    override suspend fun lmsCourses(): List<LmsCourse> = listOf(
        LmsCourse("程序设计", "李老师", 3, "实验报告 4 明晚截止"),
        LmsCourse("大学物理", "赵老师", 1, "第 8 章测验待完成"),
        LmsCourse("形势与政策", "陈老师", 0, "暂无待办"),
    )

    override suspend fun lmsCourseSummaries(): List<LmsCourseSummary> = listOf(
        LmsCourseSummary(
            id = 1001,
            name = "程序设计",
            courseCode = "CS101",
            credit = "3.5",
            compulsory = true,
            startDate = "2026-02-23",
            department = LmsDepartment(name = "计算机学院"),
            instructors = listOf(LmsInstructor(name = "李老师")),
            published = true,
            studentCount = 120,
        ),
        LmsCourseSummary(
            id = 1002,
            name = "大学物理",
            courseCode = "PHYS101",
            credit = "4.0",
            compulsory = true,
            startDate = "2026-02-23",
            department = LmsDepartment(name = "物理学院"),
            instructors = listOf(LmsInstructor(name = "赵老师")),
            published = true,
            studentCount = 96,
        ),
    )

    override suspend fun lmsActivities(courseId: Int?): List<LmsActivity> = listOf(
        LmsActivity(
            id = 1,
            courseId = 1001,
            type = LmsActivityType.Homework,
            title = "实验报告 4",
            endTime = "2026-06-30 23:59",
            published = true,
            submission = LmsSubmission(status = "not_submitted"),
        ),
        LmsActivity(
            id = 2,
            courseId = 1001,
            type = LmsActivityType.Material,
            title = "第 8 章课件",
            published = true,
            uploads = listOf(LmsUpload(name = "chapter-8.pdf", size = 2_430_000)),
        ),
        LmsActivity(
            id = 3,
            courseId = 1002,
            type = LmsActivityType.LectureLive,
            title = "大学物理直播课",
            startTime = "2026-06-29 14:30",
            endTime = "2026-06-29 16:20",
            liveRoomName = "涵英楼直播间",
            liveStatus = "replay",
            replayVideos = listOf(ReplayVideo(1, "encoder", "", 0), ReplayVideo(2, "instructor", "", 0)),
        ),
    ).filter { courseId == null || it.courseId == courseId }

    override suspend fun classReplayCourses(): List<ClassReplayCourse> = listOf(
        ClassReplayCourse(1001, "程序设计", "程序设计", "CS101", "计算机学院", listOf("李老师"), true, false, "2026-02-23", "2026-07-12"),
        ClassReplayCourse(1002, "大学物理", "大学物理", "PHYS101", "物理学院", listOf("赵老师"), true, false, "2026-02-23", "2026-07-12"),
    )

    override suspend fun liveActivities(courseId: Int?): List<LiveActivity> = listOf(
        LiveActivity(301, "程序设计第 12 周", "lecture_live", "2026-05-12 10:10", "2026-05-12 12:00", 1001, true, "live-301"),
        LiveActivity(302, "大学物理实验讲解", "lecture_live", "2026-05-18 14:30", "2026-05-18 16:20", 1002, true, "live-302"),
    ).filter { courseId == null || it.courseId == courseId }

    override suspend fun replayDetails(): List<ReplayDetail> = listOf(
        ReplayDetail(
            activityId = 301,
            title = "程序设计第 12 周",
            startTime = "2026-05-12 10:10",
            endTime = "2026-05-12 12:00",
            roomName = "西二楼301",
            instructorNames = listOf("李老师"),
            replayVideos = listOf(ReplayVideo(1, "encoder", "", 0), ReplayVideo(2, "instructor", "", 0)),
        )
    )

    override suspend fun libraryAreas(): List<LibraryArea> = listOf(
        LibraryArea("二楼", "二层连廊及流通大厅", 42, 118),
        LibraryArea("三楼", "北楼三层ILibrary-A（东）", 31, 80),
        LibraryArea("四楼", "北楼四层中间", 56, 96),
    )

    override suspend fun librarySeats(areaCode: String?): SeatResult = SeatResult.Success(
        seats = listOf(
            SeatInfo("A101", true),
            SeatInfo("A102", false),
            SeatInfo("A103", true),
            SeatInfo("B210", true),
        ),
        areaStatsMap = mapOf(
            "north2elian" to AreaStats(42, 118),
            "east3A" to AreaStats(31, 80),
        ),
    )

    override suspend fun libraryBooking(): MyBookingInfo? =
        MyBookingInfo(
            seatId = "A101",
            area = "二层连廊及流通大厅",
            statusText = "已预约，待签到",
            actionUrls = mapOf("取消预约" to "/my/?cancel=1", "入馆签到" to "/my/?firstruguan=1"),
        )

    override suspend fun libraryRecommendations(): List<SeatInfo> =
        listOf(SeatInfo("A101", true), SeatInfo("A103", true), SeatInfo("B210", true))

    override suspend fun venueSlots(): List<VenueSlot> = listOf(
        VenueSlot("羽毛球 1 号场", LocalDate(2026, 6, 30), LocalTime(19, 0), LocalTime(20, 0), true),
        VenueSlot("篮球半场 A", LocalDate(2026, 6, 30), LocalTime(20, 0), LocalTime(21, 0), false),
        VenueSlot("乒乓球 3 号台", LocalDate(2026, 7, 1), LocalTime(18, 0), LocalTime(19, 0), true),
    )

    override suspend fun venues(): List<Venue> = listOf(
        Venue(11, "羽毛球馆", "兴庆校区体育馆", "badminton"),
        Venue(12, "篮球馆", "创新港体育中心", "basketball"),
        Venue(13, "乒乓球馆", "文体中心", "tabletennis"),
    )

    override suspend fun venueAreaSlots(): List<VenueAreaSlot> = listOf(
        VenueAreaSlot(101, "羽毛球 1 号场", 9001, "19:00-20:00", 20.0, "2026-06-30", 1, 1, 0, "11"),
        VenueAreaSlot(102, "羽毛球 2 号场", 9002, "20:00-21:00", 20.0, "2026-06-30", 1, 1, 0, "11"),
        VenueAreaSlot(201, "篮球半场 A", 9101, "20:00-21:00", 30.0, "2026-06-30", 0, 1, 1, "12"),
    )

    override suspend fun venueBookingPreview(): VenueBookingResult =
        VenueBookingResult(false, price = 20.0, message = "需要完成滑块验证码和移动交大支付确认")

    override suspend fun attendanceSummary(postgraduate: Boolean): AttendanceSummary {
        val term = if (postgraduate) "2025-2026 春季学期" else "2025-2026-2"
        val records = listOf(
            AttendanceRecord("kq-1", term, 1, 2, 18, "主楼A203", "高等数学", "王老师", AttendanceStatus.Normal, "2026-06-24"),
            AttendanceRecord("kq-2", term, 3, 4, 18, "西二楼301", "程序设计", "李老师", AttendanceStatus.Late, "2026-06-25"),
            AttendanceRecord("kq-3", term, 5, 6, 18, "涵英楼2-105", "大学物理", "赵老师", AttendanceStatus.Leave, "2026-06-26"),
            AttendanceRecord("kq-4", term, 7, 8, 18, "主楼B101", "形势与政策", "陈老师", AttendanceStatus.Absence, "2026-06-27"),
        )
        val stats = records.groupBy { it.courseName }.map { (name, list) ->
            CourseAttendanceStat(
                subjectName = name,
                subjectCode = "",
                normalCount = list.count { it.status == AttendanceStatus.Normal },
                lateCount = list.count { it.status == AttendanceStatus.Late },
                absenceCount = list.count { it.status == AttendanceStatus.Absence },
                leaveEarlyCount = 0,
                leaveCount = list.count { it.status == AttendanceStatus.Leave },
                total = list.size,
            )
        }
        return AttendanceSummary(
            studentName = if (postgraduate) "研途同学" else "西迁人",
            studentNo = if (postgraduate) "pg-local" else "local",
            departmentName = if (postgraduate) "研究生院" else "计算机学院",
            campusName = "创新港",
            terms = listOf(
                AttendanceTerm("2025-2026-2", "2025-2026 第二学期", "2026-02-23", "2026-07-12"),
                AttendanceTerm("2025-2026-1", "2025-2026 第一学期", "2025-09-01", "2026-01-18"),
            ),
            records = records,
            flows = listOf(
                AttendanceFlow("flow-1", "主楼A203", "2026-06-29 08:03", AttendanceFlowType.Valid),
                AttendanceFlow("flow-2", "西二楼301", "2026-06-29 10:17", AttendanceFlowType.Repeated),
            ),
            courseStats = stats,
        )
    }

    override suspend fun judgeDashboard(): JudgeDashboard {
        val undergraduate = listOf(
            JudgeQuestionnaire("wj-1", "高等数学", "王老师", "2025-2026-2", "2026-06-01", "2026-07-05", "01", "期末评教", false),
            JudgeQuestionnaire("wj-2", "程序设计", "李老师", "2025-2026-2", "2026-06-01", "2026-07-05", "05", "过程评教", true),
        )
        val questions = listOf(
            JudgeQuestion(
                id = "zb-1",
                title = "教师讲授重点突出、条理清晰",
                type = JudgeQuestionType.Objective,
                answer = "A",
                options = listOf(
                    JudgeOption("A", "非常满意", "1"),
                    JudgeOption("B", "满意", "2"),
                    JudgeOption("C", "基本满意", "3"),
                ),
            ),
            JudgeQuestion(
                id = "zb-2",
                title = "课程建议",
                type = JudgeQuestionType.Subjective,
                subjectiveAnswer = "无",
            ),
        )
        return JudgeDashboard(
            currentTerm = "2025-2026-2",
            undergraduate = undergraduate,
            graduate = listOf(
                GraduateJudgeQuestionnaire(
                    assessment = "allow",
                    classId = "BJ001",
                    className = "研究生课程班",
                    teachingClassId = 8001,
                    teacherId = "T001",
                    teacherName = "周老师",
                    courseCode = "GSTE101",
                    courseName = "学术写作",
                    department = "研究生院",
                    termCode = "2025-2026-2",
                    termName = "2025-2026 春",
                    status = "allow",
                )
            ),
            sampleQuestions = questions,
            autoFillPlan = JudgeAutoFillPlan(
                questionnaireId = undergraduate.first().id,
                targetScoreLabel = "优秀",
                subjectiveText = "无",
                firstObjectiveDowngraded = true,
                answers = questions.associate { it.id to (it.answer.ifBlank { it.subjectiveAnswer }) },
            ),
        )
    }

    override suspend fun yellowPage(): YellowPageData = YellowPageData(
        categories = listOf(
            YellowPageCategory(1, "党政机关", 1, 1),
            YellowPageCategory(2, "学院书院", 1, 2),
            YellowPageCategory(3, "校园服务", 1, 3),
        ),
        departments = listOf(
            YellowPageDepartment(101, 1, "党委办公室、校长办公室", "82668888 / 88968888", 1, 1),
            YellowPageDepartment(201, 2, "计算机科学与技术学院", "82668666", 1, 1),
            YellowPageDepartment(301, 3, "网络信息中心", "82667777 / 88967777", 1, 1),
            YellowPageDepartment(302, 3, "后勤保障部", "82665555", 2, 1),
        ),
        updateTime = "2026年06月",
    )

    override suspend fun ywtbUserInfo(): YwtbUserInfo =
        YwtbUserInfo("西迁人", "local-uid", "本科生", "计算机学院")

    override suspend fun teachingWeekInfo(): TeachingWeekInfo? =
        TeachingWeekInfo(18, "第二学期", "2025-2026", "2026-02-23")

    override suspend fun mobileJiaodaState(): MobileJiaodaState =
        MobileJiaodaState(
            launchUrl = "https://superapp.xjtu.edu.cn/",
            cookieDomains = listOf(
                "superapp.xjtu.edu.cn",
                "transaction.xjtu.edu.cn",
                "transaction-service.xjtu.edu.cn",
                "message-service.xjtu.edu.cn",
                "reservation.xjtu.edu.cn",
                "reservation-service.xjtu.edu.cn",
                "lms-h5.xjtu.edu.cn",
                "identity1.xjtu.edu.cn",
                "api-org.tronclass.com.cn",
                "tyxylp.xjtu.edu.cn",
                "login.xjtu.edu.cn",
                "cas.xjtu.edu.cn",
            ),
            casHandoffEnabled = true,
        )

    override fun convertWebVpnUrl(input: String, reversed: Boolean, webVpnReady: Boolean): WebVpnConversionState {
        return webVpnUrlCodec.convert(input, reversed, webVpnReady)
    }

    override fun settingsState(): AppSettingsState =
        AppSettingsState(
            darkMode = localStore.getSetting("darkMode", "跟随系统"),
            homeTheme = localStore.getSetting("homeTheme", "卡片主题"),
            navBarStyle = localStore.getSetting("navBarStyle", "经典底栏"),
            defaultTab = localStore.getSetting("defaultTab", "首页"),
            networkMode = localStore.getSetting("networkMode", "自动检测"),
            autoCheckUpdate = localStore.getFlag("autoCheckUpdate", true),
            updateChannel = localStore.getSetting("updateChannel", "stable"),
            showQuickActions = localStore.getFlag("showQuickActions", true),
            cacheSizeText = "账号隔离缓存",
            versionText = "CMPS 0.1.0",
        )

    override suspend fun agentDashboard(): AgentDashboard {
        val card = campusCard().first
        return AgentDashboard(
            greeting = "你好，可以直接查课表、考试、空教室、考勤、成绩、校园卡、黄页和通知。",
            tools = listOf(
                AgentToolDescriptor("get_schedule", "schedule", "查询课表与自定义日程", true),
                AgentToolDescriptor("get_empty_rooms", "empty_room", "查询空闲教室", false),
                AgentToolDescriptor("get_attendance", "attendance", "查询考勤记录与统计", true),
                AgentToolDescriptor("search_yellow_page", "yellow_page", "查询校园黄页", false),
                AgentToolDescriptor("ask_jiaoxiaozhi", "jiaoxiaozhi", "查询政策和办事流程", true),
            ),
            widgets = listOf(
                AgentWidgetModel.Schedule("本周课表", weeklySchedule()),
                AgentWidgetModel.Exams(examSchedule()),
                AgentWidgetModel.Rooms("兴庆校区 第1-12节", emptyRooms(EmptyRoomQuery("兴庆校区", setOf("主楼A"), 1, 1, 1..12)), 0),
                AgentWidgetModel.Attendance(attendanceSummary().records.take(3)),
                AgentWidgetModel.Grades(scores(), gpaInfo().gpa, gpaInfo().totalCredits),
                AgentWidgetModel.Card(card),
            ),
        )
    }

    override suspend fun jiaoxiaozhiDashboard(): JiaoxiaozhiDashboard =
        JiaoxiaozhiDashboard(
            models = listOf(
                JiaoxiaozhiModel("qwen-plus", "Qwen-Plus", "速度与效果均衡"),
                JiaoxiaozhiModel("qwen-max", "Qwen-Max", "适合复杂任务"),
                JiaoxiaozhiModel("ep-20250207092149-pvc95", "DeepSeek-R1", "侧重推理、数学与代码"),
                JiaoxiaozhiModel("ep-20250219175323-5mvmg", "Doubao1.5-Pro", "响应稳定、综合能力均衡"),
            ),
            defaultModelId = "qwen-plus",
            sessions = listOf(
                JiaoxiaozhiSessionInfo("jxzz-demo", "办事流程咨询", "qwen-plus", currentEpochMillis() - 86_400_000, currentEpochMillis()),
            ),
            activeConversation = JiaoxiaozhiConversation(
                messages = listOf(
                    JiaoxiaozhiMessage("user", "创新港成绩单怎么下载？", currentEpochMillis() - 60_000),
                    JiaoxiaozhiMessage("assistant", "可以在一网通办或电子证明服务中申请成绩单，生成预览后提交并下载 PDF。", currentEpochMillis()),
                )
            ),
            networkEnabled = true,
            authenticated = false,
            refererUrl = "https://assistant.xjtu.edu.cn/digitalPeople3/",
        )

    override suspend fun downloadTasks(): List<DownloadTask> = listOf(
        DownloadTask(
            id = 1,
            activityId = 301,
            courseName = "程序设计",
            activityTitle = "程序设计第 12 周",
            cameraType = "encoder",
            videoUrl = "https://replay.example/encoder.mp4",
            audioSource = "both",
            filePath = "Downloads/ClassReplay/程序设计/程序设计第12周_电脑屏幕.mp4",
            fileSize = 524_288_000,
            downloadedSize = 314_572_800,
            status = "downloading",
            createTime = 1_782_700_000_000,
            completeTime = null,
            errorMessage = null,
            downloadSpeed = 1_048_576,
        ),
        DownloadTask(
            id = 2,
            activityId = 302,
            courseName = "大学物理",
            activityTitle = "实验讲解",
            cameraType = "instructor",
            videoUrl = "https://replay.example/instructor.mp4",
            audioSource = "instructor",
            filePath = "Downloads/ClassReplay/大学物理/实验讲解_教师直播.mp4",
            fileSize = 220_200_960,
            downloadedSize = 220_200_960,
            status = "completed",
            createTime = 1_782_600_000_000,
            completeTime = 1_782_610_000_000,
            errorMessage = null,
        ),
    )

    override suspend fun browserState(initialUrl: String): BrowserState {
        val url = initialUrl.ifBlank { "https://www.xjtu.edu.cn/" }
        val host = url.substringAfter("://", url).substringBefore("/")
        return BrowserState(
            initialUrl = url,
            currentUrl = url,
            editingUrl = url,
            pageTitle = "内置浏览器",
            isLoading = false,
            progress = 1f,
            canGoBack = false,
            canGoForward = false,
            cookieDomains = listOf(host, "login.xjtu.edu.cn", "webvpn.xjtu.edu.cn").distinct(),
        )
    }

    override suspend fun cacheEntries(): List<CacheEntryInfo> = localStore.defaultCacheEntries()

    override suspend fun notifications(): List<NotificationItem> = listOf(
        NotificationItem("关于暑期校园服务安排的通知", "学校办公室", "2026-06-28", important = true),
        NotificationItem("图书馆端午期间开放时间调整", "图书馆", "2026-06-27"),
        NotificationItem("创新港通勤班车时刻更新", "后勤保障部", "2026-06-26"),
    )

    override suspend fun schoolTerms(): List<SchoolTerm> = listOf(
        SchoolTerm(
            id = "demo-2026-spring",
            startDate = LocalDate(2026, 2, 23),
            endDate = LocalDate(2026, 7, 12),
            termName = "第二学期",
            yearName = "2025-2026学年",
            totalWeeks = 20,
            workDays = 100,
            events = listOf(
                CalendarEvent("demo-exam", LocalDate(2026, 7, 1), LocalDate(2026, 7, 12), "考试周", "期末考试", 12, "#ff9500"),
                CalendarEvent("demo-summer", LocalDate(2026, 7, 13), LocalDate(2026, 8, 23), "暑假", "假期", 42, "#34c759"),
            ),
        )
    )

    override fun classTimeSlots(): List<ClassTimeSlot> = XjtuTime.getAllSlots()
}

class HybridCampusRepository(
    private val fallback: CampusRepository = DemoCampusRepository(),
    private val localStore: CampusLocalStore = CampusLocalStore(),
    private val authCoordinator: AuthSessionCoordinator = AuthSessionCoordinator(localStore, CampusCasAuthBridge()),
    private val emptyRoomApi: EmptyRoomCdnApi = EmptyRoomCdnApi(),
    private val schoolCalendarApi: SchoolCalendarApi = SchoolCalendarApi(),
    private val notificationApi: NotificationApi = NotificationApi(),
    private val yellowPageApi: YellowPageApi = YellowPageApi(),
    private val jiaocaiApi: JiaocaiApi = JiaocaiApi(),
    private val campusCardApi: CampusCardApi = CampusCardApi(),
    private val jwAppApi: JwAppApi = JwAppApi(),
    private val fitnessApi: FitnessApi = FitnessApi(),
    private val attendanceApi: AttendanceApi = AttendanceApi(),
    private val couponApi: CouponApi = CouponApi(),
    private val libraryApi: LibraryApi = LibraryApi(),
    private val venueApi: VenueApi = VenueApi(),
    private val lmsApi: LmsApi = LmsApi(),
    private val classReplayApi: ClassReplayApi = ClassReplayApi(),
    private val undergraduateJudgeApi: UndergraduateJudgeApi = UndergraduateJudgeApi(),
    private val graduateJudgeApi: GraduateJudgeApi = GraduateJudgeApi(),
    private val paymentCodeApi: PaymentCodeApi = PaymentCodeApi(),
    private val ywtbApi: YwtbApi = YwtbApi(),
) : CampusRepository by fallback {
    override suspend fun emptyRooms(query: EmptyRoomQuery): List<EmptyRoomItem> =
        runCatching { emptyRoomApi.emptyRooms(query) }.getOrElse { fallback.emptyRooms(query) }

    override suspend fun weeklySchedule(): List<ScheduleCourse> =
        runCatching { jwAppApi.weeklySchedule(requireTicket(CampusEndpoint.JwApp)) }.getOrElse { fallback.weeklySchedule() }

    override suspend fun examSchedule(): List<ExamItem> =
        runCatching { jwAppApi.examSchedule(requireTicket(CampusEndpoint.JwApp)) }.getOrElse { fallback.examSchedule() }

    override suspend fun scores(): List<ScoreRecord> =
        runCatching { jwAppApi.termScores(requireTicket(CampusEndpoint.JwApp)).flatMap { it.scoreList } }
            .getOrElse { fallback.scores() }

    override suspend fun termScores(): List<TermScore> =
        runCatching { jwAppApi.termScores(requireTicket(CampusEndpoint.JwApp)) }.getOrElse { fallback.termScores() }

    override suspend fun fitnessYears(): List<FitnessYear> =
        runCatching { fitnessApi.years(requireTicket(CampusEndpoint.Fitness)) }.getOrElse { fallback.fitnessYears() }

    override suspend fun fitnessScore(yearNum: String?): FitnessScore =
        runCatching { fitnessApi.score(requireTicket(CampusEndpoint.Fitness), yearNum) }.getOrElse { fallback.fitnessScore(yearNum) }

    override suspend fun attendanceSummary(postgraduate: Boolean): AttendanceSummary =
        if (postgraduate) {
            fallback.attendanceSummary(postgraduate)
        } else {
            runCatching { attendanceApi.summary(requireTicket(CampusEndpoint.Attendance)) }
                .getOrElse { fallback.attendanceSummary(postgraduate) }
        }

    override suspend fun couponTypes(): List<CouponType> =
        runCatching { couponApi.types(requireTicket(CampusEndpoint.Coupon)) }.getOrElse { fallback.couponTypes() }

    override suspend fun couponPage(filter: CouponFilterOption?): CouponPage =
        runCatching { couponApi.page(requireTicket(CampusEndpoint.Coupon), filter) }.getOrElse { fallback.couponPage(filter) }

    override suspend fun couponDetail(showCardId: String): CouponDetail =
        runCatching { couponApi.detail(requireTicket(CampusEndpoint.Coupon), showCardId) }.getOrElse { fallback.couponDetail(showCardId) }

    override suspend fun libraryAreas(): List<LibraryArea> =
        runCatching { libraryApi.areas(requireTicket(CampusEndpoint.LibrarySeat)) }.getOrElse { fallback.libraryAreas() }

    override suspend fun librarySeats(areaCode: String?): SeatResult =
        runCatching { libraryApi.seats(requireTicket(CampusEndpoint.LibrarySeat), areaCode) }.getOrElse { fallback.librarySeats(areaCode) }

    override suspend fun venues(): List<Venue> =
        runCatching { venueApi.venues(requireTicket(CampusEndpoint.Venue)) }.getOrElse { fallback.venues() }

    override suspend fun venueAreaSlots(): List<VenueAreaSlot> =
        runCatching {
            val ticket = requireTicket(CampusEndpoint.Venue)
            val venue = venueApi.venues(ticket).firstOrNull() ?: return@runCatching emptyList()
            venueApi.availableAreaSlots(ticket, venue.id)
        }.getOrElse { fallback.venueAreaSlots() }

    override suspend fun venueSlots(): List<VenueSlot> =
        runCatching {
            val ticket = requireTicket(CampusEndpoint.Venue)
            val venue = venueApi.venues(ticket).firstOrNull()
            venueApi.slots(ticket, venue)
        }.getOrElse { fallback.venueSlots() }

    override suspend fun lmsCourses(): List<LmsCourse> =
        runCatching { lmsApi.dashboardCourses(requireTicket(CampusEndpoint.Lms)) }.getOrElse { fallback.lmsCourses() }

    override suspend fun lmsCourseSummaries(): List<LmsCourseSummary> =
        runCatching { lmsApi.courses(requireTicket(CampusEndpoint.Lms)) }.getOrElse { fallback.lmsCourseSummaries() }

    override suspend fun lmsActivities(courseId: Int?): List<LmsActivity> =
        runCatching { lmsApi.activities(requireTicket(CampusEndpoint.Lms), courseId) }.getOrElse { fallback.lmsActivities(courseId) }

    override suspend fun classReplayCourses(): List<ClassReplayCourse> =
        runCatching { classReplayApi.courses(requireTicket(CampusEndpoint.ClassReplay)) }.getOrElse { fallback.classReplayCourses() }

    override suspend fun liveActivities(courseId: Int?): List<LiveActivity> =
        runCatching { classReplayApi.liveActivities(requireTicket(CampusEndpoint.ClassReplay), courseId) }
            .getOrElse { fallback.liveActivities(courseId) }

    override suspend fun replayDetails(): List<ReplayDetail> =
        runCatching { classReplayApi.replayDetails(requireTicket(CampusEndpoint.ClassReplay)) }.getOrElse { fallback.replayDetails() }

    override suspend fun judgeDashboard(): JudgeDashboard {
        val base = fallback.judgeDashboard()
        val undergraduate = runCatching {
            undergraduateJudgeApi.dashboard(requireTicket(CampusEndpoint.JwxtJudge))
        }.getOrNull()
        val graduate = runCatching {
            graduateJudgeApi.questionnaires(requireTicket(CampusEndpoint.GraduateJudge))
        }.getOrElse { base.graduate }
        return base.copy(
            currentTerm = undergraduate?.first ?: base.currentTerm,
            undergraduate = undergraduate?.second ?: base.undergraduate,
            graduate = graduate,
            autoFillPlan = base.autoFillPlan.copy(
                questionnaireId = (undergraduate?.second?.firstOrNull { !it.finished }?.id ?: base.autoFillPlan.questionnaireId),
            ),
        )
    }

    override suspend fun schoolTerms(): List<SchoolTerm> =
        runCatching { schoolCalendarApi.getTerms() }.getOrElse { fallback.schoolTerms() }

    override fun classTimeSlots(): List<ClassTimeSlot> = XjtuTime.getAllSlots()

    override suspend fun notifications(): List<NotificationItem> =
        runCatching { notificationApi.getMergedNotifications() }.getOrElse { fallback.notifications() }

    override suspend fun yellowPage(): YellowPageData =
        runCatching { yellowPageApi.getData() }.getOrElse { fallback.yellowPage() }

    override suspend fun jiaocaiBooks(keyword: String): List<JiaocaiBook> =
        runCatching { jiaocaiApi.search(keyword.ifBlank { "高等数学" }) }.getOrElse { fallback.jiaocaiBooks(keyword) }

    override suspend fun campusCard(): Pair<CampusCardSummary, List<CardTransaction>> =
        runCatching {
            val ticket = requireTicket(CampusEndpoint.CampusCard)
            val card = campusCardApi.getCardInfo(ticket)
            val transactions = campusCardApi.getTransactions(ticket)
            CampusCardToolkit.summarize(card, "刚刚") to CampusCardToolkit.presentTransactions(transactions)
        }.getOrElse { fallback.campusCard() }

    override suspend fun campusCardInsight(): CardInsight =
        runCatching {
            val ticket = requireTicket(CampusEndpoint.CampusCard)
            CampusCardToolkit.insight(campusCardApi.getTransactions(ticket))
        }.getOrElse { fallback.campusCardInsight() }

    override suspend fun paymentCode(): PaymentCodeState =
        runCatching {
            val authenticated = paymentCodeApi.authenticate()
            val code = paymentCodeApi.getBarCode()
            PaymentCodeState(
                code = code,
                refreshedAt = currentEpochMillis().toString(),
                expiresInSeconds = 60,
                authenticated = authenticated,
            )
        }.getOrElse { fallback.paymentCode() }

    override suspend fun ywtbUserInfo(): YwtbUserInfo =
        runCatching { ywtbApi.userInfo(requireTicket(CampusEndpoint.Ywtb)) }.getOrElse { fallback.ywtbUserInfo() }

    override suspend fun teachingWeekInfo(): TeachingWeekInfo? =
        runCatching { ywtbApi.currentTeachingWeek() }.getOrElse { fallback.teachingWeekInfo() }

    private suspend fun requireTicket(endpoint: CampusEndpoint): AuthTicket {
        val accountId = localStore.activeAccountId.takeIf { it.isNotBlank() && it != "default" }
            ?: error("未登录")
        val current = authCoordinator.ticketFor(accountId, endpoint)
        if (current != null) return current
        val step = authCoordinator.ensureTicket(accountId, endpoint)
        if (step !is LoginStep.Success && step !is LoginStep.Loading) {
            error("认证未完成")
        }
        return authCoordinator.ticketFor(accountId, endpoint)
            ?: error("认证票据不可用")
    }
}
