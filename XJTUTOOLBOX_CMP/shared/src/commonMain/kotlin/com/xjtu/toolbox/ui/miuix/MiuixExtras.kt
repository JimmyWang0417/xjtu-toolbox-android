// Strict transcription of Miuix TabRow, FilterChip, DropdownMenu, SmallTitle, BasicComponent
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.BoxWithConstraints
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.itemsIndexed
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlin.math.roundToInt

// ─── TabRow ───

object MiuixTabRowDefaults {
    val TabRowHeight = 42.dp
    val TabRowCornerRadius = 12.dp
    val TabRowMinWidth = 76.dp
    val TabRowMaxWidth = 98.dp

    @Composable
    fun tabRowColors(
        backgroundColor: Color = MiuixTheme.colorScheme.surface,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        selectedBackgroundColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        selectedContentColor: Color = MiuixTheme.colorScheme.onBackground,
    ): MiuixTabRowColors = remember(backgroundColor, contentColor, selectedBackgroundColor, selectedContentColor) {
        MiuixTabRowColors(backgroundColor, contentColor, selectedBackgroundColor, selectedContentColor)
    }
}

@Immutable
data class MiuixTabRowColors(
    private val backgroundColor: Color,
    private val contentColor: Color,
    private val selectedBackgroundColor: Color,
    private val selectedContentColor: Color,
) {
    @Stable
    internal fun backgroundColor(selected: Boolean): Color = if (selected) selectedBackgroundColor else backgroundColor

    @Stable
    internal fun contentColor(selected: Boolean): Color = if (selected) selectedContentColor else contentColor
}

@Composable
fun MiuixTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: MiuixTabRowColors = MiuixTabRowDefaults.tabRowColors(),
    minWidth: Dp = MiuixTabRowDefaults.TabRowMinWidth,
    maxWidth: Dp = MiuixTabRowDefaults.TabRowMaxWidth,
    height: Dp = MiuixTabRowDefaults.TabRowHeight,
    cornerRadius: Dp = MiuixTabRowDefaults.TabRowCornerRadius,
    itemSpacing: Dp = 9.dp,
) {
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .height(height)
            .background(color = colors.backgroundColor(false)),
    ) {
        val availableWidth = this.maxWidth
        val tabCount = tabs.size
        val tabWidth = remember(tabCount, minWidth, maxWidth, availableWidth, itemSpacing) {
            if (tabCount == 0) minWidth
            else {
                val totalSpacing = if (tabCount > 1) (tabCount - 1).toFloat() * itemSpacing.value else 0f
                val contentWidth = availableWidth.value - totalSpacing
                val idealWidth = contentWidth / tabCount
                when {
                    idealWidth < minWidth.value -> minWidth
                    idealWidth > maxWidth.value -> Dp(idealWidth)
                    else -> Dp(idealWidth)
                }
            }
        }

        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val spacingPx = with(density) { itemSpacing.toPx() }
        val indicatorOffset = remember { Animatable(0f) }
        val listState = rememberLazyListState()
        val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

        LaunchedEffect(selectedTabIndex, tabWidthPx, spacingPx) {
            val target = selectedTabIndex * (tabWidthPx + spacingPx)
            indicatorOffset.animateTo(target, tween(200, easing = LinearEasing))
        }

        LaunchedEffect(selectedTabIndex, availableWidth) {
            val centerOffset = (availableWidth - tabWidth) / 2
            val offsetPx = with(density) { -centerOffset.toPx() }.roundToInt()
            listState.animateScrollToItem(selectedTabIndex, offsetPx)
        }

        val scrollOffset = remember(listState) {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            firstIndex * (tabWidthPx + spacingPx) + firstOffset
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .offset { IntOffset((indicatorOffset.value - scrollOffset).roundToInt(), 0) }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(colors.backgroundColor(true)),
            )
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                itemsIndexed(tabs) { index, tabText ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(tabWidth)
                            .clip(shape)
                            .clickable { currentOnTabSelected(index) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = tabText,
                            color = colors.contentColor(selectedTabIndex == index),
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ─── FilterChip ───

@Composable
fun MiuixFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val bgColor = if (selected) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.secondaryContainer
    val textColor = if (selected) MiuixTheme.colorScheme.onTertiaryContainer else MiuixTheme.colorScheme.onSecondaryContainer

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MiuixText(
            text = label,
            color = textColor,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ─── DropdownMenu ───

@Composable
fun MiuixDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (expanded) {
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onDismissRequest)) {
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun MiuixDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        text()
    }
}

// ─── SmallTitle ───

@Composable
fun MiuixSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onBackgroundVariant,
    insideMargin: PaddingValues = PaddingValues(28.dp, 8.dp),
) {
    MiuixText(
        modifier = modifier.padding(insideMargin),
        text = text,
        style = MiuixTheme.textStyles.subtitle,
        color = textColor,
    )
}

// ─── BasicComponent ───

@Immutable
data class MiuixBasicComponentColors(
    val color: Color,
    val disabledColor: Color,
) {
    @Stable
    internal fun color(enabled: Boolean): Color = if (enabled) color else disabledColor
}

object MiuixBasicComponentDefaults {
    val InsideMargin = PaddingValues(16.dp)

    @Composable
    fun titleColor(
        color: Color = MiuixTheme.colorScheme.onBackground,
        disabledColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ): MiuixBasicComponentColors = remember(color, disabledColor) {
        MiuixBasicComponentColors(color, disabledColor)
    }

    @Composable
    fun summaryColor(
        color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        disabledColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
    ): MiuixBasicComponentColors = remember(color, disabledColor) {
        MiuixBasicComponentColors(color, disabledColor)
    }
}

@Composable
fun MiuixBasicComponent(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: MiuixBasicComponentColors = MiuixBasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: MiuixBasicComponentColors = MiuixBasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    insideMargin: PaddingValues = MiuixBasicComponentDefaults.InsideMargin,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val hasOnClick = onClick != null

    val clickableModifier = if (enabled && hasOnClick) {
        Modifier.clickable { currentOnClick?.invoke() }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(insideMargin),
        verticalArrangement = Arrangement.Center,
    ) {
        if (startAction == null && endActions == null) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                if (title != null) {
                    MiuixText(
                        text = title,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = titleColor.color(enabled),
                    )
                }
                if (summary != null) {
                    MiuixText(
                        text = summary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor.color(enabled),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startAction?.invoke()
                if (startAction != null) Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (title != null) {
                        MiuixText(
                            text = title,
                            fontSize = MiuixTheme.textStyles.headline1.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = titleColor.color(enabled),
                        )
                    }
                    if (summary != null) {
                        MiuixText(
                            text = summary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = summaryColor.color(enabled),
                        )
                    }
                }
                if (endActions != null) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        content = endActions,
                    )
                }
            }
        }
    }
}
