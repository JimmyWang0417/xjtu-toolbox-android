package com.xjtu.toolbox.cmps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.cmps.app.LocalCampusRepository
import com.xjtu.toolbox.cmps.data.AgentDashboard
import com.xjtu.toolbox.cmps.data.AgentWidgetModel
import com.xjtu.toolbox.cmps.data.AttendanceSummary
import com.xjtu.toolbox.cmps.data.BrowserState
import com.xjtu.toolbox.cmps.data.CacheEntryInfo
import com.xjtu.toolbox.cmps.data.CouponFilterOption
import com.xjtu.toolbox.cmps.data.CouponPage
import com.xjtu.toolbox.cmps.data.DownloadTask
import com.xjtu.toolbox.cmps.data.FitnessScore
import com.xjtu.toolbox.cmps.data.GmisScheduleItem
import com.xjtu.toolbox.cmps.data.GmisScoreItem
import com.xjtu.toolbox.cmps.data.JiaocaiBook
import com.xjtu.toolbox.cmps.data.JiaoxiaozhiDashboard
import com.xjtu.toolbox.cmps.data.LibraryArea
import com.xjtu.toolbox.cmps.data.MyBookingInfo
import com.xjtu.toolbox.cmps.data.SeatInfo
import com.xjtu.toolbox.cmps.data.SeatResult
import com.xjtu.toolbox.cmps.data.JudgeDashboard
import com.xjtu.toolbox.cmps.data.LmsCourse
import com.xjtu.toolbox.cmps.data.LmsActivity
import com.xjtu.toolbox.cmps.data.ClassReplayCourse
import com.xjtu.toolbox.cmps.data.MobileJiaodaState
import com.xjtu.toolbox.cmps.data.ReplayDetail
import com.xjtu.toolbox.cmps.data.NotificationItem
import com.xjtu.toolbox.cmps.data.SchoolCourseResult
import com.xjtu.toolbox.cmps.data.SchoolTerm
import com.xjtu.toolbox.cmps.data.TranscriptWorkflowState
import com.xjtu.toolbox.cmps.data.Venue
import com.xjtu.toolbox.cmps.data.VenueAreaSlot
import com.xjtu.toolbox.cmps.data.VenueBookingResult
import com.xjtu.toolbox.cmps.data.VenueSlot
import com.xjtu.toolbox.cmps.data.YellowPageData
import com.xjtu.toolbox.cmps.data.YwtbUserInfo
import com.xjtu.toolbox.cmps.ui.components.DataRow
import com.xjtu.toolbox.cmps.ui.components.SelectableBlock
import com.xjtu.toolbox.cmps.ui.components.ServiceTile
import com.xjtu.toolbox.cmps.ui.components.StatusBand
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
fun LmsScreen() {
    val repository = LocalCampusRepository.current
    var courses by remember { mutableStateOf<List<LmsCourse>>(emptyList()) }
    var activities by remember { mutableStateOf<List<LmsActivity>>(emptyList()) }
    LaunchedEffect(repository) {
        courses = repository.lmsCourses()
        activities = repository.lmsActivities()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("思源", "课程、作业、测验、直播回放与通知统一陈列。", MiuixTheme.colorScheme.primary) }
        items(courses) { course ->
            ServiceTile(course.title, "${course.teacher} · ${course.nextTask}", Icons.Default.School, MiuixTheme.colorScheme.primary, badgeText = course.unread.takeIf { it > 0 }?.toString()) {}
        }
        item { Text("活动与作业", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(activities) { activity ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(activity.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("类型", activity.type.label)
                    activity.endTime?.let { DataRow("截止", it) }
                    activity.submission?.let { DataRow("提交", it.statusLabel) }
                    if (activity.uploads.isNotEmpty()) {
                        DataRow("附件", activity.uploads.joinToString("、") { "${it.name} ${it.readableSize}" })
                    }
                    if (activity.replayVideos.isNotEmpty()) {
                        DataRow("回放", activity.replayVideos.joinToString("、") { it.label })
                    }
                }
            }
        }
    }
}

@Composable
fun ClassReplayScreen() {
    val repository = LocalCampusRepository.current
    var courses by remember { mutableStateOf<List<ClassReplayCourse>>(emptyList()) }
    var details by remember { mutableStateOf<List<ReplayDetail>>(emptyList()) }
    LaunchedEffect(repository) {
        courses = repository.classReplayCourses()
        details = repository.replayDetails()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("课程回放", "课程平台课程、直播活动、教师/屏幕双机位回放统一迁移。", MiuixTheme.colorScheme.primary) }
        items(courses) { course ->
            ServiceTile(
                title = course.displayName,
                subtitle = "${course.semesterLabel} · ${course.instructors.joinToString(" / ")} · ${course.department}",
                icon = Icons.Default.School,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item { Text("最近回放", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(details) { detail ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(detail.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("时间", "${detail.startTime} - ${detail.endTime}")
                    DataRow("教室", detail.roomName ?: "--")
                    DataRow("教师", detail.instructorNames.joinToString(" / ").ifBlank { "--" })
                    DataRow("机位", detail.replayVideos.joinToString("、") { it.label })
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    val repository = LocalCampusRepository.current
    var areas by remember { mutableStateOf<List<LibraryArea>>(emptyList()) }
    var booking by remember { mutableStateOf<MyBookingInfo?>(null) }
    var recommendations by remember { mutableStateOf<List<SeatInfo>>(emptyList()) }
    var seatResult by remember { mutableStateOf<SeatResult?>(null) }
    LaunchedEffect(repository) {
        areas = repository.libraryAreas()
        booking = repository.libraryBooking()
        recommendations = repository.libraryRecommendations()
        seatResult = repository.librarySeats()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("图书馆", "座位余量、智能推荐、预约、换座、签到动作会集中到一个页面。", MiuixTheme.colorScheme.primary) }
        booking?.let { info ->
            item {
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("我的预约", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        DataRow("座位", info.seatId ?: "--")
                        DataRow("区域", info.area ?: "--")
                        DataRow("状态", info.statusText ?: "--")
                        if (info.actionUrls.isNotEmpty()) DataRow("可操作", info.actionUrls.keys.joinToString("、"))
                    }
                }
            }
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("座位推荐", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    recommendations.forEach { seat ->
                        DataRow(seat.seatId, if (seat.available) "可预约" else "已占用")
                    }
                }
            }
        }
        items(areas) { area ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(area.name, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("楼层", area.floor)
                    DataRow("可用/总数", area.ratioLabel)
                }
            }
        }
        item {
            val total = (seatResult as? SeatResult.Success)?.seats?.size ?: 0
            val available = (seatResult as? SeatResult.Success)?.seats?.count { it.available } ?: 0
            ServiceTile("座位明细", "$available/$total 可用 · 支持换座/取消/签到动作", Icons.Default.EventSeat, MiuixTheme.colorScheme.secondary) {}
        }
    }
}

@Composable
fun VenueScreen() {
    val repository = LocalCampusRepository.current
    var slots by remember { mutableStateOf<List<VenueSlot>>(emptyList()) }
    var venues by remember { mutableStateOf<List<Venue>>(emptyList()) }
    var areaSlots by remember { mutableStateOf<List<VenueAreaSlot>>(emptyList()) }
    var bookingPreview by remember { mutableStateOf<VenueBookingResult?>(null) }
    LaunchedEffect(repository) {
        slots = repository.venueSlots()
        venues = repository.venues()
        areaSlots = repository.venueAreaSlots()
        bookingPreview = repository.venueBookingPreview()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("场馆预订", "场地、日期、时段、收藏和冲突提示会按 Android 版迁移。", MiuixTheme.colorScheme.primary) }
        items(venues) { venue ->
            ServiceTile(
                title = venue.name,
                subtitle = venue.address ?: "校园场馆",
                icon = Icons.Default.SportsTennis,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("预订状态", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("价格", bookingPreview?.price?.let { "¥ $it" } ?: "--")
                    DataRow("结果", bookingPreview?.message ?: "--")
                }
            }
        }
        items(areaSlots) { area ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(area.areaName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("日期", area.date)
                    DataRow("时段", area.timeSlot)
                    DataRow("余量", "${area.remainCount}/${area.allCount}")
                    DataRow("价格", "¥ ${area.price}")
                    DataRow("状态", if (area.isAvailable) "可预约" else "已满/锁定")
                }
            }
        }
        items(slots) { slot ->
            ServiceTile(
                title = slot.venue,
                subtitle = "${slot.date} ${slot.start}-${slot.end} · ${if (slot.available) "可预约" else "已满"}",
                icon = Icons.Default.SportsTennis,
                tint = if (slot.available) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ) {}
        }
    }
}

@Composable
fun NotificationScreen() {
    val repository = LocalCampusRepository.current
    var notices by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    LaunchedEffect(repository) { notices = repository.notifications() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("通知公告", "学校、学院、教务、图书馆公告聚合检索。", MiuixTheme.colorScheme.primary) }
        items(notices) { notice ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(notice.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow(notice.source, notice.date)
                    if (notice.category.isNotBlank()) DataRow("分类", notice.category)
                    if (notice.tags.isNotEmpty()) DataRow("标签", notice.tags.joinToString("、"))
                    if (notice.important) DataRow("优先级", "重要")
                }
            }
        }
    }
}

@Composable
fun SchoolCalendarScreen() {
    val repository = LocalCampusRepository.current
    var terms by remember { mutableStateOf<List<SchoolTerm>>(emptyList()) }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    LaunchedEffect(repository) { terms = repository.schoolTerms() }
    val current = terms.firstOrNull { today >= it.startDate && today <= it.endDate } ?: terms.lastOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("校历", "学期进度、周次、假期和考试安排来自学校校历接口。", MiuixTheme.colorScheme.primary) }
        if (current != null) {
            item {
                ServiceTile(
                    title = "${current.yearName} ${current.termName}",
                    subtitle = "第 ${current.currentWeek(today)} 周 · 剩余 ${current.daysRemaining(today)} 天 · 进度 ${(current.progress(today) * 100).roundToInt()}%",
                    icon = Icons.Default.CalendarMonth,
                    tint = MiuixTheme.colorScheme.primary,
                ) {}
            }
            items(current.events) { event ->
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(event.name, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        DataRow("日期", "${event.startDate} 至 ${event.endDate}")
                        DataRow("天数", "${event.days} 天")
                        if (event.remark.isNotBlank()) DataRow("备注", event.remark)
                    }
                }
            }
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("作息时间", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    repository.classTimeSlots().forEach { slot ->
                        DataRow("第 ${slot.section} 节", slot.range)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceScreen(postgraduate: Boolean = false) {
    val repository = LocalCampusRepository.current
    var summary by remember { mutableStateOf<AttendanceSummary?>(null) }
    LaunchedEffect(repository, postgraduate) {
        summary = repository.attendanceSummary(postgraduate)
    }
    val data = summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusBand(
                title = if (postgraduate) "研究生考勤" else "本科考勤",
                summary = "学生信息、学期列表、课程统计、打卡流水和异常记录按 Android 版状态机迁移。",
                color = MiuixTheme.colorScheme.primary,
            )
        }
        item {
            ServiceTile(
                title = "出勤率",
                subtitle = data?.let { "${it.attendanceRate}% · 正常 ${it.totalNormal} · 迟到 ${it.totalLate} · 缺勤 ${it.totalAbsence} · 请假 ${it.totalLeave}" } ?: "同步中",
                icon = Icons.Default.AssignmentTurnedIn,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("学生与学期", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("姓名", data?.studentName ?: "--")
                    DataRow("学号", data?.studentNo ?: "--")
                    DataRow("学院", data?.departmentName ?: "--")
                    DataRow("校区", data?.campusName ?: "--")
                    DataRow("学期", data?.terms?.joinToString("、") { it.name } ?: "--")
                }
            }
        }
        item { Text("课程统计", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.courseStats.orEmpty()) { stat ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stat.subjectName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("出勤率", "${stat.attendanceRate}%")
                    DataRow("正常/请假", "${stat.normalCount}/${stat.leaveCount}")
                    DataRow("迟到/缺勤", "${stat.lateCount}/${stat.absenceCount}")
                }
            }
        }
        item { Text("最近记录", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.records.orEmpty()) { record ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(record.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("状态", record.status.label)
                    DataRow("时间", "${record.date} 第${record.startSection}-${record.endSection}节")
                    DataRow("地点", record.location)
                    DataRow("教师", record.teacher.ifBlank { "--" })
                }
            }
        }
        item { Text("打卡流水", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.flows.orEmpty()) { flow ->
            ServiceTile(flow.place, "${flow.waterTime} · ${flow.type.label}", Icons.Default.AssignmentTurnedIn, MiuixTheme.colorScheme.secondary) {}
        }
    }
}

@Composable
fun JudgeScreen() {
    val repository = LocalCampusRepository.current
    var dashboard by remember { mutableStateOf<JudgeDashboard?>(null) }
    LaunchedEffect(repository) {
        dashboard = repository.judgeDashboard()
    }
    val data = dashboard
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("评教", "本科 jwapp 评教与研究生 GSTE 评教统一迁移，保留一键填报计划与防全优降档规则。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                title = "待评问卷",
                subtitle = data?.let { "本科 ${it.undergraduate.count { q -> !q.finished }} · 研究生 ${it.graduate.count { q -> q.pending }}" } ?: "同步中",
                icon = Icons.Default.Campaign,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("一键评教计划", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("当前学期", data?.currentTerm ?: "--")
                    DataRow("目标评价", data?.autoFillPlan?.targetScoreLabel ?: "--")
                    DataRow("主观题", data?.autoFillPlan?.subjectiveText ?: "--")
                    DataRow("防全优", if (data?.autoFillPlan?.firstObjectiveDowngraded == true) "首个客观题自动降为良好" else "--")
                }
            }
        }
        item { Text("本科评教", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.undergraduate.orEmpty()) { q ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(q.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("教师", q.teacherName)
                    DataRow("类型", q.typeName)
                    DataRow("开放", "${q.startTime} 至 ${q.endTime}")
                    DataRow("状态", if (q.finished) "已评" else "未评")
                }
            }
        }
        item { Text("研究生评教", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.graduate.orEmpty()) { q ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(q.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("教师", q.teacherName)
                    DataRow("班级", q.className)
                    DataRow("单位", q.department)
                    DataRow("状态", if (q.pending) "待评" else "已评/未开放")
                }
            }
        }
        item { Text("题目结构", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(data?.sampleQuestions.orEmpty()) { question ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(question.title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
                    DataRow("题型", question.type.label)
                    DataRow("答案", question.answer.ifBlank { question.subjectiveAnswer.ifBlank { "--" } })
                    if (question.options.isNotEmpty()) DataRow("选项", question.options.joinToString("、") { it.label })
                }
            }
        }
    }
}

@Composable
fun YellowPageScreen() {
    val repository = LocalCampusRepository.current
    var data by remember { mutableStateOf<YellowPageData?>(null) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(repository) {
        data = repository.yellowPage()
    }
    val categories = data?.categories.orEmpty()
    val departments = data?.departments.orEmpty()
    val filtered = departments.filter { department ->
        (selectedCategory == null || department.categoryId == selectedCategory) &&
            (query.isBlank() || department.name.contains(query, ignoreCase = true) || department.phone.contains(query))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("校园黄页", "机构分类、电话搜索、号码拆分和拨号号码提取按 Android 版数据结构迁移。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("检索", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    TextField(value = query, onValueChange = { query = it }, label = "机构或电话")
                    DataRow("更新时间", data?.updateTime?.ifBlank { "--" } ?: "--")
                    DataRow("结果", "${filtered.size}/${departments.size}")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableBlock("全部", selectedCategory == null, Modifier.weight(1f)) { selectedCategory = null }
                categories.take(1).forEach { category ->
                    SelectableBlock(category.name, selectedCategory == category.id, Modifier.weight(1f)) { selectedCategory = category.id }
                }
            }
        }
        items(categories.drop(1).chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    SelectableBlock(category.name, selectedCategory == category.id, Modifier.weight(1f)) { selectedCategory = category.id }
                }
                if (row.size == 1) SelectableBlock("", false, Modifier.weight(1f)) {}
            }
        }
        items(filtered) { department ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(department.name, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("分类", categories.firstOrNull { it.id == department.categoryId }?.name ?: "--")
                    department.phoneItems.forEachIndexed { index, item ->
                        DataRow("电话 ${index + 1}", item)
                        department.dialNumber(item).takeIf { it.isNotBlank() }?.let { DataRow("拨号", it) }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item { ServiceTile("未找到结果", "换个关键词或分类试试", Icons.Default.Phone, MiuixTheme.colorScheme.secondary) {} }
        }
    }
}

@Composable
fun SchoolCourseScreen() {
    val repository = LocalCampusRepository.current
    var keyword by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SchoolCourseResult?>(null) }
    var optionSummary by remember { mutableStateOf(Triple("", "", "")) }
    LaunchedEffect(repository, keyword) {
        result = repository.querySchoolCourses(keyword)
    }
    LaunchedEffect(repository) {
        val options = repository.schoolCourseOptions()
        optionSummary = Triple(
            options.first.joinToString("、") { it.name },
            options.second.joinToString("、") { it.name },
            options.third.joinToString("、") { it.name },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("课程查询", "全校课程查询保留学期、院系、校区、公选课、容量和时间地点字段。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("查询条件", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    TextField(value = keyword, onValueChange = { keyword = it }, label = "课程名或教师")
                    DataRow("学期", optionSummary.first.ifBlank { "--" })
                    DataRow("院系", optionSummary.second.ifBlank { "--" })
                    DataRow("校区", optionSummary.third.ifBlank { "--" })
                }
            }
        }
        item {
            ServiceTile(
                "查询结果",
                "${result?.totalSize ?: 0} 门课程 · 第 ${result?.pageNumber ?: 1}/${result?.totalPages ?: 1} 页",
                Icons.Default.School,
                MiuixTheme.colorScheme.primary,
            ) {}
        }
        items(result?.courses.orEmpty()) { course ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(course.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("课程号", "${course.courseCode}-${course.sectionNumber}")
                    DataRow("教师", course.teacher)
                    DataRow("院系", course.department)
                    DataRow("学分/学时", "${course.credit} / ${course.totalHours}")
                    DataRow("容量", "${course.enrollCount}/${course.capacity} · 剩余 ${course.remaining}")
                    DataRow("地点", course.scheduleLocation)
                    DataRow("类型", if (course.isPublicElective) course.electiveCategory else "普通课程")
                }
            }
        }
    }
}

@Composable
fun TranscriptScreen() {
    val repository = LocalCampusRepository.current
    var state by remember { mutableStateOf<TranscriptWorkflowState?>(null) }
    LaunchedEffect(repository) {
        state = repository.transcriptWorkflow()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("成绩单", "loadForm、联动、预览 PDF、双阶段提交、下载信息被收束为跨端流程状态。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                title = state?.workflowName ?: "成绩单流程",
                subtitle = "workflowId=${state?.workflowId ?: "--"} · ${state?.statusMessage ?: "同步中"}",
                icon = Icons.Default.School,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("表单默认值", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("请求名", state?.defaultRequestName ?: "--")
                    DataRow("日期", state?.defaultDate ?: "--")
                    DataRow("学号", state?.linkage?.studentId ?: "--")
                    DataRow("入学年", state?.linkage?.enrollYear ?: "--")
                    DataRow("模板", state?.linkage?.templatePath ?: "--")
                }
            }
        }
        items(state?.typeOptions.orEmpty()) { option ->
            ServiceTile(option.name, "value=${option.value} · ${if (option.cancelled) "已取消" else "可申请"}", Icons.Default.School, MiuixTheme.colorScheme.secondary) {}
        }
        state?.downloadInfo?.let { info ->
            item {
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("下载信息", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        DataRow("文件", info.filename)
                        DataRow("大小", info.fileSize)
                        DataRow("链接", info.downloadUrl)
                    }
                }
            }
        }
    }
}

@Composable
fun JiaocaiScreen() {
    val repository = LocalCampusRepository.current
    var keyword by remember { mutableStateOf("") }
    var books by remember { mutableStateOf<List<JiaocaiBook>>(emptyList()) }
    LaunchedEffect(repository, keyword) {
        books = repository.jiaocaiBooks(keyword)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("教材", "教材平台搜索保留书目 ID、引擎实例、作者、摘要和本地全文状态。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(value = keyword, onValueChange = { keyword = it }, label = "教材或课程")
                    DataRow("结果", "${books.size} 本")
                }
            }
        }
        items(books) { book ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(book.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("作者", book.author.ifBlank { "--" })
                    DataRow("摘要", book.summary.ifBlank { "--" })
                    DataRow("全文", if (book.hasFullText) "可获取" else "暂无本地全文")
                    DataRow("索引", "${book.id} / ${book.engineInstanceId}")
                }
            }
        }
    }
}

@Composable
fun GmisScreen() {
    val repository = LocalCampusRepository.current
    var schedule by remember { mutableStateOf<List<GmisScheduleItem>>(emptyList()) }
    var scores by remember { mutableStateOf<List<GmisScoreItem>>(emptyList()) }
    LaunchedEffect(repository) {
        schedule = repository.gmisSchedule()
        scores = repository.gmisScores()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("研究生教务", "GMIS 课表、成绩、学期映射和绩点换算按旧 Android 解析结果迁移。", MiuixTheme.colorScheme.primary) }
        item { Text("课表", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(schedule) { course ->
            ServiceTile(
                course.name,
                "周${course.dayOfWeek} 第${course.periodStart}-${course.periodEnd}节 · ${course.classroom} · ${course.weeks}",
                Icons.Default.CalendarMonth,
                MiuixTheme.colorScheme.primary,
            ) {}
        }
        item { Text("成绩", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(scores) { score ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(score.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("类型", score.type)
                    DataRow("成绩", score.score.toString())
                    DataRow("绩点", score.gpa.toString())
                    DataRow("考试日期", score.examDate)
                }
            }
        }
    }
}

@Composable
fun MobileJiaodaScreen() {
    val repository = LocalCampusRepository.current
    var state by remember { mutableStateOf<MobileJiaodaState?>(null) }
    var userInfo by remember { mutableStateOf<YwtbUserInfo?>(null) }
    var weekInfo by remember { mutableStateOf<com.xjtu.toolbox.cmps.data.TeachingWeekInfo?>(null) }
    LaunchedEffect(repository) {
        state = repository.mobileJiaodaState()
        userInfo = repository.ywtbUserInfo()
        weekInfo = repository.teachingWeekInfo()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("移动交大", "WebView 容器、CAS 子服务接力、Cookie 域同步和一网通办用户/教学周接口已抽象到跨端状态。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                title = "启动入口",
                subtitle = state?.launchUrl ?: "同步中",
                icon = Icons.Default.DirectionsBus,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("一网通办身份", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("姓名", userInfo?.userName ?: "--")
                    DataRow("身份", userInfo?.identityTypeName ?: "--")
                    DataRow("组织", userInfo?.organizationName ?: "--")
                    DataRow("UID", userInfo?.userUid ?: "--")
                }
            }
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("教学周", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("周次", weekInfo?.week?.toString() ?: "假期/未知")
                    DataRow("学期", weekInfo?.semesterName ?: "--")
                    DataRow("学期 ID", weekInfo?.semesterId ?: "--")
                    DataRow("开学", weekInfo?.startOfTerm ?: "--")
                }
            }
        }
        item { Text("Cookie 同步域", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(state?.cookieDomains.orEmpty()) { domain ->
            ServiceTile(domain, if (state?.casHandoffEnabled == true) "支持 CAS 接力" else "普通同步域", Icons.Default.DirectionsBus, MiuixTheme.colorScheme.secondary) {}
        }
    }
}

@Composable
fun WebVpnScreen() {
    val repository = LocalCampusRepository.current
    var input by remember { mutableStateOf("https://bkkq.xjtu.edu.cn/") }
    var reversed by remember { mutableStateOf(false) }
    val state = repository.convertWebVpnUrl(input, reversed, webVpnReady = false)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("WebVPN", "校内 URL 与 WebVPN URL 双向转换、就绪状态和常用站点入口统一由共享 codec 管理。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("网址转换", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableBlock("原始到 WebVPN", !reversed, Modifier.weight(1f)) { reversed = false }
                        SelectableBlock("WebVPN 到原始", reversed, Modifier.weight(1f)) { reversed = true }
                    }
                    TextField(value = input, onValueChange = { input = it }, label = if (reversed) "WebVPN 网址" else "校内网址")
                    state.error?.let { DataRow("错误", it) }
                }
            }
        }
        item {
            ServiceTile(
                title = "转换结果",
                subtitle = state.convertedUrl.ifBlank { "等待输入" },
                icon = Icons.Default.VpnKey,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        listOf(
            "教务系统" to "https://jwxt.xjtu.edu.cn/",
            "图书馆主页" to "https://www.lib.xjtu.edu.cn/",
            "一网通办" to "https://ywtb.xjtu.edu.cn/",
            "本科考勤" to "https://bkkq.xjtu.edu.cn/",
        ).forEach { (name, url) ->
            item {
                ServiceTile(name, url, Icons.Default.VpnKey, MiuixTheme.colorScheme.secondary) {
                    input = url
                    reversed = false
                }
            }
        }
    }
}

@Composable
fun CouponScreen() {
    val repository = LocalCampusRepository.current
    var filters by remember { mutableStateOf<List<CouponFilterOption>>(emptyList()) }
    var selected by remember { mutableStateOf<CouponFilterOption?>(null) }
    var page by remember { mutableStateOf<CouponPage?>(null) }
    LaunchedEffect(repository) {
        filters = repository.couponFilters()
        selected = filters.firstOrNull()
    }
    LaunchedEffect(repository, selected) {
        page = repository.couponPage(selected)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("加餐券", "领取状态、可用余额、券类型、详情和激活流程按 Android 版 voucher 接口迁移。", MiuixTheme.colorScheme.primary) }
        items(filters.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { filter ->
                    SelectableBlock(filter.label, selected == filter, Modifier.weight(1f)) { selected = filter }
                }
                if (row.size == 1) SelectableBlock("", false, Modifier.weight(1f)) {}
            }
        }
        item {
            ServiceTile("券列表", "${page?.total ?: 0} 张 · ${selected?.emptyTitle ?: ""}", Icons.Default.School, MiuixTheme.colorScheme.primary) {}
        }
        items(page?.records.orEmpty()) { coupon ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(coupon.voucherName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("类型", coupon.typeName)
                    DataRow("金额", "¥ ${coupon.amountYuan}")
                    DataRow("剩余", "¥ ${coupon.leftAmountYuan} · ${coupon.leftCount} 次")
                    DataRow("有效期", "${coupon.startDate} 至 ${coupon.endDate}")
                    DataRow("卡号", coupon.showCardId)
                }
            }
        }
    }
}

@Composable
fun FitnessScreen() {
    val repository = LocalCampusRepository.current
    var years by remember { mutableStateOf(emptyList<com.xjtu.toolbox.cmps.data.FitnessYear>()) }
    var score by remember { mutableStateOf<FitnessScore?>(null) }
    LaunchedEffect(repository) {
        years = repository.fitnessYears()
        score = repository.fitnessScore(years.firstOrNull { it.checked }?.yearNum)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("体测查询", "学年列表、总分等级、报告状态和男女项目差异已迁移为跨端模型。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                title = score?.totalScore ?: "同步中",
                subtitle = "${score?.studentName ?: "--"} · ${score?.totalGrade ?: "--"} · ${score?.reportStatus ?: "--"}",
                icon = Icons.Default.SportsTennis,
                tint = MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("学生信息", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("学号", score?.studentNumber ?: "--")
                    DataRow("年级", score?.grade ?: "--")
                    DataRow("性别", score?.sex ?: "--")
                    DataRow("可选学年", years.joinToString("、") { it.name })
                }
            }
        }
        items(score?.items.orEmpty()) { item ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(item.name, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("成绩", item.value)
                    DataRow("等级", item.grade)
                    DataRow("状态", item.tone.ifBlank { "--" })
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen() {
    val repository = LocalCampusRepository.current
    var tasks by remember { mutableStateOf<List<DownloadTask>>(emptyList()) }
    LaunchedEffect(repository) {
        tasks = repository.downloadTasks()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("下载管理", "课程回放下载任务、断点续传、速度、状态与文件路径已迁移为跨端任务模型。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                "任务概览",
                "活跃 ${tasks.count { it.isActive }} · 已完成 ${tasks.count { it.status == "completed" }}",
                Icons.Default.School,
                MiuixTheme.colorScheme.primary,
            ) {}
        }
        items(tasks) { task ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(task.activityTitle, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("课程", task.courseName)
                    DataRow("机位/音频", "${task.cameraType} / ${task.audioSource}")
                    DataRow("状态", "${task.statusLabel} · ${(task.progress * 100).roundToInt()}%")
                    DataRow("速度", "${task.downloadSpeed / 1024} KB/s")
                    DataRow("文件", task.filePath)
                    task.errorMessage?.let { DataRow("错误", it) }
                }
            }
        }
    }
}

@Composable
fun BrowserScreen() {
    val repository = LocalCampusRepository.current
    var state by remember { mutableStateOf<BrowserState?>(null) }
    LaunchedEffect(repository) {
        state = repository.browserState("https://www.xjtu.edu.cn/")
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("内置浏览器", "URL 编辑、加载进度、前进后退、下载监听和 Cookie 同步域已迁移为跨端浏览器状态。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                state?.pageTitle ?: "浏览器",
                state?.currentUrl ?: "同步中",
                Icons.Default.VpnKey,
                MiuixTheme.colorScheme.primary,
            ) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("导航状态", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("输入框", state?.editingUrl ?: "--")
                    DataRow("加载", if (state?.isLoading == true) "加载中" else "空闲")
                    DataRow("进度", "${((state?.progress ?: 0f) * 100).roundToInt()}%")
                    DataRow("后退/前进", "${state?.canGoBack == true} / ${state?.canGoForward == true}")
                }
            }
        }
        item { Text("Cookie 同步域", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(state?.cookieDomains.orEmpty()) { domain ->
            ServiceTile(domain, "WebView 与网络层共享登录态", Icons.Default.VpnKey, MiuixTheme.colorScheme.secondary) {}
        }
    }
}

@Composable
fun CacheScreen() {
    val repository = LocalCampusRepository.current
    var entries by remember { mutableStateOf<List<CacheEntryInfo>>(emptyList()) }
    LaunchedEffect(repository) {
        entries = repository.cacheEntries()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("缓存状态", "文件缓存 key、TTL、账号隔离、陈旧回退状态已迁移为跨端缓存 contract。", MiuixTheme.colorScheme.primary) }
        items(entries) { entry ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(entry.key, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("账号域", entry.accountScope)
                    DataRow("TTL", "${entry.ttlMs / 1000}s")
                    DataRow("年龄", entry.ageMs?.let { "${it / 1000}s" } ?: "未缓存")
                    DataRow("陈旧回退", if (entry.staleAvailable) "可用" else "不可用")
                }
            }
        }
    }
}

@Composable
fun JiaoxiaozhiScreen() {
    val repository = LocalCampusRepository.current
    var dashboard by remember { mutableStateOf<JiaoxiaozhiDashboard?>(null) }
    LaunchedEffect(repository) {
        dashboard = repository.jiaoxiaozhiDashboard()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("交晓智", "模型列表、会话、消息、SSE 流式回答、token/cookie 登录态抽象已迁移。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile(
                "状态",
                "登录 ${if (dashboard?.authenticated == true) "已就绪" else "未认证"} · 联网 ${if (dashboard?.networkEnabled == true) "开启" else "关闭"}",
                Icons.Default.SmartToy,
                MiuixTheme.colorScheme.primary,
            ) {}
        }
        item { Text("模型", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(dashboard?.models.orEmpty()) { model ->
            ServiceTile(
                model.label,
                "${model.description} · ${if (model.id == dashboard?.defaultModelId) "默认" else model.id}",
                Icons.Default.SmartToy,
                MiuixTheme.colorScheme.secondary,
            ) {}
        }
        item { Text("会话", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(dashboard?.sessions.orEmpty()) { session ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(session.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("模型", session.modelId)
                    DataRow("锁定", if (session.locked) "是" else "否")
                    DataRow("更新", session.updatedAt.toString())
                }
            }
        }
        item { Text("消息", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(dashboard?.activeConversation?.messages.orEmpty()) { message ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (message.role == "user") "我" else "交晓智", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("内容", message.content)
                    DataRow("时间", message.createdAt.toString())
                }
            }
        }
    }
}

@Composable
fun AgentScreen() {
    val repository = LocalCampusRepository.current
    var dashboard by remember { mutableStateOf<AgentDashboard?>(null) }
    LaunchedEffect(repository) {
        dashboard = repository.agentDashboard()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("屁岱", dashboard?.greeting ?: "AI 助手保留校园语境、账号隔离、可解释工具调用和离线兜底。", MiuixTheme.colorScheme.primary) }
        item { Text("工具", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(dashboard?.tools.orEmpty()) { tool ->
            ServiceTile(tool.name, "${tool.description} · ${if (tool.loginRequired) "需登录" else "免登录"}", Icons.Default.SmartToy, MiuixTheme.colorScheme.primary) {}
        }
        item { Text("结构化卡片", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(dashboard?.widgets.orEmpty()) { widget ->
            when (widget) {
                is AgentWidgetModel.Schedule -> ServiceTile(widget.title, "${widget.courses.size} 节课", Icons.Default.CalendarMonth, MiuixTheme.colorScheme.secondary) {}
                is AgentWidgetModel.Exams -> ServiceTile("考试安排", "${widget.exams.size} 场考试", Icons.Default.School, MiuixTheme.colorScheme.secondary) {}
                is AgentWidgetModel.Rooms -> ServiceTile("空闲教室", "${widget.condition} · ${widget.rooms.size} 间", Icons.Default.EventSeat, MiuixTheme.colorScheme.secondary) {}
                is AgentWidgetModel.Attendance -> ServiceTile("考勤记录", "${widget.records.size} 条", Icons.Default.AssignmentTurnedIn, MiuixTheme.colorScheme.secondary) {}
                is AgentWidgetModel.Grades -> ServiceTile("成绩/GPA", "GPA ${widget.gpa?.toString()?.take(4) ?: "--"} · ${widget.totalCredits} 学分", Icons.Default.School, MiuixTheme.colorScheme.secondary) {}
                is AgentWidgetModel.Card -> ServiceTile("校园卡", widget.summary.balance, Icons.Default.School, MiuixTheme.colorScheme.secondary) {}
            }
        }
    }
}
