// Strict transcription of top.yukonga.miuix.kmp.basic.NavigationBar
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

enum class MiuixNavigationBarDisplayMode {
    IconAndText,
    IconOnly,
    TextOnly,
    IconWithSelectedLabel,
}

enum class MiuixFloatingNavigationBarDisplayMode {
    IconAndText,
    IconOnly,
    TextOnly,
}

val LocalMiuixNavigationBarDisplayMode =
    compositionLocalOf { MiuixNavigationBarDisplayMode.IconAndText }

val LocalMiuixFloatingNavigationBarDisplayMode =
    compositionLocalOf { MiuixFloatingNavigationBarDisplayMode.IconOnly }

@Composable
fun MiuixNavigationBar(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    showDivider: Boolean = true,
    mode: MiuixNavigationBarDisplayMode = MiuixNavigationBarDisplayMode.IconAndText,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color),
    ) {
        if (showDivider) {
            MiuixHorizontalDivider()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalMiuixNavigationBarDisplayMode provides mode) {
                content()
            }
        }
    }
}

@Composable
fun RowScope.MiuixNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var isPressed by remember { mutableStateOf(false) }

    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> if (selected) {
            onSurfaceContainerColor.copy(alpha = 0.5f)
        } else {
            onSurfaceContainerColor.copy(alpha = 0.6f)
        }
        selected -> onSurfaceContainerColor
        else -> onSurfaceContainerColor.copy(0.4f)
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    val mode = LocalMiuixNavigationBarDisplayMode.current

    Column(
        modifier = modifier
            .height(64.dp)
            .weight(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    },
                    onTap = { if (enabled) onClick() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (mode == MiuixNavigationBarDisplayMode.IconAndText || mode == MiuixNavigationBarDisplayMode.IconWithSelectedLabel) {
            Arrangement.Top
        } else {
            Arrangement.Center
        },
    ) {
        when (mode) {
            MiuixNavigationBarDisplayMode.IconAndText -> {
                MiuixText(
                    text = icon,
                    modifier = Modifier.padding(top = 8.dp).size(26.dp),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                MiuixText(
                    text = label,
                    color = tint,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = fontWeight,
                )
            }
            MiuixNavigationBarDisplayMode.TextOnly -> {
                MiuixText(
                    text = label,
                    color = tint,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = fontWeight,
                )
            }
            else -> {
                MiuixText(
                    text = icon,
                    modifier = Modifier.size(26.dp),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun MiuixFloatingNavigationBar(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = 32.dp,
    horizontalOutSidePadding: Dp = 36.dp,
    shadowElevation: Dp = 1.dp,
    showDivider: Boolean = false,
    mode: MiuixFloatingNavigationBarDisplayMode = MiuixFloatingNavigationBarDisplayMode.IconOnly,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalOutSidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 36.dp)
                .then(
                    if (showDivider) {
                        Modifier
                            .background(color = MiuixTheme.colorScheme.dividerLine, shape = shape)
                            .padding(0.75.dp)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (shadowElevation > 0.dp) {
                        Modifier.graphicsLayer(
                            shadowElevation = with(density) { shadowElevation.toPx() },
                            shape = shape,
                            clip = cornerRadius > 0.dp,
                        )
                    } else if (cornerRadius > 0.dp) {
                        Modifier.clip(shape)
                    } else {
                        Modifier
                    },
                )
                .background(color)
                .then(modifier)
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume click */ }
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalMiuixFloatingNavigationBarDisplayMode provides mode) {
                content()
            }
        }
    }
}

@Composable
fun MiuixFloatingNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var isPressed by remember { mutableStateOf(false) }

    val onSurfaceContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> if (selected) {
            onSurfaceContainerColor.copy(alpha = 0.5f)
        } else {
            onSurfaceContainerColor.copy(alpha = 0.6f)
        }
        selected -> onSurfaceContainerColor
        else -> onSurfaceContainerColor.copy(0.4f)
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    val mode = LocalMiuixFloatingNavigationBarDisplayMode.current

    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    },
                    onTap = { if (enabled) onClick() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            MiuixFloatingNavigationBarDisplayMode.IconAndText -> {
                MiuixText(
                    text = icon,
                    modifier = Modifier.padding(top = 6.dp).size(24.dp),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier.padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Invisible text for layout (always bold)
                    MiuixText(
                        text = label,
                        modifier = Modifier.alpha(0f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    // Visible text
                    MiuixText(
                        text = label,
                        color = tint,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = fontWeight,
                    )
                }
            }

            MiuixFloatingNavigationBarDisplayMode.TextOnly -> {
                Box(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = label,
                        modifier = Modifier.alpha(0f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MiuixText(
                        text = label,
                        color = tint,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = fontWeight,
                    )
                }
            }

            MiuixFloatingNavigationBarDisplayMode.IconOnly -> {
                MiuixText(
                    text = icon,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp).size(28.dp),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: String,
)
