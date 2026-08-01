package com.xjtu.toolbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.xjtu.toolbox.ui.miuix.MiuixTheme
import com.xjtu.toolbox.ui.miuix.miuixLightColorScheme

val LocalNavigation = compositionLocalOf<NavigationState> {
    error("NavigationState not provided")
}

@Composable
fun App() {
    MiuixTheme(colors = miuixLightColorScheme()) {
        MainNavigation()
    }
}
