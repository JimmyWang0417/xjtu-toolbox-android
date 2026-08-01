// Strict transcription of top.yukonga.miuix.kmp.basic.Checkbox
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tencent.kuikly.compose.animation.animateColor
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.animateFloat
import com.tencent.kuikly.compose.animation.core.keyframes
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.animation.core.updateTransition
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.requiredSize
import com.tencent.kuikly.compose.foundation.layout.wrapContentSize
import com.tencent.kuikly.compose.foundation.selection.toggleable
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.drawWithCache
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.StrokeJoin
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.unit.dp

@Composable
fun MiuixCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: MiuixCheckboxColors = MiuixCheckboxDefaults.checkboxColors(),
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val isOn = checked

    val transition = updateTransition(isOn, label = "CheckboxTransition")

    val backgroundColorState by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "BackgroundColor",
    ) {
        if (it) colors.checkedBackgroundColor(enabled) else colors.uncheckedBackgroundColor(enabled)
    }

    val foregroundColorState by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "ForegroundColor",
    ) {
        if (it) colors.checkedForegroundColor(enabled) else colors.uncheckedForegroundColor(enabled)
    }

    val checkAlphaState by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 10, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 150, easing = FastOutSlowInEasing)
            }
        },
        label = "CheckAlpha",
    ) { if (it) 1f else 0f }

    val checkStartTrimState by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckStartTrim",
    ) { if (it) 0.186f else 0.1f }

    val checkEndTrimState by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                keyframes {
                    durationMillis = 300
                    0.85f at 200 using FastOutSlowInEasing
                    0.803f at 300 using FastOutSlowInEasing
                }
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckEndTrim",
    ) { if (it) 0.803f else 0.1f }

    val capsuleShape = remember { RoundedCornerShape(50) }
    val checkPath = remember { Path() }

    val toggleableModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            onValueChange = { v -> currentOnCheckedChange?.invoke(v) },
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = null,
            indication = null,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .requiredSize(26.dp)
            .clip(capsuleShape)
            .drawWithCache {
                val viewportSize = 23f
                val strokeWidth = size.width * 0.09f
                val centerX = size.width / 2
                val centerY = size.height / 2
                val viewportCenterX = viewportSize / 2
                val viewportCenterY = viewportSize / 2

                val startPoint = Offset(
                    centerX + ((5f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((9.4f - viewportCenterY) / viewportSize * size.height),
                )
                val middlePoint = Offset(
                    centerX + ((10.3f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((14.9f - viewportCenterY) / viewportSize * size.height),
                )
                val endPoint = Offset(
                    centerX + ((17.9f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((5.1f - viewportCenterY) / viewportSize * size.height),
                )

                val cache = MiuixCheckmarkCache(startPoint, middlePoint, endPoint, centerX, centerY, strokeWidth)

                val stroke = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    miter = 10.0f,
                )

                onDrawBehind {
                    drawCircle(backgroundColorState)
                    drawMiuixCheckmark(
                        color = foregroundColorState,
                        alpha = checkAlphaState,
                        trimStart = checkStartTrimState,
                        trimEnd = checkEndTrimState,
                        crossCenterGravitation = 0f,
                        path = checkPath,
                        cache = cache,
                        stroke = stroke,
                    )
                }
            }
            .then(toggleableModifier),
    ) {}
}

private data class MiuixCheckmarkCache(
    val startPoint: Offset,
    val middlePoint: Offset,
    val endPoint: Offset,
    val centerX: Float,
    val centerY: Float,
    val strokeWidth: Float,
)

private fun DrawScope.drawMiuixCheckmark(
    color: Color,
    alpha: Float = 1f,
    trimStart: Float,
    trimEnd: Float,
    crossCenterGravitation: Float,
    path: Path,
    cache: MiuixCheckmarkCache,
    stroke: Stroke,
) {
    path.rewind()

    val gravitatedStart = Offset(
        cache.startPoint.x,
        lerp(cache.startPoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedMiddle = Offset(
        lerp(cache.middlePoint.x, cache.centerX, crossCenterGravitation),
        lerp(cache.middlePoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedEnd = Offset(
        cache.endPoint.x,
        lerp(cache.endPoint.y, cache.centerY, crossCenterGravitation),
    )

    val firstSegmentLength = (gravitatedMiddle - gravitatedStart).getDistance()
    val secondSegmentLength = (gravitatedEnd - gravitatedMiddle).getDistance()
    val totalLength = firstSegmentLength + secondSegmentLength

    val startDistance = totalLength * trimStart
    val endDistance = totalLength * trimEnd

    if (startDistance < firstSegmentLength && endDistance > 0) {
        val segStartRatio = (startDistance / firstSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = (endDistance / firstSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segStartRatio
        val startY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segStartRatio
        val endX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segEndRatio
        val endY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segEndRatio

        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
    }

    if (endDistance > firstSegmentLength) {
        val segStartRatio = ((startDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = ((endDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segStartRatio
        val startY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segStartRatio
        val endX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segEndRatio
        val endY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segEndRatio

        if (startDistance < firstSegmentLength) {
            path.lineTo(endX, endY)
        } else {
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
        }
    }

    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = stroke,
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

object MiuixCheckboxDefaults {
    @Composable
    fun checkboxColors(
        checkedForegroundColor: Color = MiuixTheme.colorScheme.onPrimary,
        uncheckedForegroundColor: Color = MiuixTheme.colorScheme.secondary,
        disabledCheckedForegroundColor: Color = MiuixTheme.colorScheme.disabledOnPrimary,
        disabledUncheckedForegroundColor: Color = MiuixTheme.colorScheme.disabledOnPrimary,
        checkedBackgroundColor: Color = MiuixTheme.colorScheme.primary,
        uncheckedBackgroundColor: Color = MiuixTheme.colorScheme.secondary,
        disabledCheckedBackgroundColor: Color = MiuixTheme.colorScheme.disabledPrimary,
        disabledUncheckedBackgroundColor: Color = MiuixTheme.colorScheme.disabledSecondary,
    ): MiuixCheckboxColors = remember(
        checkedForegroundColor, uncheckedForegroundColor, disabledCheckedForegroundColor, disabledUncheckedForegroundColor,
        checkedBackgroundColor, uncheckedBackgroundColor, disabledCheckedBackgroundColor, disabledUncheckedBackgroundColor,
    ) {
        MiuixCheckboxColors(
            checkedForegroundColor, uncheckedForegroundColor, disabledCheckedForegroundColor, disabledUncheckedForegroundColor,
            checkedBackgroundColor, uncheckedBackgroundColor, disabledCheckedBackgroundColor, disabledUncheckedBackgroundColor,
        )
    }
}

@Immutable
data class MiuixCheckboxColors(
    private val checkedForegroundColor: Color,
    private val uncheckedForegroundColor: Color,
    private val disabledCheckedForegroundColor: Color,
    private val disabledUncheckedForegroundColor: Color,
    private val checkedBackgroundColor: Color,
    private val uncheckedBackgroundColor: Color,
    private val disabledCheckedBackgroundColor: Color,
    private val disabledUncheckedBackgroundColor: Color,
) {
    @Stable
    internal fun checkedForegroundColor(enabled: Boolean): Color = if (enabled) checkedForegroundColor else disabledCheckedForegroundColor

    @Stable
    internal fun uncheckedForegroundColor(enabled: Boolean): Color = if (enabled) uncheckedForegroundColor else disabledUncheckedForegroundColor

    @Stable
    internal fun checkedBackgroundColor(enabled: Boolean): Color = if (enabled) checkedBackgroundColor else disabledCheckedBackgroundColor

    @Stable
    internal fun uncheckedBackgroundColor(enabled: Boolean): Color = if (enabled) uncheckedBackgroundColor else disabledUncheckedBackgroundColor
}
