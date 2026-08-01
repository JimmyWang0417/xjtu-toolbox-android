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
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.Routes
import com.xjtu.toolbox.classreplay.Course
import com.xjtu.toolbox.classreplay.LiveActivity
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun ClassScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var activities by remember { mutableStateOf<List<LiveActivity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain ClassLogin from CredentialStore and load courses
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录课程平台（TronClass）" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = selectedCourse?.displayName ?: "课程回放",
                navigationIcon = {
                    BackButton {
                        if (selectedCourse != null) { selectedCourse = null; activities = emptyList() }
                        else nav.goBack()
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                errorMessage = null; isLoading = true
                scope.launch { isLoading = false; errorMessage = "请先在设置中登录课程平台（TronClass）" }
            }, modifier = Modifier.fillMaxSize().padding(padding))
            selectedCourse != null -> {
                if (activities.isEmpty()) EmptyState(title = "暂无回放视频", modifier = Modifier.fillMaxSize().padding(padding))
                else LazyColumn(Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activities) { activity ->
                        MiuixCard(Modifier.fillMaxWidth().clickable {
                            nav.navigate(Routes.VIDEO_PLAYER, mapOf("activityId" to activity.id))
                        }) {
                            Column(Modifier.padding(12.dp)) {
                                MiuixText(activity.title.ifEmpty { "回放 #${activity.id}" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                MiuixText("${activity.startTime} ~ ${activity.endTime}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                                if (activity.isClosed) {
                                    MiuixSurface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                        modifier = Modifier.padding(top = 4.dp)) {
                                        MiuixText("可回放", fontSize = 11.sp, color = Color(0xFF4CAF50),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            courses.isEmpty() -> EmptyState(title = "暂无课程", modifier = Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(courses) { course ->
                    MiuixCard(Modifier.fillMaxWidth().clickable {
                        selectedCourse = course
                        // TODO: load activities for this course
                    }) {
                        Column(Modifier.padding(12.dp)) {
                            MiuixText(course.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            MiuixText(course.courseCode, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                            if (course.instructors.isNotEmpty()) {
                                MiuixText(course.instructors.joinToString(", "), fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                            }
                            MiuixText(course.semesterLabel, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
}
