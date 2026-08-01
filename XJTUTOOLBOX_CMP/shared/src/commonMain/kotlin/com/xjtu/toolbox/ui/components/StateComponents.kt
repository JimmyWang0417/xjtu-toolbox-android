package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.ui.miuix.*

@Composable
fun LoadingState(
    message: String = "正在加载...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixCircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            MiuixText(
                message,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixText("⚠", fontSize = 36.sp)
            Spacer(Modifier.height(16.dp))
            MiuixText(
                message,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                MiuixTextButton(text = "重试", onClick = onRetry)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiuixText("📭", fontSize = 36.sp)
            Spacer(Modifier.height(16.dp))
            MiuixText(
                title,
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    subtitle,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
