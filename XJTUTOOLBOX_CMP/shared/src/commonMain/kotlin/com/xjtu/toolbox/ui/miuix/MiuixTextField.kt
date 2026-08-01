// Strict transcription of top.yukonga.miuix.kmp.basic.TextField
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tencent.kuikly.compose.animation.animateColorAsState
import com.tencent.kuikly.compose.animation.core.animateDpAsState
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.interaction.MutableInteractionSource
import com.tencent.kuikly.compose.foundation.interaction.collectIsFocusedAsState
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.foundation.text.KeyboardActions
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.drawWithContent
import com.tencent.kuikly.compose.ui.geometry.CornerRadius
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.graphics.drawscope.inset
import com.tencent.kuikly.compose.ui.graphics.takeOrElse
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.input.VisualTransformation
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

private enum class MiuixLabelAnimState { Hidden, Placeholder, Normal, Floating }

@Composable
fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    labelColor: Color = MiuixTheme.colorScheme.onSecondaryContainer,
    backgroundColor: Color = MiuixTheme.colorScheme.secondaryContainer,
    borderColor: Color = MiuixTheme.colorScheme.primary,
    cornerRadius: Dp = 16.dp,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(borderColor),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderWidthState by animateDpAsState(if (isFocused) 2.dp else 0.dp)
    val borderColorState by animateColorAsState(if (isFocused) borderColor else backgroundColor)
    val borderShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val labelState = remember(value, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> MiuixLabelAnimState.Hidden
            useLabelAsPlaceholder && value.isNotEmpty() -> MiuixLabelAnimState.Placeholder
            value.isNotEmpty() -> MiuixLabelAnimState.Floating
            else -> MiuixLabelAnimState.Normal
        }
    }

    val insideMarginH = 16.dp
    val insideMarginV = 16.dp

    val labelAnim by animateDpAsState(
        when (labelState) {
            MiuixLabelAnimState.Floating -> -(insideMarginV / 2)
            else -> 0.dp
        },
    )
    val labelFontSize by animateDpAsState(
        when (labelState) {
            MiuixLabelAnimState.Floating -> 10.dp
            else -> 17.dp
        },
    )

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val contentColor = LocalMiuixContentColor.current
    val resolvedTextStyle = remember(textStyle, contentColor) {
        val textColor = textStyle.color.takeOrElse { contentColor }
        textStyle.copy(color = textColor)
    }

    BasicTextField(
        value = value,
        onValueChange = currentOnValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = @Composable { innerTextField ->
            Box(
                modifier = Modifier
                    .background(backgroundColor, borderShape)
                    .drawWithContent {
                        drawContent()
                        val bw = borderWidthState
                        if (bw > 0.dp) {
                            val strokePx = bw.toPx()
                            val halfStroke = strokePx / 2f
                            val cr = cornerRadius.toPx()
                            inset(halfStroke) {
                                drawRoundRect(
                                    color = borderColorState,
                                    cornerRadius = CornerRadius(cr - halfStroke, cr - halfStroke),
                                    style = Stroke(width = strokePx),
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon?.invoke()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                when {
                                    leadingIcon == null && trailingIcon == null -> Modifier.padding(horizontal = insideMarginH, vertical = insideMarginV)
                                    leadingIcon == null -> Modifier.padding(start = insideMarginH).padding(vertical = insideMarginV)
                                    trailingIcon == null -> Modifier.padding(end = insideMarginH).padding(vertical = insideMarginV)
                                    else -> Modifier.padding(vertical = insideMarginV)
                                }
                            ),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (labelState != MiuixLabelAnimState.Hidden && labelState != MiuixLabelAnimState.Placeholder) {
                            MiuixText(
                                text = label,
                                fontSize = labelFontSize.value.sp,
                                color = labelColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.offset(y = labelAnim),
                                textAlign = TextAlign.Start,
                            )
                        }
                        Box(
                            modifier = Modifier.offset(
                                y = if (labelState == MiuixLabelAnimState.Floating) insideMarginV / 2 else 0.dp,
                            ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            innerTextField()
                        }
                    }
                    trailingIcon?.invoke()
                }
            }
        },
    )
}
