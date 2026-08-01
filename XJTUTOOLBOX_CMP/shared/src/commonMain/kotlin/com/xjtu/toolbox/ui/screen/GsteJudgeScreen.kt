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
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.judge.GraduateQuestionnaire
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun GsteJudgeScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var questionnaires by remember { mutableStateOf<List<GraduateQuestionnaire>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // TODO: obtain GsteLogin from CredentialStore and load questionnaires
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录研究生系统" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "研究生评教",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (questionnaires.isNotEmpty()) {
                MiuixButton(onClick = {
                    isSubmitting = true
                    scope.launch { /* TODO: auto submit */ isSubmitting = false }
                }, modifier = Modifier.fillMaxWidth().padding(12.dp), enabled = !isSubmitting) {
                    MiuixText(if (isSubmitting) "提交中..." else "一键好评全部 (${questionnaires.size})")
                }
            }

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录研究生系统" }
                }, modifier = Modifier.fillMaxSize())
                questionnaires.isEmpty() -> EmptyState(title = "暂无待评教课程", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(questionnaires) { q ->
                        MiuixCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                MiuixText(q.KCMC, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(2.dp))
                                MiuixText("教师: ${q.JSXM}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                MiuixText("${q.KKDW} · ${q.TERMNAME}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                                val assessed = q.ASSESSMENT == "1"
                                MiuixSurface(shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(top = 4.dp),
                                    color = if (assessed) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)) {
                                    MiuixText(if (assessed) "已评" else "待评", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (assessed) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
