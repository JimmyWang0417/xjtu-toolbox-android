// Strict transcription of top.yukonga.miuix.kmp.basic.Switch
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.animateColorAsState
import com.tencent.kuikly.compose.animation.core.animateDpAsState
import com.tencent.kuikly.compose.animation.core.spring
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.gestures.Orientation
import com.tencent.kuikly.compose.foundation.gestures.draggable
import com.tencent.kuikly.compose.foundation.gestures.rememberDraggableState
import com.tencent.kuikly.compose.foundation.interaction.MutableInteractionSource
import com.tencent.kuikly.compose.foundation.interaction.collectIsDraggedAsState
import com.tencent.kuikly.compose.foundation.interaction.collectIsPressedAsState
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.wrapContentSize
import com.tencent.kuikly.compose.foundation.selection.toggleable
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.drawBehind
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun MiuixSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: MiuixSwitchColors = MiuixSwitchDefaults.switchColors(),
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    val capsuleShape = remember { RoundedCornerShape(50) }
    val springSpec = remember { spring<Dp>(dampingRatio = 0.6f, stiffness = 987f) }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }

    val thumbOffsetState = animateDpAsState(
        targetValue = if (checked) {
            if (!enabled) 25.dp
            else if (isPressed || isDragged) 23.75.dp
            else 25.dp
        } else {
            if (!enabled) 4.dp
            else if (isPressed || isDragged) 2.75.dp
            else 4.dp
        } + dragOffset.dp,
        animationSpec = springSpec,
    )

    val thumbSizeState = animateDpAsState(
        targetValue = if (!enabled) 20.dp
        else if (isPressed || isDragged) 22.5.dp
        else 20.dp,
        animationSpec = springSpec,
    )

    val thumbColorState = animateColorAsState(
        if (checked) colors.checkedThumbColor(enabled) else colors.uncheckedThumbColor(enabled),
    )

    val backgroundColorState = animateColorAsState(
        if (checked) colors.checkedTrackColor(enabled) else colors.uncheckedTrackColor(enabled),
        animationSpec = tween(durationMillis = 200),
    )

    val hasCallback = onCheckedChange != null
    val toggleableModifier = if (hasCallback) {
        Modifier.toggleable(
            value = checked,
            onValueChange = { v -> currentOnCheckedChange?.invoke(v) },
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .size(49.dp, 28.dp)
            .clip(capsuleShape)
            .drawBehind {
                drawRect(backgroundColorState.value)
            }
            .then(toggleableModifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffsetState.value.roundToPx(), 0) }
                .size(thumbSizeState.value)
                .drawBehind {
                    drawCircle(color = thumbColorState.value)
                }
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { dragAmount ->
                                rawDragOffset += dragAmount / 2f
                                dragOffset = if (checked) {
                                    rawDragOffset.coerceIn(-21f, 0f)
                                } else {
                                    rawDragOffset.coerceIn(0f, 21f)
                                }
                            },
                            onDragStarted = {
                                rawDragOffset = 0f
                            },
                            onDragStopped = {
                                if (dragOffset.absoluteValue > 21f / 2f) {
                                    currentOnCheckedChange?.invoke(!checked)
                                }
                                dragOffset = 0f
                                rawDragOffset = 0f
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

object MiuixSwitchDefaults {
    @Composable
    fun switchColors(
        checkedThumbColor: Color = MiuixTheme.colorScheme.onPrimary,
        uncheckedThumbColor: Color = MiuixTheme.colorScheme.onSecondary,
        disabledCheckedThumbColor: Color = MiuixTheme.colorScheme.disabledOnPrimary,
        disabledUncheckedThumbColor: Color = MiuixTheme.colorScheme.disabledOnSecondary,
        checkedTrackColor: Color = MiuixTheme.colorScheme.primary,
        uncheckedTrackColor: Color = MiuixTheme.colorScheme.secondary,
        disabledCheckedTrackColor: Color = MiuixTheme.colorScheme.disabledPrimary,
        disabledUncheckedTrackColor: Color = MiuixTheme.colorScheme.disabledSecondary,
    ): MiuixSwitchColors = remember(
        checkedThumbColor, uncheckedThumbColor, disabledCheckedThumbColor, disabledUncheckedThumbColor,
        checkedTrackColor, uncheckedTrackColor, disabledCheckedTrackColor, disabledUncheckedTrackColor,
    ) {
        MiuixSwitchColors(
            checkedThumbColor = checkedThumbColor,
            uncheckedThumbColor = uncheckedThumbColor,
            disabledCheckedThumbColor = disabledCheckedThumbColor,
            disabledUncheckedThumbColor = disabledUncheckedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedTrackColor = uncheckedTrackColor,
            disabledCheckedTrackColor = disabledCheckedTrackColor,
            disabledUncheckedTrackColor = disabledUncheckedTrackColor,
        )
    }
}

@Immutable
data class MiuixSwitchColors(
    private val checkedThumbColor: Color,
    private val uncheckedThumbColor: Color,
    private val disabledCheckedThumbColor: Color,
    private val disabledUncheckedThumbColor: Color,
    private val checkedTrackColor: Color,
    private val uncheckedTrackColor: Color,
    private val disabledCheckedTrackColor: Color,
    private val disabledUncheckedTrackColor: Color,
) {
    @Stable
    internal fun checkedThumbColor(enabled: Boolean): Color = if (enabled) checkedThumbColor else disabledCheckedThumbColor

    @Stable
    internal fun uncheckedThumbColor(enabled: Boolean): Color = if (enabled) uncheckedThumbColor else disabledUncheckedThumbColor

    @Stable
    internal fun checkedTrackColor(enabled: Boolean): Color = if (enabled) checkedTrackColor else disabledCheckedTrackColor

    @Stable
    internal fun uncheckedTrackColor(enabled: Boolean): Color = if (enabled) uncheckedTrackColor else disabledUncheckedTrackColor
}
