// Strict transcription of top.yukonga.miuix.kmp.basic.Divider
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

object MiuixDividerDefaults {
    val Thickness = 0.75.dp
    val DividerColor @Composable get() = MiuixTheme.colorScheme.dividerLine
}

@Composable
fun MiuixHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = MiuixDividerDefaults.Thickness,
    color: Color = MiuixDividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxWidth().height(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(0f, thickness.toPx() / 2),
        end = Offset(size.width, thickness.toPx() / 2),
    )
}

@Composable
fun MiuixVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = MiuixDividerDefaults.Thickness,
    color: Color = MiuixDividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxHeight().width(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(thickness.toPx() / 2, 0f),
        end = Offset(thickness.toPx() / 2, size.height),
    )
}
