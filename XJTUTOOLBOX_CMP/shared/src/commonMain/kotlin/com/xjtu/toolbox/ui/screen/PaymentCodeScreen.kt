package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import kotlinx.coroutines.launch

@Composable
fun PaymentCodeScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var barCodeNumber by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(12) }
    val scope = rememberCoroutineScope()

    // TODO: obtain authenticated HttpClient and PaymentCodeApi, generate QR/barcode
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "付款码",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                isLoading -> LoadingState()
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }
                })
                else -> MiuixCard(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = MiuixCardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixText("付款码", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        MiuixText("向商家出示此码 · 点击刷新", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(24.dp))
                        // TODO: render QR code and barcode images cross-platform
                        MiuixSurface(Modifier.size(200.dp), color = MiuixTheme.colorScheme.surface) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MiuixText("QR Code", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        MiuixSurface(Modifier.fillMaxWidth().height(60.dp), color = MiuixTheme.colorScheme.surface) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MiuixText("Barcode", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (barCodeNumber.isNotEmpty()) {
                            MiuixText(barCodeNumber, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        MiuixText("${countdown}s", fontSize = 11.sp, color = MiuixTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
