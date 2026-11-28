package com.example.wallpapercarousel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wallpapercarousel.WallpaperItem
import com.example.wallpapercarousel.data.Prefs

/** 一屏可见几张缩略图（不翻页，超出部分自由横滑） */
private const val VISIBLE_THUMBS = 3

/**
 * 一个壁纸集 = 一张卡片。
 *
 * 头部两行：
 * - 第一行：名称（数量）+ 描边小加号 ＋（只往本组加图）+ 齿轮（编辑本组）；
 * - 第二行：「应用」/「应用中」+「排序」平分整行。
 * 内容：一排可自由横向滚动的缩略图（非翻页），排序统一放到排序页里做。
 */
@Composable
fun GroupCard(
    group: Prefs.Group,
    items: List<String>,
    isActive: Boolean,
    currentName: String?,
    modifier: Modifier = Modifier,
    onApply: () -> Unit,
    /** 点描边小加号：只往这个组加图，不切换"正在应用"的组 */
    onAdd: () -> Unit,
    /** 空态整块点击：同样是往这个组加图 */
    onAddHere: () -> Unit,
    onSort: () -> Unit,
    onSettings: () -> Unit,
    onUse: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：名称（数量）+ 描边小加号（往本组加图）+ 齿轮（编辑本组）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${group.name}（${items.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(36.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "向这个壁纸集添加壁纸",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "编辑壁纸集",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 第二行：应用 / 排序 平分整行，点击区域比挤在标题行里大得多
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 「应用」→ 该组成为当前播放的组；已是当前组时显示「应用中」并停用点击
                Button(
                    onClick = onApply,
                    enabled = !isActive,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                ) { Text(if (isActive) "应用中" else "应用") }
                OutlinedButton(
                    onClick = onSort,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("排序") }
            }

            if (items.isEmpty()) {
                // 空态整块可点：同样是往这个集里加图（不会把正在应用的集换掉）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onAddHere() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "这个壁纸集还是空的，点这里添加壁纸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gap = 8.dp
                    val itemWidth = (maxWidth - gap * (VISIBLE_THUMBS - 1)) / VISIBLE_THUMBS
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(bottom = 2.dp)
                    ) {
                        items(count = items.size, key = { items[it] }) { index ->
                            val name = items[index]
                            WallpaperItem(
                                name = name,
                                modifier = Modifier.width(itemWidth),
                                isCurrent = name == currentName,
                                onUse = { onUse(name) },
                                onPreview = { onPreview(name) },
                                onDelete = { onDelete(name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
