package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.emptyroom.CAMPUS_BUILDINGS
import com.xjtu.toolbox.emptyroom.EmptyRoomApi
import com.xjtu.toolbox.emptyroom.RoomInfo
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.EmptyState
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

private val PERIOD_TIMES = listOf(
    "08:00", "09:00", "10:10", "11:10", "14:00", "15:00", "16:10", "17:10", "19:00", "20:00", "21:00"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyRoomScreen() {
    val nav = LocalNavigation.current
    val scope = rememberCoroutineScope()
    val api = remember { EmptyRoomApi(HttpClient()) }

    val campusNames = remember { CAMPUS_BUILDINGS.keys.toList() }
    var selectedCampusIdx by remember { mutableStateOf(0) }
    val selectedCampus = campusNames.getOrElse(selectedCampusIdx) { campusNames.first() }
    val buildings = remember(selectedCampus) { CAMPUS_BUILDINGS[selectedCampus] ?: emptyList() }
    var selectedBuildings by remember(selectedCampus) { mutableStateOf(setOf(buildings.firstOrNull() ?: "")) }

    val availableDates = remember { api.getAvailableDates() }
    var selectedDate by remember { mutableStateOf(availableDates.firstOrNull() ?: "") }

    var rooms by remember { mutableStateOf<List<RoomInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var smartFilter by remember { mutableStateOf("全部") }
    var startPeriod by remember { mutableStateOf(1) }
    var endPeriod by remember { mutableStateOf(11) }

    LaunchedEffect(selectedCampus, selectedBuildings, selectedDate) {
        val active = selectedBuildings.filter { it.isNotEmpty() }.toSet()
        if (active.isEmpty()) return@LaunchedEffect
        isLoading = true; errorMessage = null
        try {
            rooms = api.getEmptyRoomsMulti(selectedCampus, active, selectedDate)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorMessage = e.message ?: "查询失败"
            rooms = emptyList()
        } finally { isLoading = false }
    }

    val displayRooms = remember(rooms, smartFilter, startPeriod, endPeriod) {
        val rangeFiltered = rooms.filter { room ->
            val s = (startPeriod - 1).coerceIn(0, room.status.lastIndex)
            val e = (endPeriod - 1).coerceIn(s, room.status.lastIndex)
            (s..e).all { room.status.getOrNull(it) == 0 }
        }
        when (smartFilter) {
            "大教室" -> rangeFiltered.filter { it.size >= 100 }
            else -> rangeFiltered
        }.sortedBy { it.name }
    }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "空闲教室",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── 校区选择 ──
            MiuixTabRow(
                tabs = campusNames.map { it.removeSuffix("校区") },
                selectedTabIndex = selectedCampusIdx,
                onTabSelected = { selectedCampusIdx = it },
            )

            // ── 教学楼选择 ──
            MiuixCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    MiuixText("教学楼", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        buildings.forEach { building ->
                            val sel = building in selectedBuildings
                            MiuixSurface(
                                modifier = Modifier.clickable {
                                    selectedBuildings = if (sel) {
                                        val ns = selectedBuildings - building; if (ns.isEmpty()) selectedBuildings else ns
                                    } else selectedBuildings + building
                                },
                                color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                            ) {
                                MiuixText(building, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (sel) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // 日期选择
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDates.forEachIndexed { idx, date ->
                            val label = if (idx == 0) "今天" else "明天"
                            MiuixSurface(
                                modifier = Modifier.clickable { selectedDate = date },
                                color = if (selectedDate == date) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                            ) {
                                MiuixText("$label ${date.substring(5)}", fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (selectedDate == date) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    // 筛选
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("全部", "大教室").forEach { filter ->
                            MiuixSurface(
                                modifier = Modifier.clickable { smartFilter = filter },
                                color = if (smartFilter == filter) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                            ) {
                                MiuixText(filter, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (smartFilter == filter) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }

            // ── 内容区 ──
            when {
                isLoading -> LoadingState(message = "正在查询...", modifier = Modifier.fillMaxSize())
                errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = {
                    errorMessage = null; isLoading = true
                    scope.launch {
                        try { rooms = api.getEmptyRoomsMulti(selectedCampus, selectedBuildings, selectedDate) }
                        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; errorMessage = e.message }
                        finally { isLoading = false }
                    }
                }, modifier = Modifier.fillMaxSize())
                displayRooms.isEmpty() -> EmptyState(title = "暂无符合条件的教室", modifier = Modifier.fillMaxSize())
                else -> {
                    MiuixText("${displayRooms.size} 间教室", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    // 节次表头
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(start = 90.dp, end = 8.dp)) {
                        (1..11).forEach { p ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                MiuixText("$p", fontSize = 9.sp, color = MiuixTheme.colorScheme.outline, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayRooms, key = { it.name }) { room ->
                            RoomCard(room)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomInfo) {
    MiuixCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(82.dp)) {
                MiuixText(room.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MiuixText("${room.size}座", fontSize = 11.sp, color = MiuixTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(6.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                room.status.forEachIndexed { _, value ->
                    val isFree = value == 0
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(3.dp))
                            .background(if (isFree) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MiuixTheme.colorScheme.error.copy(alpha = 0.15f))
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MiuixText(if (isFree) "○" else "×", fontSize = 8.sp,
                            color = if (isFree) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
