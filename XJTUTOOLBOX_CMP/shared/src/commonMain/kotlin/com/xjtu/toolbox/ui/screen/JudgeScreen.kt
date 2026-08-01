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
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.judge.Questionnaire
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun JudgeScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var unfinished by remember { mutableStateOf<List<Questionnaire>>(emptyList()) }
    var finished by remember { mutableStateOf<List<Questionnaire>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // TODO: obtain JwxtLogin from CredentialStore and load questionnaires
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录教务系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "教学评价",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = listOf("未评 (${unfinished.size})", "已评 (${finished.size})"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            if (unfinished.isNotEmpty() && selectedTab == 0) {
                MiuixButton(onClick = {
                    // TODO: auto-fill all questionnaires
                    isSubmitting = true
                    scope.launch { isSubmitting = false }
                }, modifier = Modifier.fillMaxWidth().padding(12.dp), enabled = !isSubmitting) {
                    MiuixText(if (isSubmitting) "提交中..." else "一键好评全部 (${unfinished.size})")
                }
            }

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录教务系统" }
                }, modifier = Modifier.fillMaxSize())
                else -> {
                    val list = if (selectedTab == 0) unfinished else finished
                    if (list.isEmpty()) EmptyState(
                        title = if (selectedTab == 0) "暂无待评教课程" else "暂无已评课程",
                        modifier = Modifier.fillMaxSize()
                    )
                    else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list) { q -> QuestionnaireCard(q, isFinished = selectedTab == 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionnaireCard(q: Questionnaire, isFinished: Boolean) {
    MiuixCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MiuixText(q.KCM, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                MiuixText("${q.WJMC} · ${q.BPJS}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                MiuixText("${q.KSSJ} ~ ${q.JSSJ}", fontSize = 11.sp, color = MiuixTheme.colorScheme.outline)
            }
            MiuixSurface(
                shape = RoundedCornerShape(6.dp),
                color = if (isFinished) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
            ) {
                MiuixText(
                    if (isFinished) "已评" else "待评",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (isFinished) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
