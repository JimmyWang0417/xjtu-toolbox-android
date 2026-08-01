package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.attendance.AttendanceWaterRecord
import com.xjtu.toolbox.attendance.CourseAttendanceStat
import com.xjtu.toolbox.attendance.WaterType
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var records by remember { mutableStateOf<List<AttendanceWaterRecord>>(emptyList()) }
    var courseStats by remember { mutableStateOf<List<CourseAttendanceStat>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain AttendanceLogin from CredentialStore and load data
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录考勤系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "考勤查询",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = listOf("考勤记录", "课程统计"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录考勤系统" }
                }, modifier = Modifier.fillMaxSize())
                else -> when (selectedTab) {
                    0 -> {
                        if (records.isEmpty()) EmptyState(title = "暂无考勤记录", modifier = Modifier.fillMaxSize())
                        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(records) { record -> AttendanceRecordCard(record) }
                        }
                    }
                    1 -> {
                        if (courseStats.isEmpty()) EmptyState(title = "暂无课程统计", modifier = Modifier.fillMaxSize())
                        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(courseStats) { stat -> CourseStatCard(stat) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecordCard(record: AttendanceWaterRecord) {
    val statusColor = when (record.status) {
        WaterType.NORMAL -> Color(0xFF4CAF50)
        WaterType.LATE -> Color(0xFFFF9800)
        WaterType.ABSENCE -> Color(0xFFF44336)
        WaterType.LEAVE -> Color(0xFF2196F3)
    }
    MiuixCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MiuixText(record.courseName.ifEmpty { "未知课程" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                MiuixText("${record.date}  第${record.startTime}-${record.endTime}节", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                MiuixText(record.location, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
            }
            MiuixSurface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                MiuixText(record.status.displayName, fontSize = 12.sp, color = statusColor,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun CourseStatCard(stat: CourseAttendanceStat) {
    MiuixCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            MiuixText(stat.subjectName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatChip("正常", stat.normalCount, Color(0xFF4CAF50))
                StatChip("迟到", stat.lateCount, Color(0xFFFF9800))
                StatChip("缺勤", stat.absenceCount, Color(0xFFF44336))
                StatChip("请假", stat.leaveCount, Color(0xFF2196F3))
            }
            if (stat.total > 0) {
                Spacer(Modifier.height(4.dp))
                MiuixLinearProgressIndicator(progress = stat.actualCount.toFloat() / stat.total ,
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        MiuixText("$label $count", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
