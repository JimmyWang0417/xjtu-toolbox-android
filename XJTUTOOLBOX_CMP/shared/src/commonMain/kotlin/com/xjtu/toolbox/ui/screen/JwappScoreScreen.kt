package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
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
import com.xjtu.toolbox.jwapp.ScoreItem
import com.xjtu.toolbox.jwapp.TermScore
import com.xjtu.toolbox.schedule.ExamItem
import com.xjtu.toolbox.schedule.TextbookItem
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun JwappScoreScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var termScores by remember { mutableStateOf<List<TermScore>>(emptyList()) }
    var exams by remember { mutableStateOf<List<ExamItem>>(emptyList()) }
    var textbooks by remember { mutableStateOf<List<TextbookItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain JwxtLogin from CredentialStore and load scores/exams/textbooks
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录教务系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "教务成绩",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = listOf("成绩查询", "考试安排", "教材信息"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录教务系统" }
                }, modifier = Modifier.fillMaxSize())
                else -> when (selectedTab) {
                    0 -> ScoreTab(termScores)
                    1 -> ExamTab(exams)
                    2 -> TextbookTab(textbooks)
                }
            }
        }
    }
}

@Composable
private fun ScoreTab(termScores: List<TermScore>) {
    if (termScores.isEmpty()) { EmptyState(title = "暂无成绩数据", modifier = Modifier.fillMaxSize()); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        termScores.forEach { term ->
            item {
                MiuixText(term.termName, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(term.scoreList) { score -> ScoreCard(score) }
        }
    }
}

@Composable
private fun ScoreCard(score: ScoreItem) {
    val passColor = if (score.passFlag) Color(0xFF4CAF50) else Color(0xFFF44336)
    MiuixCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MiuixText(score.courseName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                MiuixText("${score.coursePoint}学分 · ${score.examType}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.End) {
                MiuixText(score.score, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = passColor)
                if (score.gpa != null) MiuixText("GPA ${score.gpa}", fontSize = 11.sp, color = MiuixTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun ExamTab(exams: List<ExamItem>) {
    if (exams.isEmpty()) { EmptyState(title = "暂无考试安排", modifier = Modifier.fillMaxSize()); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(exams) { exam ->
            MiuixCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    MiuixText(exam.courseName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    MiuixText("${exam.examDate} ${exam.examTime}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                    MiuixText(exam.location, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                    MiuixText("座号: ${exam.seatNumber}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun TextbookTab(textbooks: List<TextbookItem>) {
    if (textbooks.isEmpty()) { EmptyState(title = "暂无教材信息", modifier = Modifier.fillMaxSize()); return }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(textbooks) { tb ->
            MiuixCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    MiuixText(tb.textbookName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    MiuixText("课程: ${tb.courseName}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                    MiuixText("${tb.author} · ${tb.publisher}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                    if (tb.price.isNotEmpty()) MiuixText("¥${tb.price}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}
