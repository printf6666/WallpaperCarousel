package com.example.wallpapercarousel.logic

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.util.Log
import android.view.WindowManager
import com.example.wallpapercarousel.data.Prefs
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.util.BitmapUtil
import java.io.File
import kotlin.random.Random

/**
 * 选下一张并应用到系统壁纸
 */
object WallpaperApplier {

    private const val TAG = "WallpaperApplier"

    /** 串行化设置操作，避免定时任务与手动点击并发写壁纸导致异常 */
    private val applyLock = Any()

    /**
     * 应用下一张壁纸。随机模式避免与上一张重复。
     * @return 是否成功应用（没有可用壁纸或设置失败时返回 false）
     */
    fun applyNext(context: Context, reschedule: Boolean = false): Boolean {
        val s = Prefs.load(context)
        val names = Prefs.getWallpaperNames(context)
            .filter { WallpaperStore.file(context, it).exists() }
        if (names.isEmpty()) return false

        val last = Prefs.getLastIndex(context)
        val idx = if (s.randomOrder && names.size > 1) {
            var r = Random.nextInt(names.size)
            if (r == last) r = (r + 1) % names.size
            r
        } else {
            (last + 1) % names.size
        }

        val ok = apply(context, WallpaperStore.file(context, names[idx]))
        if (ok) Prefs.setLastIndex(context, idx)

        if (reschedule) RotationScheduler.scheduleNext(context)
        return ok
    }

    /**
     * 把一张图片设置为桌面壁纸（仅桌面，不修改锁屏壁纸）。
     *
     * 做法：自行把图片按屏幕比例居中裁剪并缩放到"正好等于屏幕分辨率"，
     * 再以空裁剪提示交给系统。位图与屏幕 1:1，系统没有任何自行缩放的空间，
     * 不会出现放大数倍或靠左显示的问题。
     */
    fun apply(context: Context, file: File): Boolean = synchronized(applyLock) {
        val screen = realScreenSize(context)
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val bmp = try {
                BitmapUtil.decodeCenterCrop(file, screen[0], screen[1])
            } catch (t: Throwable) {
                Log.e(TAG, "decode failed", t)
                null
            } ?: return@synchronized false

            try {
                val wm = WallpaperManager.getInstance(context)
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
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

    /** 真实屏幕分辨率（含状态栏/导航栏背后的区域） */
    @Suppress("DEPRECATION")
    private fun realScreenSize(context: Context): IntArray {
        val fallback = context.resources.displayMetrics
            .let { intArrayOf(it.widthPixels, it.heightPixels) }
        return try {
            val p = Point()
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealSize(p)
            if (p.x > 0 && p.y > 0) intArrayOf(p.x, p.y) else fallback
        } catch (e: Exception) {
            fallback
        }
    }
}