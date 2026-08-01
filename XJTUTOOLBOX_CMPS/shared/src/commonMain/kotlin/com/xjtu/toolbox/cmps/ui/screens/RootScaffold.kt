package com.xjtu.toolbox.cmps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.cmps.app.AppRoute
import com.xjtu.toolbox.cmps.app.LocalCampusRepository
import com.xjtu.toolbox.cmps.app.LocalNavigator
import com.xjtu.toolbox.cmps.app.RootTab
import com.xjtu.toolbox.cmps.app.ServiceGroup
import com.xjtu.toolbox.cmps.app.SessionState
import com.xjtu.toolbox.cmps.app.serviceCatalog
import com.xjtu.toolbox.cmps.data.ScheduleCourse
import com.xjtu.toolbox.cmps.data.ExamItem
import com.xjtu.toolbox.cmps.data.CustomCourseEntity
import com.xjtu.toolbox.cmps.data.XjtuTime
import com.xjtu.toolbox.cmps.ui.components.DataRow
import com.xjtu.toolbox.cmps.ui.components.SelectableBlock
import com.xjtu.toolbox.cmps.ui.components.ServiceTile
import com.xjtu.toolbox.cmps.ui.components.StatusBand
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RootScaffold(
    currentRoute: AppRoute,
    session: SessionState,
    onBack: () -> Unit,
) {
    val navigator = LocalNavigator.current
    var selectedTab by remember { mutableStateOf(RootTab.Home) }
    val onMain = currentRoute == AppRoute.Main
    Scaffold(
        topBar = {
            TopAppBar(
                title = if (onMain) selectedTab.label else currentRoute.title,
                largeTitle = if (onMain) selectedTab.label else currentRoute.title,
                navigationIcon = {
                    if (!onMain) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (onMain && selectedTab == RootTab.Profile) {
                        IconButton(onClick = { navigator.navigate(AppRoute.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        },
        bottomBar = if (onMain) {
            {
                NavigationBar(mode = NavigationBarDisplayMode.IconAndText) {
                    RootTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = tab.icon(),
                            label = tab.label,
                            badge = when {
                                tab == RootTab.Profile && session.accounts.size > 1 -> {
                                    { Badge { Text(session.accounts.size.toString()) } }
                                }
                                tab == RootTab.Profile && !session.isLoggedIn -> {
                                    { Badge() }
                                }
                                else -> null
                            }
                        )
                    }
                }
            }
        } else {
            {}
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).padding(padding)) {
            if (onMain) {
                when (selectedTab) {
                    RootTab.Home -> HomeTab(session)
                    RootTab.Schedule -> ScheduleTab()
                    RootTab.Tools -> ToolsTab()
                    RootTab.Profile -> ProfileTab(session)
                }
            } else {
                RouteScreen(currentRoute, session)
            }
        }
    }
}

private fun RootTab.icon(): ImageVector = when (this) {
    RootTab.Home -> Icons.Default.Home
    RootTab.Schedule -> Icons.Default.CalendarMonth
    RootTab.Tools -> Icons.Default.Widgets
    RootTab.Profile -> Icons.Default.AccountCircle
}

@Composable
private fun HomeTab(session: SessionState) {
    val navigator = LocalNavigator.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StatusBand(
                title = if (session.isLoggedIn) "晚上好，${session.activeAccount?.displayName}" else "欢迎使用岱宗盒子",
                summary = if (session.campusOnline) "校园网与跨端服务桥接已就绪" else "当前未检测到校园网，校内服务将自动尝试 WebVPN",
                color = MiuixTheme.colorScheme.primary
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceTile("空闲教室", "找一间马上能坐下的教室", Icons.Default.MeetingRoom, Color(0xFF4C8F6A), Modifier.weight(1f)) {
                    navigator.navigate(AppRoute.EmptyRoom)
                }
                ServiceTile("校园卡", "余额、账单、付款码", Icons.Default.CreditCard, Color(0xFF3E7DC4), Modifier.weight(1f)) {
                    navigator.navigate(AppRoute.CampusCard)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceTile("思源", "课程、作业、通知", Icons.Default.School, Color(0xFF8C6AC8), Modifier.weight(1f)) {
                    navigator.navigate(AppRoute.Lms)
                }
                ServiceTile("屁岱", "校园工具助手", Icons.Default.SmartToy, Color(0xFFBD6F45), Modifier.weight(1f), badgeText = "AI") {
                    navigator.navigate(AppRoute.Agent)
                }
            }
        }
        ServiceGroup.entries.forEach { group ->
            item {
                Text(group.title, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
            }
            items(serviceCatalog.filter { it.group == group }) { service ->
                ServiceTile(
                    title = service.label,
                    subtitle = if (service.loginRequired) "需要统一身份认证" else "免登录可用",
                    icon = service.route.icon(),
                    tint = service.route.color(),
                ) { navigator.navigate(service.route) }
            }
        }
    }
}

@Composable
private fun ScheduleTab() {
    val repository = LocalCampusRepository.current
    var courses by remember { mutableStateOf<List<ScheduleCourse>>(emptyList()) }
    var exams by remember { mutableStateOf<List<ExamItem>>(emptyList()) }
    var customCourses by remember { mutableStateOf<List<CustomCourseEntity>>(emptyList()) }
    LaunchedEffect(repository) {
        courses = repository.weeklySchedule()
        exams = repository.examSchedule()
        customCourses = repository.customCourses()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("本周日程", "课表、考试、手动日程与 ICS 导出统一汇总。", MiuixTheme.colorScheme.primary) }
        items((1..7).toList()) { day ->
            val dayCourses = courses.filter { it.dayOfWeek == day }
            Card(cornerRadius = 16.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("周$day", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (dayCourses.isEmpty()) {
                        Text("暂无课程", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        dayCourses.forEach { course ->
                            DataRow(
                                "${course.sections.first}-${course.sections.last}节",
                                "${course.name} · ${course.location} · ${XjtuTime.getTimeRangeStr(course.sections.first, course.sections.last)}",
                            )
                        }
                    }
                }
            }
        }
        item { Text("考试安排", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(exams) { exam ->
            Card(cornerRadius = 16.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(exam.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("时间", "${exam.examDate} ${exam.examTime}")
                    DataRow("地点", exam.location.ifBlank { "--" })
                    DataRow("座位", exam.seatNumber.ifBlank { "--" })
                }
            }
        }
        item { Text("自定义日程", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(customCourses) { custom ->
            ServiceTile(
                title = custom.courseName,
                subtitle = "周${custom.dayOfWeek} 第${custom.startSection}-${custom.endSection}节 · ${custom.location}",
                icon = Icons.Default.CalendarMonth,
                tint = MiuixTheme.colorScheme.secondary,
            ) {}
        }
    }
}

@Composable
private fun ToolsTab() {
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("全部服务", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold) }
        items(serviceCatalog) { service ->
            ServiceTile(service.label, service.group.title, service.route.icon(), service.route.color()) {
                navigator.navigate(service.route)
            }
        }
    }
}

@Composable
private fun ProfileTab(session: SessionState) {
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusBand(
                title = session.activeAccount?.displayName ?: "未登录",
                summary = session.activeAccount?.let { "${it.type.displayName} · ${it.username}" } ?: "登录后同步课表、成绩、校园卡与图书馆服务",
                color = MiuixTheme.colorScheme.primary
            )
        }
        item {
            Card(onClick = { navigator.navigate(AppRoute.Accounts) }, cornerRadius = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("账号管理", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("已保存账号", session.accounts.size.toString())
                    DataRow("当前状态", if (session.isLoggedIn) "已登录" else "未登录")
                }
            }
        }
        item {
            ServiceTile("设置", "外观、隐私、同步与更新日志", Icons.Default.Settings, MiuixTheme.colorScheme.secondary) {
                navigator.navigate(AppRoute.Settings)
            }
        }
    }
}

@Composable
private fun RouteScreen(route: AppRoute, session: SessionState) {
    when (route) {
        AppRoute.Login -> LoginScreen()
        AppRoute.Accounts -> AccountsScreen(session)
        AppRoute.Settings -> SettingsScreen()
        AppRoute.EmptyRoom -> EmptyRoomScreen()
        AppRoute.CampusCard -> CampusCardScreen()
        AppRoute.PaymentCode -> PaymentCodeScreen()
        AppRoute.Coupon -> CouponScreen()
        AppRoute.Score -> ScoreScreen()
        AppRoute.Transcript -> TranscriptScreen()
        AppRoute.Gmis -> GmisScreen()
        AppRoute.Attendance -> AttendanceScreen(postgraduate = false)
        AppRoute.PostgraduateAttendance -> AttendanceScreen(postgraduate = true)
        AppRoute.Lms -> LmsScreen()
        AppRoute.ClassReplay -> ClassReplayScreen()
        AppRoute.Library -> LibraryScreen()
        AppRoute.Venue -> VenueScreen()
        AppRoute.Fitness -> FitnessScreen()
        AppRoute.SchoolCourse -> SchoolCourseScreen()
        AppRoute.Judge -> JudgeScreen()
        AppRoute.Jiaocai -> JiaocaiScreen()
        AppRoute.Notification -> NotificationScreen()
        AppRoute.YellowPage -> YellowPageScreen()
        AppRoute.SchoolCalendar -> SchoolCalendarScreen()
        AppRoute.WebVpn -> WebVpnScreen()
        AppRoute.MobileJiaoda -> MobileJiaodaScreen()
        AppRoute.Browser -> BrowserScreen()
        AppRoute.Downloads -> DownloadsScreen()
        AppRoute.Cache -> CacheScreen()
        AppRoute.Jiaoxiaozhi -> JiaoxiaozhiScreen()
        AppRoute.Agent -> AgentScreen()
        AppRoute.Main -> Unit
        else -> PlaceholderFeatureScreen(route, "${route.title} 正在迁移 Android 版完整业务逻辑。")
    }
}

private fun AppRoute.icon(): ImageVector = when (this) {
    AppRoute.EmptyRoom -> Icons.Default.MeetingRoom
    AppRoute.CampusCard -> Icons.Default.CreditCard
    AppRoute.PaymentCode -> Icons.Default.QrCode
    AppRoute.Lms -> Icons.Default.School
    AppRoute.ClassReplay -> Icons.Default.VideoLibrary
    AppRoute.SchoolCourse -> Icons.AutoMirrored.Filled.MenuBook
    AppRoute.Attendance -> Icons.Default.AssignmentTurnedIn
    AppRoute.PostgraduateAttendance -> Icons.AutoMirrored.Filled.FactCheck
    AppRoute.Score -> Icons.Default.HistoryEdu
    AppRoute.Gmis -> Icons.Default.HistoryEdu
    AppRoute.Transcript -> Icons.AutoMirrored.Filled.LibraryBooks
    AppRoute.Library -> Icons.Default.EventSeat
    AppRoute.Venue -> Icons.Default.SportsTennis
    AppRoute.Fitness -> Icons.Default.FitnessCenter
    AppRoute.Judge -> Icons.Default.Campaign
    AppRoute.Jiaocai -> Icons.AutoMirrored.Filled.MenuBook
    AppRoute.Notification -> Icons.Default.Campaign
    AppRoute.YellowPage -> Icons.Default.Phone
    AppRoute.SchoolCalendar -> Icons.Default.CalendarMonth
    AppRoute.WebVpn -> Icons.Default.VpnKey
    AppRoute.MobileJiaoda -> Icons.Default.DirectionsBus
    AppRoute.Browser -> Icons.Default.VpnKey
    AppRoute.Downloads -> Icons.Default.VideoLibrary
    AppRoute.Cache -> Icons.Default.Widgets
    AppRoute.Jiaoxiaozhi -> Icons.Default.SmartToy
    AppRoute.Agent -> Icons.Default.SmartToy
    else -> Icons.Default.Widgets
}

private fun AppRoute.color(): Color = when (this) {
    AppRoute.EmptyRoom -> Color(0xFF4C8F6A)
    AppRoute.CampusCard, AppRoute.PaymentCode -> Color(0xFF3E7DC4)
    AppRoute.Score, AppRoute.Transcript -> Color(0xFF8868C6)
    AppRoute.Library -> Color(0xFF6D7E3F)
    AppRoute.Agent, AppRoute.Jiaoxiaozhi -> Color(0xFFBD6F45)
    else -> Color(0xFF5F7F9F)
}

@Composable
private fun PlaceholderFeatureScreen(route: AppRoute, summary: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand(route.title, summary, MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 18.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("迁移清单", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    listOf("网络接口", "缓存与账号隔离", "状态视图", "操作反馈", "Android/iOS 平台桥").forEach {
                        SelectableBlock(it, selected = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {}
                    }
                }
            }
        }
    }
}
