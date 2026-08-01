// Strict transcription of top.yukonga.miuix.kmp.basic.Button
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.defaultMinSize
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

@Composable
fun MiuixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = MiuixButtonDefaults.CornerRadius,
    minWidth: Dp = MiuixButtonDefaults.MinWidth,
    minHeight: Dp = MiuixButtonDefaults.MinHeight,
    colors: MiuixButtonColors = MiuixButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = MiuixButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    MiuixSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        color = if (enabled) colors.color else colors.disabledColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
                .padding(insideMargin),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun MiuixTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = MiuixButtonDefaults.CornerRadius,
    minWidth: Dp = MiuixButtonDefaults.MinWidth,
    minHeight: Dp = MiuixButtonDefaults.MinHeight,
    colors: MiuixTextButtonColors = MiuixButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = MiuixButtonDefaults.InsideMargin,
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = MiuixButtonColors(
            color = colors.color,
            disabledColor = colors.disabledColor,
            contentColor = colors.textColor,
            disabledContentColor = colors.disabledTextColor,
        ),
        insideMargin = insideMargin,
    ) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.button,
        )
    }
}

object MiuixButtonDefaults {
    val MinWidth = 58.dp
    val MinHeight = 40.dp
    val CornerRadius = 16.dp
    val InsideMargin = PaddingValues(16.dp)

    @Composable
    fun buttonColors(
        color: Color = MiuixTheme.colorScheme.secondaryVariant,
        disabledColor: Color = MiuixTheme.colorScheme.disabledSecondaryVariant,
        contentColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
        disabledContentColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ): MiuixButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        MiuixButtonColors(color, disabledColor, contentColor, disabledContentColor)
    }

    @Composable
    fun buttonColorsPrimary(): MiuixButtonColors {
        val color = MiuixTheme.colorScheme.primary
        val disabledColor = MiuixTheme.colorScheme.disabledPrimaryButton
        val contentColor = MiuixTheme.colorScheme.onPrimary
        val disabledContentColor = MiuixTheme.colorScheme.disabledOnPrimaryButton
        return remember(color, disabledColor, contentColor, disabledContentColor) {
            MiuixButtonColors(color, disabledColor, contentColor, disabledContentColor)
        }
    }

    @Composable
    fun textButtonColors(
        color: Color = MiuixTheme.colorScheme.secondaryVariant,
        disabledColor: Color = MiuixTheme.colorScheme.disabledSecondaryVariant,
        textColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
        disabledTextColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ): MiuixTextButtonColors = remember(color, disabledColor, textColor, disabledTextColor) {
        MiuixTextButtonColors(color, disabledColor, textColor, disabledTextColor)
    }

    @Composable
    fun textButtonColorsPrimary(): MiuixTextButtonColors {
        val color = MiuixTheme.colorScheme.primary
        val disabledColor = MiuixTheme.colorScheme.disabledPrimaryButton
        val textColor = MiuixTheme.colorScheme.onPrimary
        val disabledTextColor = MiuixTheme.colorScheme.disabledOnPrimaryButton
        return remember(color, disabledColor, textColor, disabledTextColor) {
            MiuixTextButtonColors(color, disabledColor, textColor, disabledTextColor)
        }
    }
}

@Immutable
data class MiuixButtonColors(
    val color: Color,
    val disabledColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
)

@Immutable
data class MiuixTextButtonColors(
    val color: Color,
    val disabledColor: Color,
    val textColor: Color,
    val disabledTextColor: Color,
)
