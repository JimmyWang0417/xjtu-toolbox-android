package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.ui.miuix.*

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    unselectedContainerColor: Color = MiuixTheme.colorScheme.secondaryContainer
) {
    val chipShape = RoundedCornerShape(20.dp)
    val bgColor = if (selected) MiuixTheme.colorScheme.tertiaryContainer else unselectedContainerColor
    val textColor = if (selected) MiuixTheme.colorScheme.onTertiaryContainer else MiuixTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = modifier
            .clip(chipShape)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(4.dp))
        }
        MiuixText(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
fun AppSuggestionChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelContent: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
) {
    val chipShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(chipShape)
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(4.dp))
        }
        if (labelContent != null) labelContent()
        else if (label != null) MiuixText(label, fontSize = 13.sp)
    }
}
