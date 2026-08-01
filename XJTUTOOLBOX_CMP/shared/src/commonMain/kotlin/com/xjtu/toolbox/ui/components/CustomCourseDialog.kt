package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.schedule.CustomCourseEntity
import com.xjtu.toolbox.ui.miuix.*

/**
 * 自定义课程编辑界面 (KuiklyUI Material3 全屏 Scaffold)
 * @param existing 编辑已有课程时传入，为 null 表示新增
 * @param termCode 当前学期代码
 * @param onSave 保存回调
 * @param onDelete 删除回调（仅编辑模式）
 * @param onDismiss 关闭回调
 */
@Composable
fun CustomCourseEditor(
    existing: CustomCourseEntity? = null,
    termCode: String,
    onSave: (CustomCourseEntity) -> Unit,
    onDelete: ((CustomCourseEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEdit = existing != null

    var courseName by remember { mutableStateOf(existing?.courseName ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var dayOfWeek by remember { mutableStateOf(existing?.dayOfWeek ?: 1) }
    var startSection by remember { mutableStateOf(existing?.startSection ?: 1) }
    var endSection by remember { mutableStateOf(existing?.endSection ?: 2) }
    var selectedWeeks by remember {
        mutableStateOf(
            if (existing != null) {
                existing.weekBits.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()
            } else {
                (1..16).toSet()
            }
        )
    }

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = if (isEdit) "编辑课程" else "添加课程",
                navigationIcon = { BackButton(onClick = onDismiss) },
                actions = {
                    MiuixTextButton(
                        text = if (isEdit) "保存" else "添加",
                        onClick = {
                            val weekBitsStr = (1..20).joinToString("") { if (it in selectedWeeks) "1" else "0" }
                            val entity = (existing ?: CustomCourseEntity(
                                courseName = "", teacher = "", location = "", weekBits = "",
                                dayOfWeek = 1, startSection = 1, endSection = 1, termCode = termCode
                            )).copy(
                                courseName = courseName.trim(),
                                teacher = teacher.trim(),
                                location = location.trim(),
                                weekBits = weekBitsStr,
                                dayOfWeek = dayOfWeek,
                                startSection = startSection,
                                endSection = endSection,
                                termCode = termCode,
                                note = note.trim()
                            )
                            onSave(entity)
                            onDismiss()
                        },
                        enabled = courseName.isNotBlank() && selectedWeeks.isNotEmpty(),
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                MiuixTextField(
                    value = courseName, onValueChange = { v -> courseName = v },
                    label = "课程名称 *", singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixTextField(
                        value = teacher, onValueChange = { v -> teacher = v },
                        label = "教师", singleLine = true, modifier = Modifier.weight(1f)
                    )
                    MiuixTextField(
                        value = location, onValueChange = { v -> location = v },
                        label = "教室", singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            }

            item { MiuixHorizontalDivider() }

            item {
                MiuixText("星期", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                    dayNames.forEachIndexed { index, name ->
                        val day = index + 1
                        val isSelected = dayOfWeek == day
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer)
                                .clickable { dayOfWeek = day }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            MiuixText(name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiuixText("第${startSection}节", modifier = Modifier.weight(1f))
                    MiuixText("→", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    MiuixText("第${endSection}节", modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixButton(onClick = { if (startSection > 1) { startSection--; if (endSection < startSection) endSection = startSection } }, modifier = Modifier.weight(1f)) { MiuixText("-") }
                    MiuixButton(onClick = { if (startSection < 12) { startSection++; if (endSection < startSection) endSection = startSection } }, modifier = Modifier.weight(1f)) { MiuixText("+") }
                    Spacer(Modifier.width(8.dp))
                    MiuixButton(onClick = { if (endSection > startSection) endSection-- }, modifier = Modifier.weight(1f)) { MiuixText("-") }
                    MiuixButton(onClick = { if (endSection < 12) endSection++ }, modifier = Modifier.weight(1f)) { MiuixText("+") }
                }
            }

            item { MiuixHorizontalDivider() }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiuixText("上课周次", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                    MiuixTextButton(text = "全选", onClick = { selectedWeeks = (1..20).toSet() })
                    MiuixTextButton(text = "单周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 1 }.toSet() })
                    MiuixTextButton(text = "双周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 0 }.toSet() })
                    MiuixTextButton(text = "清空", onClick = { selectedWeeks = emptySet() })
                }
            }
            item {
                WeekCheckboxGrid(selectedWeeks = selectedWeeks, onToggle = { week ->
                    selectedWeeks = if (week in selectedWeeks) selectedWeeks - week else selectedWeeks + week
                })
            }

            item {
                MiuixTextField(
                    value = note, onValueChange = { v -> note = v },
                    label = "备注", singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            if (isEdit && existing != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    MiuixButton(
                        onClick = { onDelete?.invoke(existing); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = MiuixButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.errorContainer),
                    ) { MiuixText("删除此课程", color = MiuixTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun WeekCheckboxGrid(selectedWeeks: Set<Int>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in listOf(1..10, 11..20)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (week in row) {
                    val isSelected = week in selectedWeeks
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer)
                            .clickable { onToggle(week) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MiuixText("$week", fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
