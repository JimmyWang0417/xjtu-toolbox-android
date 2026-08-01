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
import com.xjtu.toolbox.library.AreaStats
import com.xjtu.toolbox.library.MyBookingInfo
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var areaStats by remember { mutableStateOf<Map<String, AreaStats>>(emptyMap()) }
    var myBooking by remember { mutableStateOf<MyBookingInfo?>(null) }
    val scope = rememberCoroutineScope()

    // TODO: obtain LibraryLogin from CredentialStore and load seat data
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "图书馆座位",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = listOf("座位预约", "我的预约"),
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
                    0 -> {
                        if (areaStats.isEmpty()) EmptyState(title = "暂无可用区域", modifier = Modifier.fillMaxSize())
                        else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(areaStats.entries.toList()) { (name, stats) ->
                                MiuixCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            MiuixText(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            MiuixText("可用 ${stats.available} / 总共 ${stats.total}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                                        }
                                        if (stats.available > 0) {
                                            MiuixLinearProgressIndicator(progress = stats.available.toFloat() / stats.total ,
                                                modifier = Modifier.width(60.dp).height(6.dp)
                                            )
                                        } else {
                                            MiuixSurface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF44336).copy(alpha = 0.15f)) {
                                                MiuixText("已满", fontSize = 11.sp, color = Color(0xFFF44336),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (myBooking == null) EmptyState(title = "暂无预约记录", modifier = Modifier.fillMaxSize())
                        else {
                            val b = myBooking!!
                            MiuixCard(Modifier.fillMaxWidth().padding(16.dp),
                                colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(16.dp)) {
                                    MiuixText("当前预约", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(8.dp))
                                    if (b.area != null) MiuixText("区域: ${b.area}", fontSize = 14.sp)
                                    if (b.seatId != null) MiuixText("座位: ${b.seatId}", fontSize = 14.sp)
                                    if (b.statusText != null) MiuixText("状态: ${b.statusText}", fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
