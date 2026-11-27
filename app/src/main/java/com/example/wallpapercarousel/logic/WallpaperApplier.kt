package com.example.wallpapercarousel.logic

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.example.wallpapercarousel.data.Prefs
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.util.BitmapUtil
import java.io.File
import java.io.FileInputStream

/**
 * 选下一张并应用到系统壁纸（仅桌面）
 */
object WallpaperApplier {

    private const val TAG = "WallpaperApplier"

    /** 串行化设置操作，避免定时任务与手动点击并发写壁纸导致异常 */
    private val applyLock = Any()

    /**
     * 应用下一张壁纸。
     *
     * 播放范围：
     * - 分组播放：只在当前组里挑；开了「自动切组」时先按星期/时段规则解析当前组；
     * - 全部播放：所有壁纸一个池子，忽略分组。
     *
     * 切换方式：分组播放用组自己的 mode，全部播放用设置里的 randomOrder。
     * 随机模式不会连续重复同一张；顺序模式从"上次应用的那张"往后走一格。
     */
    fun applyNext(context: Context, reschedule: Boolean = false): Boolean {
        val s = Prefs.load(context)
        val groups = Prefs.groups(context)

        // 自动模式：命中规则的组优先，没命中就保持用户手动选的那组
        if (!s.scopeAll && s.autoSwitch) {
            val resolved = GroupResolver.matching(groups)?.id
            if (resolved != null && resolved != Prefs.currentGroupId(context)) {
                Log.i(TAG, "auto switch group -> $resolved")
                Prefs.setCurrentGroupId(context, resolved)
            }
        }
        val groupId = Prefs.currentGroupId(context)

        val all = Prefs.getWallpaperNames(context)
            .filter { WallpaperStore.file(context, it).exists() }
        if (all.isEmpty()) return false

        val groupIndex = groups.mapIndexed { i, g -> g.id to i }.toMap()
        val pool = if (s.scopeAll) {
            // 全部播放也遵守"组与组之间的顺序"：先按组分块，块内保持全局顺序
            all.sortedBy { groupIndex[Prefs.wallpaperGroup(context, it)] ?: Int.MAX_VALUE }
        } else {
            val inGroup = all.filter { Prefs.wallpaperGroup(context, it) == groupId }
            // 组里还没放图时不空转，退回全部，用户加完图后自动恢复正常
            if (inGroup.isEmpty()) all else inGroup
        }

        val random = if (s.scopeAll) {
            s.randomOrder
        } else {
            groups.firstOrNull { it.id == groupId }?.mode == Prefs.MODE_RANDOM
        }

        val lastName = Prefs.getLastName(context)
        val idx = when {
            random && pool.size > 1 -> {
                // 排除上一张，避免连续重复
                val candidates = pool.filter { it != lastName }
                pool.indexOf(candidates.random())
            }
            else -> {
                val li = pool.indexOf(lastName)
                if (li < 0) 0 else (li + 1) % pool.size
            }
        }

        val name = pool[idx]
        val ok = apply(context, WallpaperStore.file(context, name), Prefs.getCrop(context, name))
        if (ok) {
            Prefs.setLastName(context, name)
            // 同步更新旧索引，兼容仍按索引读取的历史逻辑
            val gi = Prefs.getWallpaperNames(context).indexOf(name)
            if (gi >= 0) Prefs.setLastIndex(context, gi)
        }

        if (reschedule) RotationScheduler.scheduleNext(context)
        return ok
    }

    /**
     * 把一张图片设置为桌面壁纸（仅桌面，不修改锁屏壁纸）。
     *
     * 做法：自行把图片按屏幕比例居中裁剪并缩放到"正好等于屏幕分辨率"，
     * 再以空裁剪提示交给系统。位图与屏幕 1:1，系统没有缩放空间，
     * 桌面渲染时 scale 恒为 1，不会出现放大数倍或靠左显示的问题。
     *
     * 性能：优先使用预渲染好的成品图（见 [WallpaperCache]），以文件流形式交给系统，
     * 本进程不再解大图；缓存不可用时才回退到内存位图路径，并在成功后补一份缓存。
     */
    fun apply(context: Context, file: File, crop: RectF? = null): Boolean = synchronized(applyLock) {
        val wm = WallpaperManager.getInstance(context)
        val target = targetSize(context)
        val started = SystemClock.elapsedRealtime()

        // ① 快路径：预生成成品图 → setStream
        val cached = try {
            WallpaperCache.ensure(context, file, crop)
        } catch (t: Throwable) {
            Log.w(TAG, "prepare cache failed", t)
            null
        }
        if (cached != null) {
            try {
                FileInputStream(cached).use { ins ->
                    wm.setStream(ins, null, true, WallpaperManager.FLAG_SYSTEM)
                }
                recordApplied(
                    context, wm, target,
                    "cache ${SystemClock.elapsedRealtime() - started}ms ${cached.length() / 1024}KB"
                )
                return@synchronized true
            } catch (t: Throwable) {
                Log.w(TAG, "setStream failed, fallback to bitmap", t)
                // 文件可能损坏或被系统判为不可用，删掉让下次重新生成
                cached.delete()
            }
        }

        // ② 兜底：内存位图路径（原实现）
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val bmp = try {
                BitmapUtil.decodeCenterCrop(file, target[0], target[1], crop)
            } catch (t: Throwable) {
                Log.e(TAG, "decode failed", t)
                null
            } ?: return@synchronized false

            try {
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
                recordApplied(
                    context, wm, target,
                    "bitmap ${SystemClock.elapsedRealtime() - started}ms"
                )
                // 顺手把这张位图存成缓存，下一次就能走快路径
                try {
                    WallpaperCache.saveFromBitmap(context, file, crop, bmp)
                } catch (t: Throwable) {
                    Log.w(TAG, "save cache after bitmap apply failed", t)
                }
                return@synchronized true
            } catch (t: Throwable) {
                Log.e(TAG, "set wallpaper failed (attempt $attempt)", t)
            }

            // 不主动 recycle：交给 GC，避免个别 ROM 异步读取位图时损坏壁纸
            if (attempt < 2) {
                try {
                    Thread.sleep(700)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        false
    }

    /** 记录本次应用的尺寸、耗时与系统回读结果（用于排查 ROM 二次裁剪） */
    private fun recordApplied(context: Context, wm: WallpaperManager, target: IntArray, via: String) {
        val applied = "${target[0]}×${target[1]}"
        // 读回系统实际存储的尺寸：不一致说明该 ROM 又做了二次裁剪/缩放
        val stored = storedWallpaperSize(wm)
        val storedText = stored?.let { "${it[0]}×${it[1]}" }
        Log.i(
            TAG,
            "applied=$applied via=$via stored=$storedText " +
                "desired=${wm.desiredMinimumWidth}x${wm.desiredMinimumHeight}"
        )
        Prefs.setLastApplied(
            context,
            when {
                storedText == null -> "$applied（屏幕 ${target[0]}×${target[1]}）"
                storedText == applied -> "$applied ✓"
                else -> "$applied → 系统存储 $storedText ⚠"
            }
        )
    }

    /**
     * 读回系统实际存储的壁纸位图尺寸（只读文件头，不解码像素）。
     * 用于验证 setBitmap 之后 ROM 有没有再动手脚，失败返回 null。
     */
    private fun storedWallpaperSize(wm: WallpaperManager): IntArray? = try {
        wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                intArrayOf(opts.outWidth, opts.outHeight)
            } else {
                null
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "read back wallpaper size failed", t)
        null
    }

    /**
     * 壁纸位图的尺寸：严格等于屏幕分辨率（自然方向），不使用其它任何来源。
     *
     * 系统绘制桌面壁纸时按 scale = max(屏宽/图宽, 屏高/图高) 缩放并居中绘制：
     * - 图与屏幕同尺寸 → scale = 1，画面 1:1，正常；
     * - 图比屏幕宽（例如 2 倍宽）→ scale 仍为 1（由高度决定），但只显示中间一段，
     *   观感就是"被放大了两倍"。
     *
     * 特别注意 **不能** 用 WallpaperManager.getDesiredMinimumWidth/Height()：
     * ColorOS 启动器会声明约 2 倍屏宽的期望值（表示壁纸"可滚动"），但它实际渲染
     * 并不滚动而是居中裁剪，按该值出图会导致整张壁纸被放大一倍。
     * 期望值只写进日志和界面诊断，不参与计算。
     *
     * 尺寸还必须与当前旋转方向无关：自动轮换在后台广播里执行，可能正处于横屏应用
     * 甚至息屏状态，若按"当前旋转方向"取尺寸就会生成一张横图（如 2400×1080），
     * 铺到竖屏桌面上会被放大 2 倍以上 —— 这正是旧版本"概率性放大"的来源。
     */
    private fun targetSize(context: Context): IntArray = naturalScreenSize(context)

    /** 壁纸目标尺寸（宽, 高），供裁剪预览页使用同一比例 */
    fun targetSizePx(context: Context): IntArray = naturalScreenSize(context)

    /**
     * 屏幕分辨率（自然方向：竖屏设备返回 宽<=高），与当前旋转方向无关。
     *
     * 依次尝试：Display.getRealMetrics（逻辑真实尺寸，含系统栏区域）
     * → Display.getMode（物理面板尺寸）→ displayMetrics 兜底。
     * 不使用 WindowManager.defaultDisplay：后台/息屏上下文里它可能抛异常
     * 或返回旋转后的尺寸。
     */
    @Suppress("DEPRECATION")
    private fun naturalScreenSize(context: Context): IntArray {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = try {
            dm?.getDisplay(Display.DEFAULT_DISPLAY)
        } catch (t: Throwable) {
            Log.w(TAG, "getDisplay failed", t)
            null
        }

        // 面板本身的方向（横屏平板为 true），决定"自然方向"是宽>高还是宽<=高
        val panelLandscape = try {
            val m = display?.mode
            m != null && m.physicalWidth > m.physicalHeight
        } catch (t: Throwable) {
            false
        }

        // 1) 逻辑真实尺寸：包含状态栏/导航栏背后的区域，最贴近桌面实际可用范围
        if (display != null) {
            try {
                val m = DisplayMetrics()
                display.getRealMetrics(m)
                if (m.widthPixels > 0 && m.heightPixels > 0) {
                    return orient(m.widthPixels, m.heightPixels, panelLandscape)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "getRealMetrics failed", t)
            }
        }

        // 2) 物理面板分辨率（天然就是自然方向）
        try {
            val mode = display?.mode
            if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                return orient(mode.physicalWidth, mode.physicalHeight, panelLandscape)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getMode failed", t)
        }

        // 3) 兜底：资源里的显示尺寸（可能不含系统栏，但方向正确）
        val fallback = context.resources.displayMetrics
        return orient(fallback.widthPixels, fallback.heightPixels, panelLandscape)
    }

    /** 按自然方向整理宽高：竖屏设备保证 宽<=高，横屏平板保证 宽>高 */
    private fun orient(w: Int, h: Int, landscape: Boolean): IntArray =
        if (landscape == (w > h)) intArrayOf(w, h) else intArrayOf(h, w)
}
