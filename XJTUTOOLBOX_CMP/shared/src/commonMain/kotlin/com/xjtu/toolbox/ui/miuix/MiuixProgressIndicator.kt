// Strict transcription of top.yukonga.miuix.kmp.basic.ProgressIndicator
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.RepeatMode
import com.tencent.kuikly.compose.animation.core.animateFloat
import com.tencent.kuikly.compose.animation.core.infiniteRepeatable
import com.tencent.kuikly.compose.animation.core.keyframes
import com.tencent.kuikly.compose.animation.core.rememberInfiniteTransition
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.CornerRadius
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.DrawScope
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

@Composable
fun MiuixLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: MiuixProgressIndicatorColors = MiuixProgressIndicatorDefaults.progressIndicatorColors(),
    height: Dp = MiuixProgressIndicatorDefaults.DefaultLinearProgressIndicatorHeight,
) {
    val currentBackgroundColor = colors.backgroundColor()
    val currentForegroundColor = colors.foregroundColor(true)

    if (progress == null) {
        val transition = rememberInfiniteTransition()
        val animatedValue by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )

        Canvas(
            modifier = modifier.fillMaxWidth().height(height),
        ) {
            drawRoundRect(
                color = currentBackgroundColor,
                size = size,
                cornerRadius = CornerRadius(size.height / 2),
            )
            for (i in 0 until 3) {
                drawIndeterminateSegment(animatedValue, i, currentForegroundColor)
            }
        }
    } else {
        val progressValue = progress.coerceIn(0f, 1f)

        Canvas(
            modifier = modifier.fillMaxWidth().height(height),
        ) {
            val cornerRadius = size.height / 2
            drawRoundRect(
                color = currentBackgroundColor,
                size = size,
                cornerRadius = CornerRadius(cornerRadius),
            )
            val minWidth = cornerRadius * 2
            val progressWidth = minWidth + (size.width - minWidth) * progressValue
            drawRoundRect(
                color = currentForegroundColor,
                size = Size(progressWidth, size.height),
                cornerRadius = CornerRadius(cornerRadius),
            )
        }
    }
}

@Composable
fun MiuixCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: MiuixProgressIndicatorColors = MiuixProgressIndicatorDefaults.progressIndicatorColors(),
    strokeWidth: Dp = MiuixProgressIndicatorDefaults.DefaultCircularProgressIndicatorStrokeWidth,
    size: Dp = MiuixProgressIndicatorDefaults.DefaultCircularProgressIndicatorSize,
) {
    val currentBackgroundColor = colors.backgroundColor()
    val currentForegroundColor = colors.foregroundColor(true)

    if (progress == null) {
        val transition = rememberInfiniteTransition()

        val rotationAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
        val sweepAnim by transition.animateFloat(
            initialValue = 30f,
            targetValue = 120f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1600
                    120f at 800 using LinearEasing
                    30f at 1600 using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
            ),
        )

        Canvas(
            modifier = modifier.size(size),
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.toPx() - strokeWidthPx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)

            drawCircle(
                color = currentBackgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidthPx),
            )

            drawArc(
                color = currentForegroundColor,
                startAngle = rotationAnim,
                sweepAngle = sweepAnim,
                useCenter = false,
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
    } else {
        val progressValue = progress.coerceIn(0f, 1f)

        Canvas(
            modifier = modifier.size(size),
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.toPx() - strokeWidthPx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)

            drawCircle(
                color = currentBackgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidthPx),
            )

            val minSweepAngle = 0.1f
            val sweepAngle = minSweepAngle + (360f - minSweepAngle) * progressValue

            drawArc(
                color = currentForegroundColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                size = Size(2 * radius, 2 * radius),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawIndeterminateSegment(
    animatedValue: Float,
    segmentIndex: Int,
    color: Color,
) {
    val position = animatedValue - segmentIndex * (0.45f + 0.55f)
    val adjustedPos = (position % 1f + 1f) % 1f
    val cornerRadius = CornerRadius(size.height / 2)

    if (adjustedPos < 1f - 0.45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * 0.45f, size.height),
            cornerRadius = cornerRadius,
        )
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * (1f - adjustedPos), size.height),
            cornerRadius = cornerRadius,
        )
        val remainingWidth = adjustedPos + 0.45f - 1f
        if (remainingWidth > 0) {
            drawRoundRect(
                color = color,
                size = Size(size.width * remainingWidth, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}

object MiuixProgressIndicatorDefaults {
    val DefaultLinearProgressIndicatorHeight = 6.dp
    val DefaultCircularProgressIndicatorStrokeWidth = 4.dp
    val DefaultCircularProgressIndicatorSize = 30.dp

    @Composable
    fun progressIndicatorColors(
        foregroundColor: Color = MiuixTheme.colorScheme.primary,
        disabledForegroundColor: Color = MiuixTheme.colorScheme.disabledPrimarySlider,
        backgroundColor: Color = MiuixTheme.colorScheme.secondaryContainer,
    ): MiuixProgressIndicatorColors = remember(foregroundColor, disabledForegroundColor, backgroundColor) {
        MiuixProgressIndicatorColors(
            foregroundColor = foregroundColor,
            disabledForegroundColor = disabledForegroundColor,
            backgroundColor = backgroundColor,
        )
    }
}

@Immutable
data class MiuixProgressIndicatorColors(
    private val foregroundColor: Color,
    private val disabledForegroundColor: Color,
    private val backgroundColor: Color,
) {
    @Stable
    internal fun foregroundColor(enabled: Boolean): Color = if (enabled) foregroundColor else disabledForegroundColor

    @Stable
    internal fun backgroundColor(): Color = backgroundColor
}
