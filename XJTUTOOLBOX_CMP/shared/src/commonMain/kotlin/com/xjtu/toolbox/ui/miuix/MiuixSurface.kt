// Strict transcription of top.yukonga.miuix.kmp.basic.Surface
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tencent.kuikly.compose.foundation.BorderStroke
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.RectangleShape
import com.tencent.kuikly.compose.ui.graphics.Shape
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

@Composable
fun MiuixSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MiuixTheme.colorScheme.surface,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val shadowElevationPx = remember(density, shadowElevation) {
        with(density) { shadowElevation.toPx() }
    }
    CompositionLocalProvider(
        LocalMiuixContentColor provides contentColor,
    ) {
        Box(
            modifier = modifier
                .miuixSurface(
                    shape = shape,
                    backgroundColor = color,
                    border = border,
                    shadowElevation = shadowElevationPx,
                ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
fun MiuixSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MiuixTheme.colorScheme.surface,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val density = LocalDensity.current
    val shadowElevationPx = remember(density, shadowElevation) {
        with(density) { shadowElevation.toPx() }
    }
    CompositionLocalProvider(
        LocalMiuixContentColor provides contentColor,
    ) {
        Box(
            modifier = modifier
                .miuixSurface(
                    shape = shape,
                    backgroundColor = color,
                    border = border,
                    shadowElevation = shadowElevationPx,
                )
                .clickable(
                    enabled = enabled,
                    onClick = currentOnClick,
                ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Stable
private fun Modifier.miuixSurface(
    shape: Shape,
    backgroundColor: Color,
    border: BorderStroke?,
    shadowElevation: Float,
) = this.then(
    if (shadowElevation > 0f) {
        Modifier.graphicsLayer(
            shadowElevation = shadowElevation,
            shape = shape,
            clip = false,
        )
    } else {
        Modifier
    },
)
    .then(if (border != null) Modifier.border(border, shape) else Modifier)
    .background(color = backgroundColor, shape = shape)
    .clip(shape)
