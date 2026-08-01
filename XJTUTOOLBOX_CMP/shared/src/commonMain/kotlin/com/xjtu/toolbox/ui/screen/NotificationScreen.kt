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
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.Routes
import com.xjtu.toolbox.notification.Notification
import com.xjtu.toolbox.notification.NotificationApi
import com.xjtu.toolbox.notification.NotificationSource
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun NotificationScreen() {
    val nav = LocalNavigation.current
    val api = remember { NotificationApi() }
    val scope = rememberCoroutineScope()

    var selectedSource by remember { mutableStateOf(NotificationSource.JWC) }
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        isLoading = true; errorMessage = null
        scope.launch {
            try {
                notifications = api.getNotifications(selectedSource, page = 1)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage = "加载失败: ${e.message}"
            } finally { isLoading = false }
        }
    }

    LaunchedEffect(selectedSource) { loadData() }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "通知公告",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MiuixTabRow(
                tabs = NotificationSource.entries.map { it.displayName },
                selectedTabIndex = NotificationSource.entries.indexOf(selectedSource),
                onTabSelected = { selectedSource = NotificationSource.entries[it] },
            )

            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = { loadData() }, modifier = Modifier.fillMaxSize())
                notifications.isEmpty() -> EmptyState(title = "暂无通知", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications) { item ->
                        MiuixCard(
                            Modifier.fillMaxWidth().clickable {
                                nav.navigate(Routes.BROWSER, mapOf("url" to item.link, "title" to item.title))
                            }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                MiuixText(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    MiuixText(item.source.displayName, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                    MiuixText(item.date, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
