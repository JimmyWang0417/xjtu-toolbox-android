// Strict transcription of top.yukonga.miuix.kmp.basic.Scaffold
// Original: https://github.com/miuix-kotlin-multiplatform/miuix
// SPDX-License-Identifier: Apache-2.0

package com.xjtu.toolbox.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.unit.dp

@Composable
fun MiuixScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }

    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }

    val contentPadding = remember(topBarHeightDp, bottomBarHeightDp) {
        PaddingValues(top = topBarHeightDp, bottom = bottomBarHeightDp)
    }

    MiuixSurface(
        modifier = modifier.fillMaxSize(),
        color = containerColor,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Main content
            Box(Modifier.fillMaxSize()) {
                content(contentPadding)
            }
            // TopBar
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .onSizeChanged { topBarHeightPx = it.height },
            ) {
                topBar()
            }
            // BottomBar
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .onSizeChanged { bottomBarHeightPx = it.height },
            ) {
                bottomBar()
            }
            // Snackbar host
            Box(Modifier.fillMaxSize()) { snackbarHost() }
            // Floating action button
            Box(Modifier.fillMaxSize()) { floatingActionButton() }
            // Floating toolbar overlay
            Box(Modifier.fillMaxSize()) { floatingToolbar() }
        }
    }
}
