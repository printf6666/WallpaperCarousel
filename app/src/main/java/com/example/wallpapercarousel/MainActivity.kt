package com.example.wallpapercarousel

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wallpapercarousel.data.Prefs
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.logic.RotationScheduler
import com.example.wallpapercarousel.logic.WallpaperApplier
import com.example.wallpapercarousel.logic.WallpaperCache
import com.example.wallpapercarousel.ui.GroupChips
import com.example.wallpapercarousel.ui.GroupEditDialog
import com.example.wallpapercarousel.ui.GroupManagerDialog
import com.example.wallpapercarousel.util.BitmapUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    /**
     * 系统分享进来的图片（ACTION_SEND / ACTION_SEND_MULTIPLE）。
     * Compose 侧收集后消费并清空，冷启动与热启动（onNewIntent）都走这里。
     */
    private val incomingUris = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUris.value = extractImageUris(intent)
        setContent { AppTheme { AppScreen(sharedUris = incomingUris) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = extractImageUris(intent)
        if (uris.isNotEmpty()) incomingUris.value = uris
    }

    /** 取分享 Intent 里的图片：优先 EXTRA_STREAM，其次 ClipData（部分相册只填后者） */
    private fun extractImageUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val fromExtra = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(singleStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> multiStream(intent)
            else -> emptyList()
        }
        if (fromExtra.isNotEmpty()) return fromExtra
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    private fun singleStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun multiStream(intent: Intent): List<Uri> {
        val list = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return list ?: emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppScreen(sharedUris: MutableStateFlow<List<Uri>>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(Prefs.load(ctx)) }
    // SnapshotStateList：拖拽排序时就地移动，避免 pointerInput 因列表重建而被重启
    val wallpapers = remember {
        mutableStateListOf<String>().apply { addAll(Prefs.getWallpaperNames(ctx)) }
    }
    var nextAt by remember { mutableStateOf(Prefs.getNextTriggerAt(ctx)) }
    var lastApplied by remember { mutableStateOf(Prefs.getLastApplied(ctx)) }
    // 当前正在使用的壁纸文件名（按名字记，换序/删删除都不会失效）
    var currentName by remember {
        mutableStateOf(
            Prefs.getLastName(ctx) ?: wallpapers.getOrNull(Prefs.getLastIndex(ctx))
        )
    }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    // 正在调整裁剪的壁纸（null 表示未打开预览页）
    var cropTarget by remember { mutableStateOf<String?>(null) }
    // 编辑态下点缩略图 → 移动到其它壁纸集
    var movingWallpaper by remember { mutableStateOf<String?>(null) }
    var tipsExpanded by remember { mutableStateOf(false) }

    // ── 壁纸集 ──
    var groups by remember { mutableStateOf(Prefs.groups(ctx)) }
    var currentGroupId by remember { mutableStateOf(Prefs.currentGroupId(ctx)) }
    // 列表当前筛选的组；null = 看全部
    var viewGroupId by remember { mutableStateOf<String?>(null) }
    var showGroupManager by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Prefs.Group?>(null) }

    // 长按进入的"抖动编辑态"
    var editing by remember { mutableStateOf(false) }
    val editState = rememberUpdatedState(editing)
    val jiggle = remember { Animatable(0f) }
    val jiggleState = jiggle.asState()
    LaunchedEffect(editing) {
        if (editing) {
            jiggle.snapTo(0f)
            jiggle.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 320, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            jiggle.snapTo(0f)
        }
    }

    fun refresh() {
        settings = Prefs.load(ctx)
        val names = Prefs.getWallpaperNames(ctx)
        if (names != wallpapers.toList()) {
            wallpapers.clear()
            wallpapers.addAll(names)
        }
        groups = Prefs.groups(ctx)
        currentGroupId = Prefs.currentGroupId(ctx)
        if (viewGroupId != null && groups.none { it.id == viewGroupId }) viewGroupId = null
        nextAt = Prefs.getNextTriggerAt(ctx)
        lastApplied = Prefs.getLastApplied(ctx)
        currentName = Prefs.getLastName(ctx)
    }

    /** 保存分组结构并重排闹钟（规则/顺序变了都可能影响下一次触发） */
    fun persistGroups(list: List<Prefs.Group>) {
        Prefs.saveGroups(ctx, list)
        refresh()
        RotationScheduler.onSettingsChanged(ctx)
    }

    /**
     * 预生成成品图：把新加的壁纸先渲染成"正好等于屏幕分辨率"的 JPEG 缓存，
     * 第一次轮到它时就能直接 setStream，不必现场解大图。
     * 失败不影响正常使用（轮换时会自动重试），所以这里静默处理。
     */
    fun prewarm(names: List<String>) {
        if (names.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            names.forEach { n ->
                runCatching {
                    WallpaperCache.ensure(ctx, WallpaperStore.file(ctx, n), Prefs.getCrop(ctx, n))
                }
            }
        }
    }

    // ── 壁纸集操作 ──

    fun addGroup() {
        val id = "g_" + System.currentTimeMillis().toString(36)
        val g = Prefs.Group(id = id, name = "壁纸集 ${groups.size + 1}")
        Prefs.setCurrentGroupId(ctx, id)
        viewGroupId = id
        persistGroups(groups + g)
    }

    fun updateGroup(g: Prefs.Group) {
        editingGroup = null
        persistGroups(groups.map { if (it.id == g.id) g else it })
    }

    fun moveGroup(g: Prefs.Group, delta: Int) {
        val idx = groups.indexOfFirst { it.id == g.id }
        val target = idx + delta
        if (idx < 0 || target !in groups.indices) return
        val list = groups.toMutableList()
        list.removeAt(idx)
        list.add(target, g)
        persistGroups(list)
    }

    fun deleteGroup(g: Prefs.Group) {
        if (groups.size <= 1) {
            Toast.makeText(ctx, "至少保留一个壁纸集", Toast.LENGTH_SHORT).show()
            return
        }
        val rest = groups.filter { it.id != g.id }
        val target = rest.first()
        // 组里的壁纸不跟着删，整体并入第一个组
        wallpapers
            .filter { Prefs.wallpaperGroup(ctx, it) == g.id }
            .forEach { Prefs.setWallpaperGroup(ctx, it, target.id) }
        if (Prefs.currentGroupId(ctx) == g.id) Prefs.setCurrentGroupId(ctx, target.id)
        if (viewGroupId == g.id) viewGroupId = target.id
        persistGroups(rest)
    }

    /** 新加的壁纸归到"当前查看的组"，看全部时归到当前播放组 */
    fun assignToActiveGroup(names: List<String>) {
        val target = viewGroupId ?: Prefs.currentGroupId(ctx)
        names.forEach { Prefs.setWallpaperGroup(ctx, it, target) }
    }

    // 消费系统分享进来的图片：复制进壁纸库、刷新列表，然后清空信号避免重复处理
    val incomingShare by sharedUris.collectAsState()
    LaunchedEffect(incomingShare) {
        if (incomingShare.isEmpty()) return@LaunchedEffect
        val added = withContext(Dispatchers.IO) {
            incomingShare.mapNotNull { WallpaperStore.add(ctx, it) }
        }
        sharedUris.value = emptyList()
        if (added.isEmpty()) {
            Toast.makeText(ctx, "添加失败，请重试", Toast.LENGTH_SHORT).show()
        } else {
            Prefs.setWallpaperNames(ctx, Prefs.getWallpaperNames(ctx) + added)
            assignToActiveGroup(added)
            refresh()
            Toast.makeText(ctx, "已从分享添加 ${added.size} 张壁纸", Toast.LENGTH_SHORT).show()
            if (Prefs.load(ctx).enabled) RotationScheduler.scheduleNext(ctx)
            prewarm(added)
        }
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

    // 启动时清理源头已不存在的成品图缓存（以及上次写入中断留下的临时文件）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { WallpaperCache.pruneOrphans(ctx) }
        }
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
            if (ok) {
                if (settings.enabled) RotationScheduler.scheduleNext(ctx)
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

    /**
     * 一键使用指定壁纸：直接写入桌面壁纸，不弹窗、不跳转，仅用 Toast 给一句反馈。
     * 同时把"上次播放位置"指向这张，顺序轮播下次从这里继续。
     */
    fun useWallpaper(name: String) {
        scope.launch {
            val crop = Prefs.getCrop(ctx, name)
            val ok = withContext(Dispatchers.IO) {
                WallpaperApplier.apply(ctx, WallpaperStore.file(ctx, name), crop)
            }
            if (ok) {
                Prefs.setLastName(ctx, name)
                currentName = name
                lastApplied = Prefs.getLastApplied(ctx)
                Toast.makeText(ctx, "应用成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "应用失败，请重试", Toast.LENGTH_SHORT).show()
            }
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
                assignToActiveGroup(added)
                refresh()
                Toast.makeText(ctx, "已添加 ${added.size} 张壁纸", Toast.LENGTH_SHORT).show()
                if (Prefs.load(ctx).enabled) RotationScheduler.scheduleNext(ctx)
                prewarm(added)
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
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("壁纸轮播")
                    // 侧载安装容易装错包，标题旁直接显示当前版本，方便核对
                    val vName = remember {
                        runCatching {
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                        }.getOrNull().orEmpty()
                    }
                    if (vName.isNotEmpty()) {
                        Text(
                            "v$vName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            })
        },
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
                            if (lastApplied.isNotEmpty()) {
                                Text(
                                    "上次应用：$lastApplied",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

                    Text("播放范围", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !settings.scopeAll,
                            onClick = { update(settings.copy(scopeAll = false)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("分组播放") }
                        SegmentedButton(
                            selected = settings.scopeAll,
                            onClick = { update(settings.copy(scopeAll = true)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("全部播放") }
                    }

                    val activeGroup = groups.firstOrNull { it.id == currentGroupId }
                    Text(
                        if (settings.scopeAll) {
                            "切换方式（全部壁纸）"
                        } else {
                            "切换方式（当前组：${activeGroup?.name ?: "-"}）"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    val randomNow = if (settings.scopeAll) {
                        settings.randomOrder
                    } else {
                        activeGroup?.mode == Prefs.MODE_RANDOM
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !randomNow,
                            onClick = {
                                if (settings.scopeAll) {
                                    update(settings.copy(randomOrder = false))
                                } else if (activeGroup != null) {
                                    updateGroup(activeGroup.copy(mode = Prefs.MODE_SEQUENTIAL))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("按顺序") }
                        SegmentedButton(
                            selected = randomNow,
                            onClick = {
                                if (settings.scopeAll) {
                                    update(settings.copy(randomOrder = true))
                                } else if (activeGroup != null) {
                                    updateGroup(activeGroup.copy(mode = Prefs.MODE_RANDOM))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("随机") }
                    }

                    if (!settings.scopeAll) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("按规则自动切组", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "工作日/周末、白天/夜晚等，命中规则的组生效；未设置规则时不切换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoSwitch,
                                onCheckedChange = { update(settings.copy(autoSwitch = it)) }
                            )
                        }
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

            // ── 壁纸列表（壁纸集 + 长按拖拽排序） ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "我的壁纸（${wallpapers.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showGroupManager = true }) { Text("管理壁纸集") }
                if (editing) {
                    TextButton(onClick = { editing = false }) { Text("完成") }
                }
            }

            val groupCounts = groups.associate { g ->
                g.id to wallpapers.count { Prefs.wallpaperGroup(ctx, it) == g.id }
            }
            GroupChips(
                groups = groups,
                selectedId = viewGroupId,
                counts = groupCounts,
                onSelect = { id ->
                    viewGroupId = id
                    // 点组 = 手动切组：同时把当前播放组指过去
                    if (id != null) {
                        Prefs.setCurrentGroupId(ctx, id)
                        currentGroupId = id
                        // 手动指定了就关掉自动切组，否则下次轮换会被规则切走
                        if (settings.autoSwitch) {
                            update(settings.copy(autoSwitch = false))
                            Toast.makeText(ctx, "已手动切组，自动切组已关闭", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAdd = { addGroup() }
            )

            if (!editing && wallpapers.size > 1) {
                Text(
                    "长按拖拽排序 · 点击缩略图裁剪",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (editing) {
                Text(
                    "拖动排序 · 点缩略图可移动到其它壁纸集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 当前视图里要显示的壁纸（全部 or 某一组）
            val visible = remember {
                mutableStateListOf<String>().apply {
                    addAll(
                        if (viewGroupId == null) {
                            Prefs.getWallpaperNames(ctx)
                        } else {
                            Prefs.getWallpaperNames(ctx)
                                .filter { Prefs.wallpaperGroup(ctx, it) == viewGroupId }
                        }
                    )
                }
            }
            // 分组/列表变化时同步（拖拽过程中由手势直接改这个列表，不会被覆盖）
            LaunchedEffect(viewGroupId, wallpapers.toList()) {
                val next = if (viewGroupId == null) {
                    wallpapers.toList()
                } else {
                    wallpapers.filter { Prefs.wallpaperGroup(ctx, it) == viewGroupId }
                }
                if (next != visible.toList()) {
                    visible.clear()
                    visible.addAll(next)
                }
            }

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (wallpapers.isEmpty()) {
                            "还没有壁纸，点击右下角 + 添加"
                        } else {
                            "这个壁纸集还是空的，点右下角 + 添加"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                // 正在拖拽的壁纸文件名；null 表示未拖拽
                val draggingKey = remember { mutableStateOf<String?>(null) }
                // 被拖项相对其"当前槽位"的位移（换位时做补偿，避免视觉跳变）
                val dragOffset = remember { mutableStateOf(Offset.Zero) }

                /**
                 * 排序落库：
                 * - 看"全部"时直接写回全局顺序；
                 * - 看某一组时，只重排这一组占用的槽位，其它组的相对位置保持不变
                 *   （组内顺序 = 全局列表里的相对顺序）。
                 */
                fun commitOrder() {
                    draggingKey.value = null
                    dragOffset.value = Offset.Zero
                    val gid = viewGroupId
                    if (gid == null) {
                        wallpapers.clear()
                        wallpapers.addAll(visible)
                    } else {
                        val slots = wallpapers.indices.filter {
                            Prefs.wallpaperGroup(ctx, wallpapers[it]) == gid
                        }
                        if (slots.size == visible.size) {
                            val newGlobal = wallpapers.toMutableList()
                            slots.forEachIndexed { i, slot -> newGlobal[slot] = visible[i] }
                            wallpapers.clear()
                            wallpapers.addAll(newGlobal)
                        }
                        // 槽位数量和可见项对不上（比如正在同步），保持原顺序不动
                    }
                    Prefs.setWallpaperNames(ctx, wallpapers.toList())
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    userScrollEnabled = draggingKey.value == null,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .pointerInput(viewGroupId) {
                            var lastAutoScroll = 0L
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val hit = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.hitTest(pos) }
                                    if (hit != null) {
                                        draggingKey.value = visible.getOrNull(hit.index)
                                        dragOffset.value = Offset.Zero
                                        editing = true
                                    }
                                },
                                onDrag = { change, delta ->
                                    val key = draggingKey.value
                                        ?: return@detectDragGesturesAfterLongPress
                                    dragOffset.value += delta
                                    val info = gridState.layoutInfo
                                    val cur = info.visibleItemsInfo.firstOrNull {
                                        visible.getOrNull(it.index) == key
                                    }
                                    val tgt = info.visibleItemsInfo.firstOrNull {
                                        it.hitTest(change.position)
                                    }
                                    // 命中别的格子：就地搬移，其余项自动让位/补位
                                    if (cur != null && tgt != null && cur.index != tgt.index) {
                                        val moved = visible.removeAt(cur.index)
                                        visible.add(tgt.index, moved)
                                        // 槽位从 cur 换到 tgt，补偿差值让被拖项视觉上留在原位
                                        dragOffset.value += Offset(
                                            (cur.offset.x - tgt.offset.x).toFloat(),
                                            (cur.offset.y - tgt.offset.y).toFloat()
                                        )
                                    }
                                    // 靠近上下边缘时自动滚动
                                    val edge = 56f
                                    val y = change.position.y
                                    val h = size.height.toFloat()
                                    val s = when {
                                        y < edge -> -((edge - y) / edge) * 14f
                                        y > h - edge -> ((y - (h - edge)) / edge) * 14f
                                        else -> 0f
                                    }
                                    val now = System.currentTimeMillis()
                                    if (s != 0f && now - lastAutoScroll > 40) {
                                        lastAutoScroll = now
                                        scope.launch { gridState.scrollBy(s) }
                                        // 内容滚动会让槽位整体位移，同步补偿
                                        dragOffset.value += Offset(0f, s)
                                    }
                                },
                                onDragEnd = { commitOrder() },
                                onDragCancel = { commitOrder() }
                            )
                        }
                ) {
                    items(visible, key = { it }) { name ->
                        val dragging = draggingKey.value == name
                        WallpaperItem(
                            name = name,
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .then(
                                    if (dragging) Modifier else Modifier.animateItem()
                                ),
                            editState = editState,
                            jiggleState = jiggleState,
                            draggingKeyState = draggingKey,
                            dragOffsetState = dragOffset,
                            onUse = { useWallpaper(name) },
                            onPreview = { cropTarget = name },
                            onMoveRequest = { movingWallpaper = name },
                            isCurrent = name == currentName,
                            onDelete = { pendingDelete = name }
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    // ── 裁剪预览页 ──
    cropTarget?.let { name ->
        val size = remember { WallpaperApplier.targetSizePx(ctx) }
        CropDialog(
            name = name,
            aspect = size[0].toFloat() / size[1].toFloat(),
            initial = remember(name) { Prefs.getCrop(ctx, name) },
            onDismiss = { cropTarget = null },
            onConfirm = { crop ->
                cropTarget = null
                if (crop == null) Prefs.clearCrop(ctx, name) else Prefs.setCrop(ctx, name, crop)
                useWallpaper(name)
            }
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除壁纸") },
            text = { Text("确定要删除这张壁纸吗？") },
            confirmButton = {
                TextButton(onClick = {
                    WallpaperStore.remove(ctx, name)
                    Prefs.clearCrop(ctx, name)
                    Prefs.clearWallpaperGroup(ctx, name)
                    WallpaperCache.remove(ctx, name)
                    if (Prefs.getLastName(ctx) == name) Prefs.clearLastName(ctx)
                    wallpapers.remove(name)
                    Prefs.setWallpaperNames(ctx, wallpapers.toList())
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

    // ── 把某张壁纸移到其它壁纸集 ──
    movingWallpaper?.let { name ->
        val owner = Prefs.wallpaperGroup(ctx, name)
        AlertDialog(
            onDismissRequest = { movingWallpaper = null },
            title = { Text("移动到壁纸集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    groups.forEach { g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Prefs.setWallpaperGroup(ctx, name, g.id)
                                    movingWallpaper = null
                                    refresh()
                                    Toast.makeText(ctx, "已移到「${g.name}」", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.name, modifier = Modifier.weight(1f))
                            if (g.id == owner) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { movingWallpaper = null }) { Text("取消") }
            }
        )
    }

    // ── 壁纸集管理 ──
    if (showGroupManager) {
        GroupManagerDialog(
            groups = groups,
            onDismiss = { showGroupManager = false },
            onEdit = { g ->
                showGroupManager = false
                editingGroup = g
            },
            onMove = { g, delta -> moveGroup(g, delta) },
            onDelete = { g -> deleteGroup(g) }
        )
    }

    editingGroup?.let { g ->
        GroupEditDialog(
            group = g,
            onDismiss = { editingGroup = null },
            onConfirm = { updated -> updateGroup(updated) }
        )
    }
}

@Composable
fun WallpaperItem(
    name: String,
    modifier: Modifier = Modifier,
    editState: State<Boolean>,
    jiggleState: State<Float>,
    draggingKeyState: State<String?>,
    dragOffsetState: State<Offset>,
    onUse: () -> Unit,
    onPreview: () -> Unit,
    onMoveRequest: () -> Unit,
    isCurrent: Boolean,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    // 每张固定相位，抖动不同步，观感更接近 iOS 删除模式
    val phase = remember(name) { (name.hashCode() and 0xFF) / 255f }
    // "当前使用"态需要一个无水波交互源，用来吞掉点击又不产生误导性反馈
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            // graphicsLayer 内的状态读取只触发图层更新，不触发重组，保证 60fps
            .graphicsLayer {
                if (draggingKeyState.value == name) {
                    // 被拖项：跟手移动 + 轻微放大
                    translationX = dragOffsetState.value.x
                    translationY = dragOffsetState.value.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                    rotationZ = 0f
                } else if (editState.value) {
                    // 编辑态：左右轻摆
                    rotationZ = sin((jiggleState.value + phase) * (2f * PI.toFloat())) * 1.8f
                }
            }
            .clip(RoundedCornerShape(12.dp))
            // 普通态点开裁剪预览；编辑态点开"移动到其它壁纸集"，
            // 同时避免长按拖拽松手时误触
            .clickable {
                if (editState.value) onMoveRequest() else onPreview()
            }
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

        // 一键使用：半透明胶囊按钮，固定贴在缩略图下方居中。
        // 已经是当前壁纸时显示"当前使用"，并禁用点击。
        Text(
            text = if (isCurrent) "当前使用" else "使用",
            color = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                Color.White
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                    } else {
                        Color(0x59000000)
                    }
                )
                .then(
                    if (isCurrent) {
                        // 已是当前壁纸：吞掉点击，既不重复应用，也不穿透到缩略图打开裁剪页
                        Modifier.clickable(interactionSource = noRipple, indication = null) { }
                    } else {
                        Modifier.clickable(onClick = onUse)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 3.dp)
        )
    }
}

/**
 * 裁剪预览页：视图框比例 = 最终壁纸比例，框内所见即最终效果。
 * 双指捏合缩放、拖动平移，确认后把可见区域换算成归一化矩形持久化。
 */
@Composable
fun CropDialog(
    name: String,
    aspect: Float,
    initial: RectF?,
    onDismiss: () -> Unit,
    onConfirm: (RectF?) -> Unit
) {
    val ctx = LocalContext.current
    val path = remember(name) { WallpaperStore.file(ctx, name).absolutePath }
    val bmp by produceState<Bitmap?>(null, name) {
        // 预览用降采样图即可（1600px），避免大图 OOM；归一化裁剪坐标与分辨率无关
        value = withContext(Dispatchers.IO) { BitmapUtil.decodeScaled(path, 1600) }
    }
    val imageBitmap = remember(bmp) { bmp?.asImageBitmap() }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var cleared by remember { mutableStateOf(false) }
    var inited by remember { mutableStateOf(false) }

    fun coverScale(img: Bitmap, vs: IntSize): Float =
        max(vs.width.toFloat() / img.width, vs.height.toFloat() / img.height)

    fun centerOffset(img: Bitmap, vs: IntSize): Offset {
        val cover = coverScale(img, vs)
        return Offset(
            (vs.width - img.width * cover) / 2f,
            (vs.height - img.height * cover) / 2f
        )
    }

    // 首次布局后：有历史裁剪就还原，否则居中铺满
    LaunchedEffect(bmp, viewSize) {
        val img = bmp ?: return@LaunchedEffect
        if (viewSize.width == 0 || viewSize.height == 0 || inited) return@LaunchedEffect
        val cover = coverScale(img, viewSize)
        val c = initial
        if (c != null && c.width() > 0f && c.height() > 0f) {
            val s = viewSize.width / (c.width() * img.width) // 源图像素 → 视图像素
            zoom = (s / cover).coerceIn(1f, 8f)
            offset = Offset(-c.left * img.width * s, -c.top * img.height * s)
        } else {
            zoom = 1f
            offset = centerOffset(img, viewSize)
        }
        inited = true
    }

    fun currentCrop(): RectF? {
        val img = bmp ?: return null
        if (viewSize.width == 0 || viewSize.height == 0) return null
        val s = coverScale(img, viewSize) * zoom
        val l = (-offset.x) / (img.width * s)
        val t = (-offset.y) / (img.height * s)
        val r = (viewSize.width - offset.x) / (img.width * s)
        val b = (viewSize.height - offset.y) / (img.height * s)
        return RectF(
            l.coerceIn(0f, 1f), t.coerceIn(0f, 1f),
            r.coerceIn(0f, 1f), b.coerceIn(0f, 1f)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("调整裁剪", style = MaterialTheme.typography.titleMedium)

                val screenH = LocalConfiguration.current.screenHeightDp.dp
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                // 预览框取「可用宽度」与「屏幕高度 58%」的较小者：
                // 竖屏壁纸比例约 0.45，若宽度撑满，高度会超过屏幕，把按钮顶到屏幕外
                val previewW = minOf(maxWidth, screenH * 0.58f * aspect)
                Box(
                    modifier = Modifier
                        .width(previewW)
                        .height(previewW / aspect)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        .onSizeChanged { viewSize = it }
                        .pointerInput(bmp, viewSize) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                val img = bmp ?: return@detectTransformGestures
                                if (viewSize.width == 0 || viewSize.height == 0) {
                                    return@detectTransformGestures
                                }
                                val cover = coverScale(img, viewSize)
                                val oldScale = cover * zoom
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                                val newScale = cover * newZoom
                                // 以捏合中心为锚点，缩放时手指下的内容不跑偏
                                val anchor = (centroid - offset) / oldScale
                                val w = img.width * newScale
                                val h = img.height * newScale
                                val nx = (centroid.x - anchor.x * newScale + pan.x)
                                    .coerceIn(viewSize.width - w, 0f)
                                val ny = (centroid.y - anchor.y * newScale + pan.y)
                                    .coerceIn(viewSize.height - h, 0f)
                                zoom = newZoom
                                offset = Offset(nx, ny)
                                cleared = false
                            }
                        }
                ) {
                    val ib = imageBitmap
                    val img = bmp
                    if (ib != null && img != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val s = coverScale(img, viewSize) * zoom
                            drawImage(
                                image = ib,
                                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                                dstSize = IntSize(
                                    (img.width * s).roundToInt(),
                                    (img.height * s).roundToInt()
                                )
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = Color.White)
                        }
                    }
                }
                }

                Text(
                    "双指捏合缩放，拖动调整位置；框内即最终效果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 操作行：居中构图 / 取消 / 应用
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        // 一键回正：缩放回到 1×、位置归中，并清除已保存的裁剪（确认时生效）
                        val img = bmp
                        if (img != null && viewSize.width > 0) {
                            zoom = 1f
                            offset = centerOffset(img, viewSize)
                        }
                        cleared = true
                    }) { Text("居中构图") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = {
                        onConfirm(if (cleared) null else currentCrop())
                    }) { Text("应用") }
                }
            }
        }
    }
}

/** 触点是否落在该格子内（LazyGridItemInfo 只暴露 offset/size，没有 bounds） */
private fun LazyGridItemInfo.hitTest(pos: Offset): Boolean =
    pos.x >= offset.x && pos.x < offset.x + size.width &&
        pos.y >= offset.y && pos.y < offset.y + size.height

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
