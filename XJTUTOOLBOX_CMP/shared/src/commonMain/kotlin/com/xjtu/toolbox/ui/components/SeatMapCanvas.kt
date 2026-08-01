package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.ui.miuix.*

/**
 * 座位地图组件 (KuiklyUI 跨平台版)
 *
 * 原 Android 版使用 Canvas + nativeCanvas + android.graphics.Paint 绘制座位地图。
 * KuiklyUI 版本使用 Compose 布局实现简化的座位列表视图，
 * 后续可通过 expect/actual 桥接各平台原生 Canvas 来实现完整的缩放/平移座位地图。
 *
 * @param seats 座位列表, 每个座位包含 id, 状态, 坐标等信息
 * @param onSeatClick 座位点击回调
 */
data class SeatInfo(
    val id: String,
    val label: String,
    val status: SeatStatus,
    val row: Int = 0,
    val col: Int = 0
)

enum class SeatStatus { AVAILABLE, OCCUPIED, SELECTED, DISABLED }

private val statusColor = mapOf(
    SeatStatus.AVAILABLE to Color(0xFF4ECCA3),
    SeatStatus.OCCUPIED to Color(0xFFBDBDBD),
    SeatStatus.SELECTED to Color(0xFF2196F3),
    SeatStatus.DISABLED to Color(0xFFE0E0E0)
)

private val statusLabel = mapOf(
    SeatStatus.AVAILABLE to "可用",
    SeatStatus.OCCUPIED to "已占",
    SeatStatus.SELECTED to "已选",
    SeatStatus.DISABLED to "不可用"
)

@Composable
fun SeatMapCanvas(
    seats: List<SeatInfo>,
    onSeatClick: (SeatInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (seats.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MiuixText("暂无座位数据", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        return
    }

    Column(modifier) {
        // 图例
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SeatStatus.entries.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(statusColor[status] ?: Color.Gray)) {}
                    MiuixText(statusLabel[status] ?: "", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        // 座位网格 (简化列表视图)
        val rowGroups = seats.groupBy { it.row }.toList().sortedBy { it.first }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rowGroups.forEach { pair ->
                val rowNum = pair.first
                val rowSeats = pair.second
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiuixText("$rowNum", fontSize = 10.sp, color = MiuixTheme.colorScheme.outline, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                        rowSeats.sortedBy { s -> s.col }.forEach { seat ->
                            val bgColor = statusColor[seat.status] ?: Color.Gray
                            val clickable = seat.status == SeatStatus.AVAILABLE || seat.status == SeatStatus.SELECTED
                            Box(
                                modifier = Modifier.weight(1f).heightIn(min = 28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bgColor)
                                    .let { m -> if (clickable) m.clickable { onSeatClick(seat) } else m },
                                contentAlignment = Alignment.Center
                            ) {
                                MiuixText(seat.label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
