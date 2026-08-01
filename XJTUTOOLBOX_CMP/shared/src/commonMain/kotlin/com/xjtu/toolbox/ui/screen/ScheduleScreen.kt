package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.schedule.CourseItem
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen() {
    val nav = LocalNavigation.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<CourseItem>>(emptyList()) }
    var currentWeek by remember { mutableStateOf(1) }

    // TODO: obtain JwxtLogin from saved credentials and load schedule
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录教务系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "课程表",
                navigationIcon = { BackButton { nav.goBack() } },
                actions = {
                    MiuixTextButton(text = "第${currentWeek}周", onClick = { /* TODO: week selector */ })
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                errorMessage = null; isLoading = true
                scope.launch { isLoading = false; errorMessage = "请先在设置中登录教务系统" }
            }, modifier = Modifier.fillMaxSize().padding(padding))
            else -> {
                // TODO: render weekly schedule grid with CourseItem data
                val weekCourses = courses.filter { it.isInWeek(currentWeek) }
                if (weekCourses.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MiuixText("本周无课程", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.height(8.dp))
                            MiuixText("第${currentWeek}周", fontSize = 14.sp, color = MiuixTheme.colorScheme.outline)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        MiuixText("课表网格视图 - ${weekCourses.size}门课程", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}
