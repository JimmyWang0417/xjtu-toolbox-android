package com.xjtu.toolbox.cmps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.cmps.app.AccountType
import com.xjtu.toolbox.cmps.app.AppRoute
import com.xjtu.toolbox.cmps.app.LocalAppStore
import com.xjtu.toolbox.cmps.app.LocalCampusRepository
import com.xjtu.toolbox.cmps.app.LocalNavigator
import com.xjtu.toolbox.cmps.app.SessionState
import com.xjtu.toolbox.cmps.data.AppSettingsState
import com.xjtu.toolbox.cmps.data.CardTransaction
import com.xjtu.toolbox.cmps.data.CampusCardToolkit
import com.xjtu.toolbox.cmps.data.CampusCardSummary
import com.xjtu.toolbox.cmps.data.CardInsight
import com.xjtu.toolbox.cmps.data.EmptyRoomItem
import com.xjtu.toolbox.cmps.data.EmptyRoomQuery
import com.xjtu.toolbox.cmps.data.PaymentCodeState
import com.xjtu.toolbox.cmps.data.GpaInfo
import com.xjtu.toolbox.cmps.data.ScoreRank
import com.xjtu.toolbox.cmps.data.ScoreRecord
import com.xjtu.toolbox.cmps.ui.components.DataRow
import com.xjtu.toolbox.cmps.ui.components.SelectableBlock
import com.xjtu.toolbox.cmps.ui.components.ServiceTile
import com.xjtu.toolbox.cmps.ui.components.StatusBand
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen() {
    val store = LocalAppStore.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.Undergraduate) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("统一身份认证", "CMPS 版将复用 Android 版 CAS/MFA/WebVPN 登录状态机，并用 Ktor/Darwin/OkHttp 实现双端 Cookie 桥。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(value = username, onValueChange = { username = it }, label = "学号")
                    TextField(value = password, onValueChange = { password = it }, label = "密码")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountType.entries.forEach { type ->
                            SelectableBlock(type.displayName, selected = accountType == type, modifier = Modifier.weight(1f)) {
                                accountType = type
                            }
                        }
                    }
                    Button(onClick = { store.login(username, password, accountType) }, modifier = Modifier.fillMaxWidth()) {
                        Text("登录并同步")
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsScreen(session: SessionState) {
    val store = LocalAppStore.current
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            StatusBand(
                title = "多账号隔离",
                summary = "账号、Cookie、课表、成绩、校园卡、AI 会话都按账号命名空间隔离。",
                color = MiuixTheme.colorScheme.primary
            )
        }
        items(session.accounts) { account ->
            Card(cornerRadius = 18.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(account.displayName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("账号", account.username)
                    DataRow("类型", account.type.displayName)
                    DataRow("状态", if (account.isActive) "当前账号" else "已保存")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { store.activateAccount(account.id) },
                            modifier = Modifier.weight(1f),
                            enabled = !account.isActive,
                        ) {
                            Text("切换")
                        }
                        Button(
                            onClick = { store.removeAccount(account.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("移除")
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { navigator.navigate(AppRoute.Login) }, modifier = Modifier.fillMaxWidth()) {
                Text("添加账号")
            }
        }
        item {
            Button(onClick = { store.logout() }, modifier = Modifier.fillMaxWidth(), enabled = session.isLoggedIn) {
                Text("退出当前账号")
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val repository = LocalCampusRepository.current
    val settings: AppSettingsState = repository.settingsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("设置", "跨端外观、数据同步、隐私许可、更新日志与调试开关。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("外观", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("深色模式", settings.darkMode)
                    DataRow("首页主题", settings.homeTheme)
                    DataRow("底栏风格", settings.navBarStyle)
                    DataRow("默认 Tab", settings.defaultTab)
                    DataRow("常用入口", if (settings.showQuickActions) "显示" else "隐藏")
                }
            }
        }
        item {
            Card(cornerRadius = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("数据与更新", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("网络模式", settings.networkMode)
                    DataRow("自动检查更新", if (settings.autoCheckUpdate) "开启" else "关闭")
                    DataRow("更新通道", settings.updateChannel)
                    DataRow("缓存", settings.cacheSizeText)
                    DataRow("版本", settings.versionText)
                }
            }
        }
    }
}

@Composable
fun EmptyRoomScreen() {
    val repository = LocalCampusRepository.current
    val campuses = listOf("兴庆校区", "雁塔校区", "曲江校区", "创新港校区")
    var campus by remember { mutableStateOf(campuses.first()) }
    val buildings = repository.buildingOptions(campus).map { it.displayName }
    var selectedBuildings by remember { mutableStateOf(setOf<String>()) }
    var rooms by remember { mutableStateOf<List<EmptyRoomItem>>(emptyList()) }
    LaunchedEffect(campus, buildings) {
        if (selectedBuildings.none { it in buildings }) selectedBuildings = setOf(buildings.first())
    }
    LaunchedEffect(campus, selectedBuildings) {
        rooms = repository.emptyRooms(
            EmptyRoomQuery(
                campus = campus,
                buildings = selectedBuildings.ifEmpty { setOf(buildings.first()) },
                week = 1,
                dayOfWeek = 1,
                sections = 1..12,
            )
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("空闲教室", "复刻 Android 版 CDN/直查教务双数据源、楼栋稳定多选和智能筛选。", MiuixTheme.colorScheme.primary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                campuses.take(2).forEach {
                    SelectableBlock(it, campus == it, Modifier.weight(1f)) { campus = it }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                campuses.drop(2).forEach {
                    SelectableBlock(it, campus == it, Modifier.weight(1f)) { campus = it }
                }
            }
        }
        items(buildings.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { building ->
                    SelectableBlock(
                        text = building,
                        selected = building in selectedBuildings,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedBuildings = if (building in selectedBuildings) {
                            (selectedBuildings - building).ifEmpty { selectedBuildings }
                        } else {
                            selectedBuildings + building
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            ServiceTile("查询结果", "$campus · ${selectedBuildings.joinToString("、")} · ${rooms.size} 间可用", Icons.Default.MeetingRoom, MiuixTheme.colorScheme.primary) {}
        }
        items(rooms) { room ->
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(room.roomName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("楼栋", room.buildingName)
                    DataRow("容量", room.capacity?.toString() ?: "未知")
                    DataRow("空闲", room.availableSections)
                    DataRow("来源", room.source.label)
                }
            }
        }
    }
}

@Composable
fun CampusCardScreen() {
    val repository = LocalCampusRepository.current
    var summary by remember { mutableStateOf<CampusCardSummary?>(null) }
    var transactions by remember { mutableStateOf<List<CardTransaction>>(emptyList()) }
    var insight by remember { mutableStateOf<CardInsight?>(null) }
    LaunchedEffect(repository) {
        val result = repository.campusCard()
        summary = result.first
        transactions = result.second
        insight = repository.campusCardInsight()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("校园卡", "余额、流水、付款码、加餐券抵扣将跨端复刻。", MiuixTheme.colorScheme.primary) }
        item {
            ServiceTile("当前余额", summary?.balance ?: "同步中", Icons.Default.CreditCard, MiuixTheme.colorScheme.primary) {}
        }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("卡片状态", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("持卡人", summary?.holder ?: "--")
                    DataRow("账号", summary?.account?.ifBlank { "--" } ?: "--")
                    DataRow("待入账", summary?.pendingAmount?.ifBlank { "--" } ?: "--")
                    DataRow("状态", summary?.status ?: "--")
                }
            }
        }
        insight?.monthlyStats?.firstOrNull()?.let { month ->
            item {
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("${month.month.label()} 消费洞察", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        DataRow("支出", CampusCardToolkit.money(month.totalSpend))
                        DataRow("入账", CampusCardToolkit.money(month.totalIncome))
                        DataRow("日均", CampusCardToolkit.money(month.avgDailySpend))
                        if (month.peakDay.isNotBlank()) DataRow("峰值日", "${month.peakDay} · ${CampusCardToolkit.money(month.peakDayAmount)}")
                    }
                }
            }
        }
        insight?.categorySpend?.takeIf { it.isNotEmpty() }?.let { categories ->
            item {
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("消费分类", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        categories.forEach { (category, amount) ->
                            DataRow(category, CampusCardToolkit.money(amount))
                        }
                    }
                }
            }
        }
        items(transactions) {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(it.title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
                    DataRow(it.location, it.amount)
                    DataRow("时间", it.time)
                    if (it.category.isNotBlank()) DataRow("分类", it.category)
                    if (it.balance.isNotBlank()) DataRow("余额", it.balance)
                }
            }
        }
    }
}

@Composable
fun PaymentCodeScreen() {
    val repository = LocalCampusRepository.current
    var codeState by remember { mutableStateOf<PaymentCodeState?>(null) }
    LaunchedEffect(repository) {
        codeState = repository.paymentCode()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("付款码", "CAS OAuth 认证、JWT 缓存和动态付款码刷新已保留跨端接口。", MiuixTheme.colorScheme.primary) }
        item {
            Card(cornerRadius = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("校园付款码", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("状态", if (codeState?.authenticated == true) "已认证" else "未认证")
                    DataRow("码值", codeState?.code ?: "同步中")
                    DataRow("刷新", codeState?.refreshedAt ?: "--")
                    DataRow("有效期", "${codeState?.expiresInSeconds ?: 0} 秒")
                    codeState?.message?.takeIf { it.isNotBlank() }?.let { DataRow("说明", it) }
                }
            }
        }
    }
}

@Composable
fun ScoreScreen() {
    val repository = LocalCampusRepository.current
    var scores by remember { mutableStateOf<List<ScoreRecord>>(emptyList()) }
    var gpaInfo by remember { mutableStateOf<GpaInfo?>(null) }
    var rank by remember { mutableStateOf<ScoreRank?>(null) }
    LaunchedEffect(repository) {
        scores = repository.scores()
        gpaInfo = repository.gpaInfo()
        rank = repository.scoreRank()
    }
    val gpaRecords = scores.filter { it.selectedForGpa && it.gpa != null }
    val averageGpa = if (gpaRecords.isEmpty()) "--" else {
        val totalCredit = gpaRecords.sumOf { it.credit }
        val weighted = gpaRecords.sumOf { (it.gpa ?: 0.0) * it.credit }
        (weighted / totalCredit).toString().take(4)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBand("成绩", "成绩列表、绩点统计、GPA 勾选和报告导出将与 Android 版保持一致。", MiuixTheme.colorScheme.primary) }
        item { ServiceTile("平均绩点", gpaInfo?.gpa?.toString()?.take(4) ?: averageGpa, Icons.Default.QueryStats, MiuixTheme.colorScheme.primary) {} }
        item {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("成绩概览", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("平均分", gpaInfo?.averageScore?.toString()?.take(5) ?: "--")
                    DataRow("总学分", gpaInfo?.totalCredits?.toString() ?: "--")
                    DataRow("课程数", gpaInfo?.courseCount?.toString() ?: "--")
                    DataRow("击败比例", rank?.defeatPercent?.let { "$it%" } ?: "--")
                }
            }
        }
        rank?.scoreDist?.takeIf { it.isNotEmpty() }?.let { dist ->
            item {
                Card(cornerRadius = 16.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("成绩分布", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                        dist.forEach { DataRow(it.range, "${it.num} 人") }
                    }
                }
            }
        }
        items(scores) {
            Card(cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(it.courseName, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold)
                    DataRow("学分", it.credit.toString())
                    DataRow("成绩", it.score)
                    DataRow("绩点", it.gpa?.toString() ?: "不计")
                }
            }
        }
    }
}
