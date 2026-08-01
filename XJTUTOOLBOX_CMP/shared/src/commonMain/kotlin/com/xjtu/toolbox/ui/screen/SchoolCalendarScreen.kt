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
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.calendar.SchoolCalendarApi
import com.xjtu.toolbox.calendar.SchoolTerm
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import kotlinx.coroutines.launch

@Composable
fun SchoolCalendarScreen() {
    val nav = LocalNavigation.current
    val scope = rememberCoroutineScope()

    var terms by remember { mutableStateOf<List<SchoolTerm>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTermIndex by remember { mutableStateOf(0) }

    fun loadData() {
        isLoading = true; errorMessage = null
        scope.launch {
            try {
                terms = SchoolCalendarApi(null).getTerms()
                val currentIdx = terms.indexOfFirst { it.currentWeek() > 0 }
                selectedTermIndex = if (currentIdx >= 0) currentIdx else 0
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage = "加载失败: ${e.message}"
            } finally { isLoading = false }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "校历",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingState(message = "正在加载校历...", modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = { loadData() }, modifier = Modifier.fillMaxSize())
                else -> {
                    if (terms.size > 1) {
                        MiuixTabRow(
                            tabs = terms.map { "${it.yearName} ${it.termName}" },
                            selectedTabIndex = selectedTermIndex,
                            onTabSelected = { selectedTermIndex = it },
                        )
                    }
                    if (terms.isNotEmpty()) {
                        val term = terms[selectedTermIndex]
                        val curWeek = term.currentWeek()
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                MiuixCard(Modifier.fillMaxWidth(),
                                    colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.padding(16.dp)) {
                                        MiuixText("${term.yearName} ${term.termName}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(Modifier.height(4.dp))
                                        MiuixText("${term.startDate} ~ ${term.endDate}", fontSize = 14.sp, color = MiuixTheme.colorScheme.onPrimaryContainer)
                                        Spacer(Modifier.height(4.dp))
                                        if (curWeek > 0) {
                                            MiuixText("当前第${curWeek}周 / 共${term.totalWeeks}周", fontSize = 13.sp, color = MiuixTheme.colorScheme.onPrimaryContainer)
                                            Spacer(Modifier.height(8.dp))
                                            MiuixLinearProgressIndicator(progress = term.progress() , modifier = Modifier.fillMaxWidth())
                                        } else {
                                            MiuixText("共${term.totalWeeks}周 · ${term.workDays}个工作日", fontSize = 13.sp, color = MiuixTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }
                            if (term.events.isNotEmpty()) {
                                item { MiuixText("学期事件", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
                                items(term.events) { event ->
                                    MiuixCard(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                MiuixText(event.name, fontWeight = FontWeight.Medium)
                                                MiuixText("${event.startDate} ~ ${event.endDate}  (${event.days}天)", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                                if (event.remark.isNotBlank()) MiuixText(event.remark, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                            }
                                        }
                                    }
                                }
                            }
                            items((1..term.totalWeeks).toList()) { weekNum ->
                                val isCurrent = weekNum == curWeek
                                MiuixCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MiuixSurface(modifier = Modifier.size(36.dp),
                                            color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                MiuixText("$weekNum", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                                    color = if (isCurrent) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        MiuixText("第${weekNum}周", fontWeight = FontWeight.Medium)
                                        if (isCurrent) {
                                            Spacer(Modifier.weight(1f))
                                            MiuixText("本周", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
