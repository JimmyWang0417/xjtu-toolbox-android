// Strict transcription of top.yukonga.miuix.kmp.basic.TopAppBar
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.IntOffset
import com.tencent.kuikly.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

// ─── TopAppBarState ───

@Stable
class MiuixTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
) {
    var heightOffsetLimit = initialHeightOffsetLimit

    var heightOffset: Float
        get() = _heightOffset.floatValue
        set(newOffset) {
            _heightOffset.floatValue =
                newOffset.coerceIn(minimumValue = heightOffsetLimit, maximumValue = 0f)
        }

    var contentOffset by mutableFloatStateOf(initialContentOffset)

    val collapsedFraction: Float
        get() = if (heightOffsetLimit != 0f) heightOffset / heightOffsetLimit else 0f

    val overlappedFraction: Float
        get() = if (heightOffsetLimit != 0f) {
            1 - ((heightOffsetLimit - contentOffset).coerceIn(heightOffsetLimit, 0f) / heightOffsetLimit)
        } else 0f

    private var _heightOffset = mutableFloatStateOf(initialHeightOffset)
}

@Composable
fun rememberMiuixTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f,
): MiuixTopAppBarState = remember {
    MiuixTopAppBarState(initialHeightOffsetLimit, initialHeightOffset, initialContentOffset)
}

// ─── ScrollBehavior ───

@Stable
class MiuixScrollBehavior(
    val state: MiuixTopAppBarState = MiuixTopAppBarState(),
) {
    fun onPreScroll(availableY: Float): Float {
        if (availableY > 0) return 0f
        val prevHeightOffset = state.heightOffset
        state.heightOffset = state.heightOffset + availableY
        return if (prevHeightOffset != state.heightOffset) availableY else 0f
    }

    fun onPostScroll(consumedY: Float, availableY: Float): Float {
        state.contentOffset += consumedY
        if (availableY < 0f || consumedY < 0f) {
            val oldOffset = state.heightOffset
            state.heightOffset = state.heightOffset + consumedY
            return state.heightOffset - oldOffset
        }
        if (availableY > 0f) {
            val oldOffset = state.heightOffset
            state.heightOffset = state.heightOffset + availableY
            return state.heightOffset - oldOffset
        }
        return 0f
    }
}

@Composable
fun rememberMiuixScrollBehavior(
    state: MiuixTopAppBarState = rememberMiuixTopAppBarState(),
): MiuixScrollBehavior = remember(state) { MiuixScrollBehavior(state) }

// ─── TopAppBar ───

@Composable
fun MiuixTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    largeTitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: MiuixScrollBehavior? = null,
    horizontalPadding: Dp = 26.dp,
) {
    val displayLargeTitle = largeTitle ?: title
    val density = LocalDensity.current

    var largeTitleHeightPx by remember { mutableStateOf(0) }
    val expandedHeightPx by rememberUpdatedState(
        largeTitleHeightPx.toFloat().coerceAtLeast(0f)
    )

    SideEffect {
        if (scrollBehavior != null && scrollBehavior.state.heightOffsetLimit != -expandedHeightPx) {
            scrollBehavior.state.heightOffsetLimit = -expandedHeightPx
        }
    }

    val heightOffset by remember {
        derivedStateOf {
            val offset = scrollBehavior?.state?.heightOffset ?: 0f
            if (offset.isNaN()) 0 else offset.roundToInt()
        }
    }

    val extOffset by remember {
        derivedStateOf {
            if (expandedHeightPx > 0f) abs(heightOffset) / expandedHeightPx * 2 else 0f
        }
    }

    val largeTitleAlpha by remember {
        derivedStateOf {
            if (expandedHeightPx > 0f) {
                1f - (abs(heightOffset) / expandedHeightPx * 2).coerceIn(0f, 1f)
            } else 1f
        }
    }

    val smallTitleAlpha by animateFloatAsState(
        targetValue = if (1 - extOffset.coerceIn(0f, 1f) == 0f) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
    )
    val smallTitleTranslationY by animateFloatAsState(
        targetValue = if (extOffset > 1f) 0f else 12f,
        animationSpec = tween(durationMillis = 250),
    )

    val layoutFraction = if (expandedHeightPx > 0f) {
        val offset = scrollBehavior?.state?.heightOffset ?: 0f
        if (offset.isNaN()) 1f else (1f - (abs(offset) / expandedHeightPx).coerceIn(0f, 1f))
    } else 1f

    val collapsedHeightDp = 56.dp
    val largeTitleHeightDp = with(density) { largeTitleHeightPx.toDp() }
    val expandedHeightDp = if (largeTitleHeightDp > collapsedHeightDp) largeTitleHeightDp else collapsedHeightDp
    val layoutHeightDp = collapsedHeightDp + (expandedHeightDp - collapsedHeightDp) * layoutFraction

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color)
            .heightIn(min = collapsedHeightDp)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            },
    ) {
        // Small title bar (56dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeightDp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box { navigationIcon() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = horizontalPadding)
                    .graphicsLayer(
                        alpha = smallTitleAlpha,
                        translationY = smallTitleTranslationY,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                MiuixText(
                    text = title,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        // Large title area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .alpha(largeTitleAlpha)
                .onSizeChanged { largeTitleHeightPx = it.height + with(density) { 56.dp.roundToPx() } },
        ) {
            MiuixText(
                modifier = Modifier.offset { IntOffset(0, heightOffset) },
                text = displayLargeTitle,
                fontSize = MiuixTheme.textStyles.title1.fontSize,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── SmallTopAppBar ───

@Composable
fun MiuixSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    horizontalPadding: Dp = 26.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(color)
            .clipToBounds()
            .padding(horizontal = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures { /* Consume click */ }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box { navigationIcon() }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            MiuixText(
                text = title,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
