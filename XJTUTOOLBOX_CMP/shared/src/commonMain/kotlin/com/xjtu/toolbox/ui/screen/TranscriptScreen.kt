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
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.dzpz.TranscriptApi
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import kotlinx.coroutines.launch

@Composable
fun TranscriptScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // TODO: obtain DzpzLogin from CredentialStore
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    val workflowTypes = remember { TranscriptApi.WORKFLOW_MAP.keys.toList() }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "电子凭证",
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
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MiuixText("选择身份类型", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    MiuixText("请选择您的身份类型以申请电子凭证", fontSize = 13.sp, color = MiuixTheme.colorScheme.outline)
                }
                items(workflowTypes) { type ->
                    MiuixCard(
                        Modifier.fillMaxWidth().clickable { selectedType = type },
                        colors = MiuixCardDefaults.defaultColors(
                            color = if (selectedType == type) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiuixText(type, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (selectedType == type) {
                                MiuixText("已选", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (selectedType != null) {
                    item {
                        MiuixButton(
                            onClick = {
                                isSubmitting = true
                                // TODO: submit transcript request using TranscriptApi
                                scope.launch { isSubmitting = false; resultMsg = "申请已提交，请在电子凭证系统查看" }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSubmitting
                        ) {
                            MiuixText(if (isSubmitting) "提交中..." else "申请 $selectedType 电子凭证")
                        }
                    }
                }
                if (resultMsg != null) {
                    item {
                        MiuixCard(Modifier.fillMaxWidth(),
                            colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.tertiaryContainer)) {
                            MiuixText(resultMsg!!, Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}
