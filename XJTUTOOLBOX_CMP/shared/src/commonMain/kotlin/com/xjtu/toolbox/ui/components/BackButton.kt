package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.ui.miuix.MiuixText

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        MiuixText("<", fontSize = 22.sp)
    }
}
