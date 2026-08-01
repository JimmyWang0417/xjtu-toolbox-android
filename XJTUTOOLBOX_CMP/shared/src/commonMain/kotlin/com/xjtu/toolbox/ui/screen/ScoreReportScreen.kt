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
import com.xjtu.toolbox.util.formatDecimal
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.score.ReportedGrade
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun ScoreReportScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var allGrades by remember { mutableStateOf<List<ReportedGrade>>(emptyList()) }
    var expandedTerms by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain JwxtLogin from saved credentials and load score report
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录教务系统" }

    val termGroups = remember(allGrades) { allGrades.groupBy { it.term } }
    val totalCredits = allGrades.sumOf { it.coursePoint }
    val weightedGpa = if (totalCredits > 0) {
        allGrades.filter { it.gpa != null }.let { filtered ->
            val fc = filtered.sumOf { it.coursePoint }
            if (fc > 0) filtered.sumOf { it.gpa!! * it.coursePoint } / fc else 0.0
        }
    } else 0.0

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "成绩查询",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                errorMessage = null; isLoading = true
                scope.launch { isLoading = false; errorMessage = "请先在设置中登录教务系统" }
            }, modifier = Modifier.fillMaxSize().padding(padding))
            allGrades.isEmpty() -> EmptyState(title = "暂无成绩数据", modifier = Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    MiuixCard(Modifier.fillMaxWidth(),
                        colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            MiuixText("成绩概览", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiuixText(weightedGpa.formatDecimal(2), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    MiuixText("加权GPA", fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiuixText("${allGrades.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    MiuixText("课程数", fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MiuixText(totalCredits.formatDecimal(1), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    MiuixText("总学分", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                termGroups.forEach { (term, grades) ->
                    item {
                        MiuixCard(
                            Modifier.fillMaxWidth().clickable {
                                expandedTerms = if (term in expandedTerms) expandedTerms - term else expandedTerms + term
                            },
                            colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                MiuixText(term, fontWeight = FontWeight.Bold)
                                MiuixText(if (term in expandedTerms) "▲" else "▼", fontSize = 12.sp)
                            }
                        }
                    }
                    if (term in expandedTerms) {
                        items(grades) { grade ->
                            MiuixCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        MiuixText(grade.courseName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        MiuixText("学分: ${grade.coursePoint}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                    MiuixText(grade.score, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
