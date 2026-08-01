package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.schedule.SchoolCourse
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun SchoolCourseScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<SchoolCourse>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // TODO: obtain JwxtLogin from saved credentials and load school courses
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录教务系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "全校课程",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = "搜索课程名称",
                singleLine = true
            )

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录教务系统" }
                }, modifier = Modifier.fillMaxSize())
                courses.isEmpty() -> EmptyState(title = "暂无课程数据", modifier = Modifier.fillMaxSize())
                else -> {
                    val filtered = if (searchQuery.isBlank()) courses else courses.filter { it.courseName.contains(searchQuery, ignoreCase = true) }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { course ->
                            MiuixCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    MiuixText(course.courseName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    MiuixText("${course.courseCode} · ${course.sectionNumber}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    MiuixText(course.teacher, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                    if (course.scheduleLocation.isNotBlank()) MiuixText("教室: ${course.scheduleLocation}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    MiuixText("${course.credit} 学分 · ${course.totalHours} 学时", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
