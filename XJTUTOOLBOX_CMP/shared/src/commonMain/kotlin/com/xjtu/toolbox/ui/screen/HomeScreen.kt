package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.NavigationState
import com.xjtu.toolbox.Routes
import com.xjtu.toolbox.ui.miuix.*

private enum class BottomTab(val label: String, val icon: String, val selectedIcon: String) {
    HOME("首页", "🏠", "🏠"),
    ACADEMIC("教务", "🎓", "🎓"),
    TOOLS("工具", "🔧", "🔧"),
    PROFILE("我的", "👤", "👤")
}

@Composable
fun HomeScreen() {
    val nav = LocalNavigation.current
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

    val homeGreeting = "岱宗盒子"

    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        // ── Miuix TopAppBar (large title) ──
        MiuixTopAppBar(
            title = when (selectedTab) {
                BottomTab.HOME -> "岱宗盒子"
                BottomTab.ACADEMIC -> "教务服务"
                BottomTab.TOOLS -> "实用工具"
                BottomTab.PROFILE -> "我的"
            },
            largeTitle = when (selectedTab) {
                BottomTab.HOME -> homeGreeting
                BottomTab.ACADEMIC -> "教务服务"
                BottomTab.TOOLS -> "实用工具"
                BottomTab.PROFILE -> "我的"
            },
        )

        // ── Tab content ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                BottomTab.HOME -> HomeTab(nav)
                BottomTab.ACADEMIC -> AcademicTab(nav)
                BottomTab.TOOLS -> ToolsTab(nav)
                BottomTab.PROFILE -> ProfileTab(nav)
            }
        }

        // ── Miuix NavigationBar (bottom) ──
        MiuixNavigationBar {
            BottomTab.entries.forEach { tab ->
                MiuixNavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = tab.icon,
                    label = tab.label,
                )
            }
        }
    }
}

// ══════════════════════════════════════════
//  Tab 1 — 首页
// ══════════════════════════════════════════

@Composable
private fun HomeTab(nav: NavigationState) {
    val cGreen = Color(0xFF2E7D32)
    val cOrange = Color(0xFFE65100)
    val cPurple = Color(0xFF7B1FA2)
    val cTeal = Color(0xFF00796B)
    val cIndigo = Color(0xFF283593)
    val cBrown = Color(0xFF4E342E)
    val cCyan = Color(0xFF00838F)
    val cPink = Color(0xFFC2185B)
    val cDeepPurple = Color(0xFF512DA8)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Quick Actions (matches original HomeTab Zone B) ──
        item {
            MiuixText(
                "快捷入口",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction("💳", "校园卡", cGreen) { nav.navigate(Routes.CAMPUS_CARD) }
                QuickAction("📅", "课表", cIndigo) { nav.navigate(Routes.SCHEDULE) }
                QuickAction("📱", "付款码", cTeal) { nav.navigate(Routes.PAYMENT_CODE) }
                QuickAction("🔔", "通知", cOrange) { nav.navigate(Routes.NOTIFICATION) }
            }
        }
        // ── 全部服务 (matches original HomeTab full services grid) ──
        item {
            Spacer(Modifier.height(16.dp))
            MiuixText(
                "全部服务",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("💳", "校园卡", "账单·洞察", cGreen, m) { nav.navigate(Routes.CAMPUS_CARD) } },
            right = { m -> HomeServiceCard("📅", "课表考试", "课表·考试", cIndigo, m) { nav.navigate(Routes.SCHEDULE) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("📊", "成绩查询", "成绩·GPA", cPurple, m) { nav.navigate(Routes.JWAPP_SCORE) } },
            right = { m -> HomeServiceCard("📱", "付款码", "校园支付", cTeal, m) { nav.navigate(Routes.PAYMENT_CODE) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("📋", "考勤查询", "出勤记录", cBrown, m) { nav.navigate(Routes.ATTENDANCE) } },
            right = { m -> HomeServiceCard("📄", "电子成绩单", "下载·签章", cIndigo, m) { nav.navigate(Routes.DZPZ) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("✏️", "本科评教", "评教系统", cPink, m) { nav.navigate(Routes.JUDGE) } },
            right = { m -> HomeServiceCard("📚", "图书馆", "座位预约", cOrange, m) { nav.navigate(Routes.LIBRARY) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("🏫", "空闲教室", "教室查询", cPurple, m) { nav.navigate(Routes.EMPTY_ROOM) } },
            right = { m -> HomeServiceCard("🔔", "通知公告", "校园通知", Color(0xFFBA1A1A), m) { nav.navigate(Routes.NOTIFICATION) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("🏟️", "场馆预订", "运动场地", cCyan, m) { nav.navigate(Routes.VENUE) } },
            right = { m -> HomeServiceCard("🎬", "课程回放", "Class录播", cDeepPurple, m) { nav.navigate(Routes.CLASS_REPLAY) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("🎓", "思源学堂", "课件·作业", cIndigo, m) { nav.navigate(Routes.LMS) } },
            right = { m -> HomeServiceCard("🔍", "课表查询", "全校课程", cCyan, m) { nav.navigate(Routes.SCHOOL_COURSE) } }
        ) }
        item { ServiceCardRow(
            left = { m -> HomeServiceCard("📆", "校历", "学期·假期·周次", cTeal, m) { nav.navigate(Routes.CALENDAR) } },
            right = null
        ) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════
//  Tab 2 — 教务
// ══════════════════════════════════════════

@Composable
private fun AcademicTab(nav: NavigationState) {
    val cIndigo = Color(0xFF283593)
    val cPurple = Color(0xFF7B1FA2)
    val cBrown = Color(0xFF4E342E)
    val cPink = Color(0xFFC2185B)
    val cCyan = Color(0xFF00838F)
    val cTeal = Color(0xFF00796B)
    val cDeepPurple = Color(0xFF512DA8)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item { SectionLabel("本科生") }
        item { ServiceCard("📅", "课表 / 考试", "课表安排·考试时间·教材查询", cIndigo) { nav.navigate(Routes.SCHEDULE) } }
        item { ServiceCard("📊", "成绩查询", "查看成绩 / GPA / 含报表补充", cPurple) { nav.navigate(Routes.JWAPP_SCORE) } }
        item { ServiceCard("📋", "考勤查询", "查看课堂出勤情况", cBrown) { nav.navigate(Routes.ATTENDANCE) } }
        item { ServiceCard("✏️", "本科评教", "一键自动评教", cPink) { nav.navigate(Routes.JUDGE) } }
        item { ServiceCard("🔍", "全校课表查询", "全校课程检索·地点·选课人数", cCyan) { nav.navigate(Routes.SCHOOL_COURSE) } }
        item { ServiceCard("📆", "校历", "学期安排·假期·考试周", cTeal) { nav.navigate(Routes.CALENDAR) } }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("课程学习") }
        item { ServiceCard("🎬", "课程回放", "课程录播·倍速回看", cDeepPurple) { nav.navigate(Routes.CLASS_REPLAY) } }
        item { ServiceCard("🎓", "思源学堂", "课件·作业·课堂回放", cIndigo) { nav.navigate(Routes.LMS) } }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════
//  Tab 3 — 工具
// ══════════════════════════════════════════

@Composable
private fun ToolsTab(nav: NavigationState) {
    val cGreen = Color(0xFF2E7D32)
    val cTeal = Color(0xFF00796B)
    val cOrange = Color(0xFFE65100)
    val cCyan = Color(0xFF00838F)
    val cPurple = Color(0xFF7B1FA2)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item { SectionLabel("校园服务") }
        item { ServiceCard("💳", "校园卡", "余额查询 / 消费账单 / 分析", cGreen) { nav.navigate(Routes.CAMPUS_CARD) } }
        item { ServiceCard("📱", "付款码", "校园支付·点击即用", cTeal) { nav.navigate(Routes.PAYMENT_CODE) } }
        item { ServiceCard("📚", "图书馆座位", "查询·预约座位", cOrange) { nav.navigate(Routes.LIBRARY) } }
        item { ServiceCard("🏟️", "场馆预订", "体育场馆·运动场地预订", cCyan) { nav.navigate(Routes.VENUE) } }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("校园查询") }
        item { ServiceCard("🏫", "空闲教室", "查询各校区各时段空闲教室·无需登录", cPurple) { nav.navigate(Routes.EMPTY_ROOM) } }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("信息获取") }
        item { ServiceCard("🔔", "通知公告", "教务处 / 研究生院通知·无需登录", cOrange) { nav.navigate(Routes.NOTIFICATION) } }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionLabel("更多工具") }
        item { ServiceCard("🌐", "一网通办", "YWTB 服务", cTeal) { nav.navigate(Routes.YWTB) } }
        item { ServiceCard("📄", "电子成绩单", "下载·签章", Color(0xFF283593)) { nav.navigate(Routes.DZPZ) } }
        item { ServiceCard("💡", "GMIS", "水电气查询", cGreen) { nav.navigate(Routes.GMIS) } }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════
//  Tab 4 — 我的
// ══════════════════════════════════════════

@Composable
private fun ProfileTab(nav: NavigationState) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize()) {
        // Hero Header
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.06f))
                    .padding(horizontal = 24.dp, vertical = 36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(72.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        MiuixText("👤", fontSize = 32.sp)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        MiuixText("XJTU 工具箱", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold)
                        MiuixText("登录以使用全部功能", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }

        // 登录表单
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        MiuixText("统一身份认证", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
                        MiuixText("CAS 统一认证登录", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(20.dp))
                        MiuixTextField(
                            value = username,
                            onValueChange = { v -> username = v },
                            label = "学号 / 手机号",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        MiuixTextField(
                            value = password,
                            onValueChange = { v -> password = v },
                            label = "密码",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(20.dp))
                        MiuixButton(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            MiuixText("登录", style = MiuixTheme.textStyles.button)
                        }
                        Spacer(Modifier.height(12.dp))
                        MiuixText(
                            "密码仅用于本地加密后发送至学校 CAS 服务器",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 关于卡片
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                MiuixCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    Column {
                        SettingsRow("💬", "反馈·建议·想法", "发现 Bug？有新点子？来 Issue 告诉我")
                        MiuixHorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow("❤️", "致谢", "XJTUToolBox by yan-xiaoo")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════
//  通用组件
// ══════════════════════════════════════════

@Composable
private fun QuickAction(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            MiuixText(icon, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        MiuixText(label, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeServiceCard(
    icon: String, title: String, subtitle: String,
    iconColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    MiuixCard(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        cornerRadius = MiuixCardDefaults.CornerRadius,
        colors = MiuixCardDefaults.defaultColors(),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                MiuixText(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                MiuixText(
                    title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                MiuixText(
                    subtitle, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ServiceCardRow(
    left: @Composable (Modifier) -> Unit,
    right: (@Composable (Modifier) -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        left(Modifier.weight(1f))
        if (right != null) {
            right(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    MiuixText(
        text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ServiceCard(
    icon: String, title: String, description: String,
    iconColor: Color, onClick: () -> Unit
) {
    MiuixCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(16.dp),
        colors = MiuixCardDefaults.defaultColors(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                MiuixText(icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                MiuixText(title, style = MiuixTheme.textStyles.headline2, fontWeight = FontWeight.Bold)
                MiuixText(
                    description, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            MiuixText("›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}

@Composable
private fun SettingsRow(icon: String, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            MiuixText(icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MiuixText(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
            MiuixText(subtitle, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        MiuixText("›", fontSize = 18.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
    }
}
