package com.example.wallpapercarousel.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wallpapercarousel.data.Prefs

private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

private fun formatMinute(m: Int): String {
    val mm = m.coerceIn(0, 1439)
    return "%02d:%02d".format(mm / 60, mm % 60)
}

/**
 * 编辑单个壁纸集：名称、组内方式、自动生效的星期与时段，左下角可删除该壁纸集。
 *
 * 用自定义 Dialog 而不是 AlertDialog，是因为要把「删除壁纸集」固定在左下角，
 * 与右下角的「取消 / 保存」分开，避免误触。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditDialog(
    group: Prefs.Group,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text("编辑壁纸集", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
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
                                    onClick = { days = if (d in days) days - d else days + d },
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
                                    if (timeEnabled) {
                                        "${formatMinute(startMin)} – ${formatMinute(endMin)}" +
                                            if (startMin > endMin) "（跨天）" else ""
                                    } else {
                                        "全天"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = timeEnabled, onCheckedChange = { timeEnabled = it })
                        }
                        if (timeEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { picking = 0 }) {
                                    Text("开始 ${formatMinute(startMin)}")
                                }
                                TextButton(onClick = { picking = 1 }) {
                                    Text("结束 ${formatMinute(endMin)}")
                                }
                            }
                        }

                        Text(
                            "规则用于「自动切组」：命中本组的时刻会自动换到本组；" +
                                "多个组同时命中时按卡片顺序取前面的。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 左下角：删除壁纸集。组里的壁纸不会丢，会并入第一个壁纸集
                        TextButton(
                            onClick = onDelete,
                            enabled = canDelete
                        ) {
                            Text(
                                "删除壁纸集",
                                color = if (canDelete) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("取消") }
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
                    }
                }
            }
        }
    }

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
