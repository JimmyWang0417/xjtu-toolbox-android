package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.clickable
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
import com.xjtu.toolbox.lms.LmsCourseSummary
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun LmsScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<LmsCourseSummary>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain LmsApi from saved credentials and load courses
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "网络教学平台",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                errorMessage = null; isLoading = true
                scope.launch { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }
            }, modifier = Modifier.fillMaxSize().padding(padding))
            courses.isEmpty() -> EmptyState(title = "暂无课程", modifier = Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(courses) { course ->
                    MiuixCard(Modifier.fillMaxWidth().clickable { /* TODO: navigate to course detail */ }) {
                        Column(Modifier.padding(16.dp)) {
                            MiuixText(course.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            MiuixText(course.courseCode, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            MiuixText(course.instructorNames, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                            if (course.courseAttributes.studentCount > 0) {
                                MiuixText("${course.courseAttributes.studentCount} 名学生", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }
    }
}
