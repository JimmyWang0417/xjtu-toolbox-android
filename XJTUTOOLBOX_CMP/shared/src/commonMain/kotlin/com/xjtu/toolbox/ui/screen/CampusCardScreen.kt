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
import com.xjtu.toolbox.card.CardInfo
import com.xjtu.toolbox.card.Transaction
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun CampusCardScreen() {
    val nav = LocalNavigation.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var cardInfo by remember { mutableStateOf<CardInfo?>(null) }
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // TODO: obtain CampusCardApi from CredentialStore and load balance + transactions
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "校园卡",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }
                }, modifier = Modifier.fillMaxSize())
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // 余额卡片
                        item {
                            MiuixCard(Modifier.fillMaxWidth(),
                                colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(24.dp)) {
                                    MiuixText("校园卡余额", fontSize = 14.sp, color = MiuixTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.height(8.dp))
                                    MiuixText(
                                        cardInfo?.let { "¥${it.balance}" } ?: "-- 元",
                                        fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (cardInfo != null) {
                                        Spacer(Modifier.height(4.dp))
                                        MiuixText("${cardInfo!!.name} · ${cardInfo!!.account}",
                                            fontSize = 12.sp, color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }

                        // Tab
                        item {
                            MiuixTabRow(
                                tabs = listOf("消费记录", "统计"),
                                selectedTabIndex = selectedTab,
                                onTabSelected = { selectedTab = it },
                            )
                        }

                        when (selectedTab) {
                            0 -> {
                                if (transactions.isEmpty()) {
                                    item { EmptyState(title = "暂无消费记录", modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) }
                                } else {
                                    items(transactions) { tx -> TransactionCard(tx) }
                                }
                            }
                            1 -> {
                                item {
                                    MiuixCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        Column(Modifier.padding(16.dp)) {
                                            MiuixText("消费统计", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Spacer(Modifier.height(8.dp))
                                            MiuixText("登录后将显示月度消费统计和用餐分析", fontSize = 13.sp, color = MiuixTheme.colorScheme.outline)
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

@Composable
private fun TransactionCard(tx: Transaction) {
    MiuixCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MiuixText(tx.merchant, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                MiuixText(tx.time, fontSize = 11.sp, color = MiuixTheme.colorScheme.outline)
            }
            MiuixText(
                "${if (tx.amount >= 0) "-" else "+"}¥${kotlin.math.abs(tx.amount)}",
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (tx.amount >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
            )
        }
    }
}
