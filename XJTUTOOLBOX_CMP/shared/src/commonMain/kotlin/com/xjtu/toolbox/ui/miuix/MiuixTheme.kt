// Strict transcription of top.yukonga.miuix.kmp.theme.MiuixTheme + ContentColor
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.ui.graphics.Color

val LocalMiuixContentColor = compositionLocalOf { Color.Black }

@Composable
fun MiuixTheme(
    colors: MiuixColors = MiuixTheme.colorScheme,
    textStyles: MiuixTextStyles = MiuixTheme.textStyles,
    content: @Composable () -> Unit,
) {
    val miuixColors = remember { colors.copy() }.apply { updateColorsFrom(colors) }
    val miuixTextStyles = remember { textStyles.copy() }.apply { updateTextStylesFrom(textStyles) }
    CompositionLocalProvider(
        LocalMiuixColors provides miuixColors,
        LocalMiuixTextStyles provides miuixTextStyles,
        LocalMiuixContentColor provides miuixColors.onBackground,
    ) {
        content()
    }
}

object MiuixTheme {
    val colorScheme: MiuixColors
        @Composable @ReadOnlyComposable
        get() = LocalMiuixColors.current

    val textStyles: MiuixTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalMiuixTextStyles.current
}
