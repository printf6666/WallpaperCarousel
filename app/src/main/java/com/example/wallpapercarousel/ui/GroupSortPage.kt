package com.example.wallpapercarousel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.util.BitmapUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 排序页每行几张 */
private const val COLUMNS = 3

/**
 * 排序页（全屏空白页）：只做一件事 —— 展示该壁纸集的全部图片并允许长按拖拽排序。
 *
 * 与主页的区别：
 * - 主页缩略图只能自由横滑浏览，**不支持长按排序**；
 * - 这里每行三张铺开全部图片，长按后拖动，其余图片实时让位/补位；
 * - 每次松手即回调 [onCommit]，由调用方落库，避免"忘记保存"。
 */
@Composable
fun GroupSortPage(
    groupName: String,
    items: List<String>,
    currentName: String?,
    onDismiss: () -> Unit,
    onItemClick: (String) -> Unit,
    onCommit: (List<String>) -> Unit,
) {
    // 只在切换壁纸集时重建：父层因落库刷新不会打断正在进行的拖拽
    val order = remember(groupName) { mutableStateListOf<String>().apply { addAll(items) } }
    val gridState = rememberLazyGridState()

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val edgePx = with(LocalDensity.current) { 72.dp.toPx() }

    // 图片被移走/删除时同步（排序落库只是换顺序，集合不变，这里不会打断拖拽）
    LaunchedEffect(items) {
        val current = items.toSet()
        order.removeAll { it !in current }
        items.filter { it !in order }.forEach { order.add(it) }
    }

    // 拖到上下边缘时自动滚动（内容滚动会整体位移槽位，需同步补偿拖拽偏移）
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (isActive) {
            val dy = when {
                dragPoint.y < edgePx -> -10f
                dragPoint.y > viewport.height - edgePx -> 10f
                else -> 0f
            }
            if (dy != 0f) {
                val consumed = gridState.scrollBy(dy)
                if (consumed != 0f) dragOffset += Offset(0f, consumed)
            }
            delay(16)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "排序 · $groupName",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "长按拖动排序，松手自动保存并补位 · 点按可移动到其它壁纸集",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                HorizontalDivider()

                if (order.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "这个壁纸集还没有图片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(COLUMNS),
                        state = gridState,
                        // 拖拽中禁用列表自身滚动，避免手势被滚动容器抢走；滚动改由边缘自动滚动接管
                        userScrollEnabled = draggingKey == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewport = it }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { pos ->
                                        dragPoint = pos
                                        val info = gridState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.hitTest(pos) }
                                        val key = info?.let { order.getOrNull(it.index) }
                                        if (key != null) {
                                            draggingKey = key
                                            dragOffset = Offset.Zero
                                        }
                                    },
                                    onDrag = { change, delta ->
                                        val key = draggingKey
                                            ?: return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        dragPoint = change.position
                                        dragOffset += delta

                                        val visible = gridState.layoutInfo.visibleItemsInfo
                                        val from = order.indexOf(key)
                                        val target = visible
                                            .firstOrNull { it.hitTest(change.position) }?.index
                                            ?: return@detectDragGesturesAfterLongPress
                                        if (from < 0 || target !in order.indices || target == from) {
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        // 先记下两个槽位的位置，换序后按差值补偿，让被拖项继续跟手
                                        val fromInfo = visible.firstOrNull { it.index == from }
                                        val targetInfo = visible.firstOrNull { it.index == target }
                                        order.removeAt(from)
                                        order.add(target, key)
                                        if (fromInfo != null && targetInfo != null) {
                                            dragOffset += Offset(
                                                (fromInfo.offset.x - targetInfo.offset.x).toFloat(),
                                                (fromInfo.offset.y - targetInfo.offset.y).toFloat()
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                        onCommit(order.toList())
                                    },
                                    onDragCancel = {
                                        // 手势被系统打断也把当前顺序存下来，避免"看着变了其实没存"
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                        onCommit(order.toList())
                                    }
                                )
                            },
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(count = order.size, key = { order[it] }) { index ->
                            val name = order[index]
                            val dragging = name == draggingKey
                            Box(
                                modifier = Modifier
                                    .then(
                                        // 被拖项跟手移动，其余项用位移动画实现"自动补位"
                                        if (dragging) Modifier.zIndex(1f)
                                        else Modifier.animateItem()
                                    )
                                    .graphicsLayer {
                                        if (dragging) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            scaleX = 1.08f
                                            scaleY = 1.08f
                                        }
                                    }
                                    .padding(2.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = draggingKey == null) { onItemClick(name) }
                            ) {
                                SortThumb(name = name, isCurrent = name == currentName)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SortThumb(name: String, isCurrent: Boolean) {
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = name) {
        value = withContext(Dispatchers.IO) {
            BitmapUtil.decodeScaled(WallpaperStore.file(ctx, name).absolutePath, 256)
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
    if (isCurrent) {
        Text(
            "当前",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** 触点是否落在该格子内（LazyGridItemInfo 只暴露 offset/size，没有 bounds） */
private fun LazyGridItemInfo.hitTest(pos: Offset): Boolean =
    pos.x >= offset.x && pos.x < offset.x + size.width &&
        pos.y >= offset.y && pos.y < offset.y + size.height
