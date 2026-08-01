package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.ui.components.BackButton

@Composable
fun BrowserScreen() {
    val nav = LocalNavigation.current
    val url = nav.routeArgs["url"] as? String ?: ""
    val title = nav.routeArgs["title"] as? String ?: "浏览器"

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = title,
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (url.isNotEmpty()) {
                // TODO: integrate platform-specific WebView (Android WebView / iOS WKWebView / HarmonyOS Web)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixText("WebView 需要平台桥接实现", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(8.dp))
                        MiuixText(url, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MiuixText("未指定 URL", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}
