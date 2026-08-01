package com.xjtu.toolbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 页面层级用色，解决"灰卡片踩灰背景"。
 *
 * miuix 的 Scaffold 默认背景是 `surface`（浅 #F7F7F7 / 深 纯黑），所以：
 * - 卡片**不能**用 `surface`——那正是背景色，卡片会整个消失（考勤流水卡就是这么没的）；
 * - 也不该用 `secondaryContainer.copy(alpha=...)`：浅色下 #F0F0F0 半透明压在 #F7F7F7 上
 *   几乎无差，深色下 #434343 半透明又发灰，这是思源学堂"劣质网页感"的来源。
 *
 * 正确的层级是：背景 `surface` → 卡片 [AppCardColor] → 卡内嵌套块 [AppInsetColor]。
 * `surfaceVariant` 在浅色是纯白、深色是 #242424，与背景两端都拉得开。
 */
val AppCardColor: Color
    @Composable get() = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceVariant

/** 卡片内部再嵌一层时用（比卡片略重），例如统计块、内嵌列表行。 */
val AppInsetColor: Color
    @Composable get() = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)

@Composable
fun AmbientGlow(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .blur(size / 3)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.42f), Color.Transparent),
                ),
            ),
    )
}

@Composable
fun ExpressiveIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    iconSize: Dp = 27.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .squircleSurface(
                color = color.copy(alpha = 0.16f),
                cornerRadius = size * 0.31f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.7f)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                    ),
                ),
        )
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ExpressivePanel(
    modifier: Modifier = Modifier,
    accent: Color,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .squircleSurface(
                color = accent.copy(alpha = 0.08f),
                cornerRadius = cornerRadius,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = SinkFeedback(),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}
