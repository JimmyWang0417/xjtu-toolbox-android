// Strict transcription of top.yukonga.miuix.kmp.basic.Card
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.spring
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

enum class MiuixPressFeedbackType { None, Sink, Tilt }

@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    insideMargin: PaddingValues = MiuixCardDefaults.InsideMargin,
    colors: MiuixCardColors = MiuixCardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    MiuixBasicCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(insideMargin),
            content = content,
        )
    }
}

@Composable
fun MiuixCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    insideMargin: PaddingValues = MiuixCardDefaults.InsideMargin,
    colors: MiuixCardColors = MiuixCardDefaults.defaultColors(),
    pressFeedbackType: MiuixPressFeedbackType = MiuixPressFeedbackType.None,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    var isPressed by remember { mutableStateOf(false) }

    val sinkScale by animateFloatAsState(
        targetValue = if (isPressed && pressFeedbackType == MiuixPressFeedbackType.Sink) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
    )

    val feedbackModifier = if (pressFeedbackType != MiuixPressFeedbackType.None) {
        Modifier.graphicsLayer(
            scaleX = sinkScale,
            scaleY = sinkScale,
        ).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent()
                    if (down.changes.any { it.pressed }) {
                        isPressed = true
                        val up = awaitPointerEvent()
                        isPressed = false
                    }
                }
            }
        }
    } else {
        Modifier
    }

    MiuixBasicCard(
        modifier = modifier.then(feedbackModifier),
        cornerRadius = cornerRadius,
        colors = colors,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = currentOnClick)
                .padding(insideMargin),
            content = content,
        )
    }
}

@Composable
private fun MiuixBasicCard(
    modifier: Modifier = Modifier,
    colors: MiuixCardColors = MiuixCardDefaults.defaultColors(),
    cornerRadius: Dp = MiuixCardDefaults.CornerRadius,
    content: @Composable () -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    CompositionLocalProvider(
        LocalMiuixContentColor provides colors.contentColor,
    ) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(color = colors.color, shape = shape),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

object MiuixCardDefaults {
    val CornerRadius = 16.dp
    val InsideMargin = PaddingValues(0.dp)

    @Composable
    fun defaultColors(
        color: Color = MiuixTheme.colorScheme.surfaceContainer,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    ): MiuixCardColors = remember(color, contentColor) {
        MiuixCardColors(color = color, contentColor = contentColor)
    }
}

@Immutable
data class MiuixCardColors(
    val color: Color,
    val contentColor: Color,
)
