package com.example.wallpapercarousel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wallpapercarousel.data.Prefs

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 组规则的一句话摘要，给列表和弹窗复用 */
fun Prefs.Group.ruleSummary(): String {
    if (!hasRule) return "不自动切换"
    val dayText = when {
        days.isEmpty() -> "每天"
        days == setOf(1, 2, 3, 4, 5) -> "工作日"
        days == setOf(6, 7) -> "周末"
        else -> days.sorted().joinToString("、") { "周" + WEEK_LABELS[it - 1] }
    }
    return if (startMin >= 0 && endMin >= 0 && startMin != endMin) {
        "$dayText ${formatMinute(startMin)}–${formatMinute(endMin)}"
    } else {
        "$dayText · 全天"
    }
}

fun Prefs.Group.modeText(): String =
    if (mode == Prefs.MODE_RANDOM) "组内随机" else "组内顺序"

private fun formatMinute(m: Int): String {
    val mm = m.coerceIn(0, 1439)
    return "%02d:%02d".format(mm / 60, mm % 60)
}

/**
 * 分组管理：改顺序、编辑规则、删除
 */
@Composable
fun GroupManagerDialog(
    groups: List<Prefs.Group>,
    onDismiss: () -> Unit,
    onEdit: (Prefs.Group) -> Unit,
    onMove: (Prefs.Group, Int) -> Unit,
    onDelete: (Prefs.Group) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理壁纸集") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "顺序即优先级：多个组的规则同时命中时，排在前面的生效；都不命中则保持当前组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                groups.forEachIndexed { index, g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${g.modeText()} · ${g.ruleSummary()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { onMove(g, -1) },
                            enabled = index > 0
                        ) { Text("↑") }
                        TextButton(
                            onClick = { onMove(g, 1) },
                            enabled = index < groups.lastIndex
                        ) { Text("↓") }
                        TextButton(onClick = { onEdit(g) }) { Text("编辑") }
                        TextButton(
                            onClick = { onDelete(g) },
                            enabled = groups.size > 1
                        ) { Text("删除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** 编辑单个组：名称、组内方式、自动生效的星期与时段 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditDialog(
    group: Prefs.Group,
    onDismiss: () -> Unit,
    onConfirm: (Prefs.Group) -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var mode by remember(group.id) { mutableIntStateOf(group.mode) }
    var days by remember(group.id) { mutableStateOf(group.days) }
    var timeEnabled by remember(group.id) {
        mutableStateOf(group.startMin >= 0 && group.endMin >= 0 && group.startMin != group.endMin)
    }
    var startMin by remember(group.id) {
        mutableIntStateOf(if (group.startMin >= 0) group.startMin else 8 * 60)
    }
    var endMin by remember(group.id) {
        mutableIntStateOf(if (group.endMin >= 0) group.endMin else 20 * 60)
    }
    // 0 = 正在选开始时间，1 = 正在选结束时间
    var picking by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑壁纸集") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("组内切换方式", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == Prefs.MODE_SEQUENTIAL,
                        onClick = { mode = Prefs.MODE_SEQUENTIAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("顺序") }
                    SegmentedButton(
                        selected = mode == Prefs.MODE_RANDOM,
                        onClick = { mode = Prefs.MODE_RANDOM },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("随机") }
                }

                Text("自动生效星期（不选 = 每天）", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WEEK_LABELS.forEachIndexed { i, label ->
                        val d = i + 1
                        FilterChip(
                            selected = d in days,
                            onClick = {
                                days = if (d in days) days - d else days + d
                            },
                            label = { Text("周$label") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { days = setOf(1, 2, 3, 4, 5) }) { Text("工作日") }
                    TextButton(onClick = { days = setOf(6, 7) }) { Text("周末") }
                    TextButton(onClick = { days = emptySet() }) { Text("每天") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("限定时间段", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (timeEnabled) "${formatMinute(startMin)} – ${formatMinute(endMin)}" +
                                if (startMin > endMin) "（跨天）" else ""
                            else "全天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = timeEnabled, onCheckedChange = { timeEnabled = it })
                }
                if (timeEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { picking = 0 }) { Text("开始 ${formatMinute(startMin)}") }
                        TextButton(onClick = { picking = 1 }) { Text("结束 ${formatMinute(endMin)}") }
                    }
                }

                Text(
                    "规则用于「自动切组」：命中本组的时刻会自动换到本组；" +
                        "多个组同时命中时按管理列表里的顺序取前面的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    group.copy(
                        name = name.trim().ifBlank { group.name },
                        mode = mode,
                        days = days,
                        startMin = if (timeEnabled) startMin else -1,
                        endMin = if (timeEnabled) endMin else -1,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // 时间选择弹窗：每次打开用 key 重建，保证初始值对得上当前是选开始还是结束
    picking?.let { which ->
        key(which) {
            val initial = if (which == 0) startMin else endMin
            val state = rememberTimePickerState(
                initialHour = initial / 60,
                initialMinute = initial % 60,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { picking = null },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        val m = state.hour * 60 + state.minute
                        if (which == 0) startMin = m else endMin = m
                        picking = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("取消") } }
            )
        }
    }
}
