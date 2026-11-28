package com.example.wallpapercarousel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * 编辑单个壁纸集：名称、组内切换方式，左下角可删除该壁纸集。
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
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("组内切换方式", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
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

                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 左下角：删除壁纸集（红色描边环）。组里的壁纸不会丢，会并入第一个壁纸集
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = canDelete,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (canDelete) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
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
                                )
                            )
                        }) { Text("保存") }
                    }
                }
            }
        }
    }
}
