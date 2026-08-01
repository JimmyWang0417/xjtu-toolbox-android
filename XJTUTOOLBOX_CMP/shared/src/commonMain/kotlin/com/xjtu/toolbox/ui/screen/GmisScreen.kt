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
import com.xjtu.toolbox.util.formatDecimal
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.gmis.GmisScoreItem
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun GmisScreen() {
    val nav = LocalNavigation.current
    var selectedTab by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scores by remember { mutableStateOf<List<GmisScoreItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // TODO: obtain GmisLogin from saved credentials and load data
    LaunchedEffect(Unit) {
        isLoading = false
        errorMessage = "请先在设置中登录统一身份认证"
    }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "研究生 · 课表/成绩",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = listOf("课表", "成绩"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }
                }, modifier = Modifier.fillMaxSize())
                else -> when (selectedTab) {
                    0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MiuixText("课表视图 - 加载后显示", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    1 -> GmisScoreList(scores)
                }
            }
        }
    }
}

@Composable
private fun GmisScoreList(scores: List<GmisScoreItem>) {
    if (scores.isEmpty()) {
        EmptyState(title = "暂无成绩数据", modifier = Modifier.fillMaxSize())
        return
    }
    val totalCredits = scores.sumOf { it.coursePoint }
    val weightedGpa = if (totalCredits > 0) scores.sumOf { it.coursePoint * it.gpa } / totalCredits else 0.0

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            MiuixText("${scores.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
        items(scores) { item ->
            val scoreColor = when {
                item.score >= 90 -> MiuixTheme.colorScheme.primary
                item.score >= 75 -> Color(0xFF558B2F)
                item.score >= 60 -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                else -> MiuixTheme.colorScheme.error
            }
            MiuixCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MiuixText(item.courseName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        MiuixText("学分: ${item.coursePoint}  GPA: ${item.gpa}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        if (item.examDate.isNotEmpty()) MiuixText(item.examDate, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    MiuixText(item.score.formatDecimal(0), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                }
            }
        }
    }
}
