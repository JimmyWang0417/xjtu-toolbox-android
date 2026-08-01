package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import kotlinx.coroutines.launch

@Composable
fun NsaScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // TODO: obtain NsaApi from saved credentials and load student profile
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "个人信息",
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
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                MiuixText("个人信息加载完成", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
