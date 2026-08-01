package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.venue.VenueApi
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun VenueScreen() {
    val nav = LocalNavigation.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var venues by remember { mutableStateOf<List<VenueApi.Venue>>(emptyList()) }
    var selectedVenue by remember { mutableStateOf<VenueApi.Venue?>(null) }
    var slots by remember { mutableStateOf<List<VenueApi.AreaSlot>>(emptyList()) }
    var slotsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // TODO: obtain VenueLogin from CredentialStore and load venue list
    LaunchedEffect(Unit) { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = selectedVenue?.name ?: "场馆预约",
                navigationIcon = {
                    BackButton {
                        if (selectedVenue != null) { selectedVenue = null; slots = emptyList() }
                        else nav.goBack()
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                errorMessage = null; isLoading = true
                scope.launch { isLoading = false; errorMessage = "请先在设置中登录统一身份认证" }
            }, modifier = Modifier.fillMaxSize().padding(padding))
            selectedVenue != null -> {
                if (slotsLoading) LoadingState(message = "加载时段...", modifier = Modifier.fillMaxSize().padding(padding))
                else if (slots.isEmpty()) EmptyState(title = "暂无可用时段", modifier = Modifier.fillMaxSize().padding(padding))
                else LazyColumn(Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(slots) { slot ->
                        MiuixCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    MiuixText(slot.areaName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    MiuixText(slot.timeSlot, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                                    if (slot.price > 0) MiuixText("¥${slot.price}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                                }
                                MiuixSurface(shape = RoundedCornerShape(6.dp),
                                    color = if (slot.isAvailable) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else Color(0xFFF44336).copy(alpha = 0.15f)) {
                                    MiuixText(if (slot.isAvailable) "可预约" else "已满",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (slot.isAvailable) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            venues.isEmpty() -> EmptyState(title = "暂无可预约场馆", modifier = Modifier.fillMaxSize().padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(venues) { venue ->
                    MiuixCard(Modifier.fillMaxWidth().clickable {
                        selectedVenue = venue
                        slotsLoading = true
                        // TODO: load slots for venue
                        scope.launch { slotsLoading = false }
                    }) {
                        Column(Modifier.padding(16.dp)) {
                            MiuixText(venue.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (!venue.address.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                MiuixText(venue.address, fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
