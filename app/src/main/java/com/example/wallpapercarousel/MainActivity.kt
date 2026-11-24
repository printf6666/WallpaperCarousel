package com.example.wallpapercarousel

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wallpapercarousel.data.Prefs
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.logic.RotationScheduler
import com.example.wallpapercarousel.logic.WallpaperApplier
import com.example.wallpapercarousel.util.BitmapUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(Prefs.load(ctx)) }
    var wallpapers by remember { mutableStateOf(Prefs.getWallpaperNames(ctx)) }
    var nextAt by remember { mutableStateOf(Prefs.getNextTriggerAt(ctx)) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var tipsExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        settings = Prefs.load(ctx)
        wallpapers = Prefs.getWallpaperNames(ctx)
        nextAt = Prefs.getNextTriggerAt(ctx)
    }

    // 回到前台时刷新状态（下次更换时间等）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun update(s: Prefs.Settings) {
        Prefs.save(ctx, s)
        RotationScheduler.onSettingsChanged(ctx)
        refresh()
    }

    fun rotateNow() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                WallpaperApplier.applyNext(ctx, reschedule = false)
            }
            if (ok && settings.enabled) {
                RotationScheduler.scheduleNext(ctx)
                refresh()
            }
            val msg = when {
                ok -> "已更换壁纸"
                wallpapers.isEmpty() -> "没有可用壁纸"
                else -> "更换失败，请稍后重试"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { WallpaperStore.add(ctx, it) } }
            if (added.isEmpty()) {
                Toast.makeText(ctx, "添加失败，请重试", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setWallpaperNames(ctx, Prefs.getWallpaperNames(ctx) + added)
                refresh()
                Toast.makeText(ctx, "已添加 ${added.size} 张壁纸", Toast.LENGTH_SHORT).show()
                if (Prefs.load(ctx).enabled) RotationScheduler.scheduleNext(ctx)
            }
        }
    }

    fun requestIgnoreBatteryOpt() {
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestExactAlarm() {
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val canExact = RotationScheduler.canScheduleExact(ctx)
    val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)

    val presets = remember {
        listOf(
            "1分钟" to 1, "5分钟" to 5, "15分钟" to 15, "30分钟" to 30,
            "1小时" to 60, "3小时" to 180, "6小时" to 360, "12小时" to 720, "1天" to 1440
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("壁纸轮播") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = "添加壁纸")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 运行状态 + 基本设置 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动轮换", style = MaterialTheme.typography.titleMedium)
                            val status = when {
                                !settings.enabled -> "已停用"
                                nextAt > 0 -> "运行中 · 下次更换 ${fmt.format(Date(nextAt))}"
                                else -> "运行中"
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { on ->
                                if (on && wallpapers.isEmpty()) {
                                    Toast.makeText(ctx, "请先添加壁纸", Toast.LENGTH_SHORT).show()
                                } else {
                                    update(settings.copy(enabled = on))
                                }
                            }
                        )
                    }

                    Text("轮换间隔", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { (label, minutes) ->
                            FilterChip(
                                selected = settings.intervalMinutes == minutes,
                                onClick = { update(settings.copy(intervalMinutes = minutes)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("切换方式", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !settings.randomOrder,
                            onClick = { update(settings.copy(randomOrder = false)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("按顺序") }
                        SegmentedButton(
                            selected = settings.randomOrder,
                            onClick = { update(settings.copy(randomOrder = true)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("随机") }
                    }

                    Button(
                        onClick = { rotateNow() },
                        enabled = wallpapers.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("立即换一张") }
                }
            }

            // ── 后台保活 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("后台保活", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (canExact) "精确闹钟：已授权" else "精确闹钟：未授权",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!canExact) {
                            TextButton(onClick = { requestExactAlarm() }) { Text("去授权") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (ignoringBattery) "电池优化：已忽略" else "电池优化：受限制",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!ignoringBattery) {
                            TextButton(onClick = { requestIgnoreBatteryOpt() }) { Text("去设置") }
                        }
                    }
                    TextButton(onClick = { tipsExpanded = !tipsExpanded }) {
                        Text(if (tipsExpanded) "收起设置指引" else "ColorOS 设置指引")
                    }
                    if (tipsExpanded) {
                        Text(
                            "1. 设置 → 应用 → 壁纸轮播 → 自启动：允许\n" +
                                "2. 设置 → 电池 → 本应用 → 允许后台运行 / 不优化\n" +
                                "3. 最近任务中下拉本应用卡片，锁定后台\n" +
                                "4. 若偶尔延迟，多为 Doze 深度休眠所致，属正常现象",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 壁纸列表 ──
            Text("我的壁纸（${wallpapers.size}）", style = MaterialTheme.typography.titleMedium)
            if (wallpapers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "还没有壁纸，点击右下角 + 添加",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(wallpapers) { name ->
                        WallpaperItem(name = name, onDelete = { pendingDelete = name })
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除壁纸") },
            text = { Text("确定要删除这张壁纸吗？") },
            confirmButton = {
                TextButton(onClick = {
                    WallpaperStore.remove(ctx, name)
                    Prefs.setWallpaperNames(ctx, Prefs.getWallpaperNames(ctx) - name)
                    pendingDelete = null
                    refresh()
                    if (settings.enabled) RotationScheduler.scheduleNext(ctx)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun WallpaperItem(name: String, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = name) {
            value = withContext(Dispatchers.IO) {
                BitmapUtil.decodeScaled(WallpaperStore.file(ctx, name).absolutePath, 256)
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
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
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color(0x66000000), CircleShape)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "删除",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
