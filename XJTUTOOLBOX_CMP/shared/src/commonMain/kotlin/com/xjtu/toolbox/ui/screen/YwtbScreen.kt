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
import com.xjtu.toolbox.ywtb.YwtbApi
import com.xjtu.toolbox.ywtb.UserInfo
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun YwtbScreen() {
    val nav = LocalNavigation.current
    var userInfo by remember { mutableStateOf<UserInfo?>(null) }
    var currentTerm by remember { mutableStateOf<String?>(null) }
    var currentWeek by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // TODO: obtain YwtbLogin from saved credentials
    LaunchedEffect(Unit) {
        isLoading = false
        errorMessage = "请先在设置中登录统一身份认证"
    }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "一网通办",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = {
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        isLoading = false
                        errorMessage = "请先在设置中登录统一身份认证"
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                userInfo?.let { info ->
                    MiuixCard(
                        Modifier.fillMaxWidth(),
                        colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            MiuixText("个人信息", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(16.dp))
                            InfoRow("👤", "姓名", info.userName)
                            Spacer(Modifier.height(12.dp))
                            InfoRow("🎓", "学号", info.userUid)
                            Spacer(Modifier.height(12.dp))
                            InfoRow("👤", "身份", info.identityTypeName)
                            Spacer(Modifier.height(12.dp))
                            InfoRow("🏢", "部门", info.organizationName)
                        }
                    }
                }
                if (currentTerm != null) {
                    MiuixCard(
                        Modifier.fillMaxWidth(),
                        colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            MiuixText("学期信息", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(16.dp))
                            val termParts = currentTerm?.split("-")
                            val termDisplay = if (termParts?.size == 3) {
                                "${termParts[0]}-${termParts[1]} 第${if (termParts[2] == "1") "一" else "二"}学期"
                            } else currentTerm ?: ""
                            InfoRow("📅", "当前学期", termDisplay)
                            Spacer(Modifier.height(12.dp))
                            val weekDisplay = if (currentWeek != null) "第${currentWeek}周" else "假期中"
                            InfoRow("📅", "当前教学周", weekDisplay)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiuixText(icon, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        MiuixText(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(80.dp))
        MiuixText(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
