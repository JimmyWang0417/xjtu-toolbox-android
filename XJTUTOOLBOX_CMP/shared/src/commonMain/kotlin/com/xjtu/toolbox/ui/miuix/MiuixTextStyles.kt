// Strict rewrite of top.yukonga.miuix.kmp.theme.TextStyles
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.sp

@Stable
class MiuixTextStyles(
    main: TextStyle,
    paragraph: TextStyle,
    body1: TextStyle,
    body2: TextStyle,
    button: TextStyle,
    footnote1: TextStyle,
    footnote2: TextStyle,
    headline1: TextStyle,
    headline2: TextStyle,
    subtitle: TextStyle,
    title1: TextStyle,
    title2: TextStyle,
    title3: TextStyle,
    title4: TextStyle,
) {
    var main by mutableStateOf(main, structuralEqualityPolicy())
        internal set
    var paragraph by mutableStateOf(paragraph, structuralEqualityPolicy())
        internal set
    var body1 by mutableStateOf(body1, structuralEqualityPolicy())
        internal set
    var body2 by mutableStateOf(body2, structuralEqualityPolicy())
        internal set
    var button by mutableStateOf(button, structuralEqualityPolicy())
        internal set
    var footnote1 by mutableStateOf(footnote1, structuralEqualityPolicy())
        internal set
    var footnote2 by mutableStateOf(footnote2, structuralEqualityPolicy())
        internal set
    var headline1 by mutableStateOf(headline1, structuralEqualityPolicy())
        internal set
    var headline2 by mutableStateOf(headline2, structuralEqualityPolicy())
        internal set
    var subtitle by mutableStateOf(subtitle, structuralEqualityPolicy())
        internal set
    var title1 by mutableStateOf(title1, structuralEqualityPolicy())
        internal set
    var title2 by mutableStateOf(title2, structuralEqualityPolicy())
        internal set
    var title3 by mutableStateOf(title3, structuralEqualityPolicy())
        internal set
    var title4 by mutableStateOf(title4, structuralEqualityPolicy())
        internal set

    fun copy(
        main: TextStyle = this.main,
        paragraph: TextStyle = this.paragraph,
        body1: TextStyle = this.body1,
        body2: TextStyle = this.body2,
        button: TextStyle = this.button,
        footnote1: TextStyle = this.footnote1,
        footnote2: TextStyle = this.footnote2,
        headline1: TextStyle = this.headline1,
        headline2: TextStyle = this.headline2,
        subtitle: TextStyle = this.subtitle,
        title1: TextStyle = this.title1,
        title2: TextStyle = this.title2,
        title3: TextStyle = this.title3,
        title4: TextStyle = this.title4,
    ): MiuixTextStyles = MiuixTextStyles(
        main, paragraph, body1, body2, button,
        footnote1, footnote2, headline1, headline2, subtitle,
        title1, title2, title3, title4,
    )
}

fun defaultMiuixTextStyles(
    main: TextStyle = TextStyle(fontSize = 17.sp),
    paragraph: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 20.sp),
    body1: TextStyle = TextStyle(fontSize = 16.sp),
    body2: TextStyle = TextStyle(fontSize = 14.sp),
    button: TextStyle = TextStyle(fontSize = 17.sp),
    footnote1: TextStyle = TextStyle(fontSize = 13.sp),
    footnote2: TextStyle = TextStyle(fontSize = 11.sp),
    headline1: TextStyle = TextStyle(fontSize = 17.sp),
    headline2: TextStyle = TextStyle(fontSize = 16.sp),
    subtitle: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    title1: TextStyle = TextStyle(fontSize = 32.sp),
    title2: TextStyle = TextStyle(fontSize = 24.sp),
    title3: TextStyle = TextStyle(fontSize = 20.sp),
    title4: TextStyle = TextStyle(fontSize = 18.sp),
): MiuixTextStyles = MiuixTextStyles(
    main, paragraph, body1, body2, button,
    footnote1, footnote2, headline1, headline2, subtitle,
    title1, title2, title3, title4,
)

@Stable
internal fun MiuixTextStyles.updateTextStylesFrom(other: MiuixTextStyles) {
    main = other.main; paragraph = other.paragraph
    body1 = other.body1; body2 = other.body2; button = other.button
    footnote1 = other.footnote1; footnote2 = other.footnote2
    headline1 = other.headline1; headline2 = other.headline2
    subtitle = other.subtitle
    title1 = other.title1; title2 = other.title2; title3 = other.title3; title4 = other.title4
}

internal val LocalMiuixTextStyles = staticCompositionLocalOf { defaultMiuixTextStyles() }
