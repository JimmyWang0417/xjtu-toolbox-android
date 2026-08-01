package com.xjtu.toolbox.cmps.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.xjtu.toolbox.cmps.data.CampusRepository
import com.xjtu.toolbox.cmps.data.CampusLocalStore
import com.xjtu.toolbox.cmps.data.DemoCampusRepository
import com.xjtu.toolbox.cmps.data.HybridCampusRepository
import com.xjtu.toolbox.cmps.ui.screens.RootScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalNavigator = compositionLocalOf<AppNavigator> { error("Navigator not provided") }
val LocalAppStore = compositionLocalOf<AppStore> { error("AppStore not provided") }
val LocalCampusRepository = compositionLocalOf<CampusRepository> { error("CampusRepository not provided") }

@Composable
fun App() {
    val navigator = remember { AppNavigator() }
    val localStore = remember { CampusLocalStore() }
    val store = remember { AppStore(localStore) }
    val repository = remember { HybridCampusRepository(DemoCampusRepository(localStore), localStore) }
    val stack by navigator.stack.collectAsState()
    val session by store.session.collectAsState()

    MiuixTheme {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalAppStore provides store,
            LocalCampusRepository provides repository,
        ) {
            RootScaffold(
                currentRoute = stack.last(),
                session = session,
                onBack = { navigator.back() },
            )
        }
    }
}
