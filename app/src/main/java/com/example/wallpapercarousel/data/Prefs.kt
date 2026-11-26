package com.example.wallpapercarousel.data

import android.content.Context
import android.graphics.RectF
import org.json.JSONArray

/**
 * 设置持久化：SharedPreferences + JSON
 */
object Prefs {

    data class Settings(
        val enabled: Boolean = false,
        val intervalMinutes: Int = 30,
        val randomOrder: Boolean = false,
    )

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("wallpaper_carousel", Context.MODE_PRIVATE)

    fun load(ctx: Context) = Settings(
        enabled = sp(ctx).getBoolean("enabled", false),
        intervalMinutes = sp(ctx).getInt("interval_minutes", 30),
        randomOrder = sp(ctx).getBoolean("random_order", false),
    )

    fun save(ctx: Context, s: Settings) {
        sp(ctx).edit()
            .putBoolean("enabled", s.enabled)
            .putInt("interval_minutes", s.intervalMinutes)
            .putBoolean("random_order", s.randomOrder)
            .apply()
    }

    /** 壁纸文件名有序列表 */
    fun getWallpaperNames(ctx: Context): List<String> = try {
        val arr = JSONArray(sp(ctx).getString("wallpapers", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun setWallpaperNames(ctx: Context, names: List<String>) {
        sp(ctx).edit().putString("wallpapers", JSONArray(names).toString()).apply()
    }

    fun getLastIndex(ctx: Context) = sp(ctx).getInt("last_index", -1)
    fun setLastIndex(ctx: Context, i: Int) = sp(ctx).edit().putInt("last_index", i).apply()

    fun getNextTriggerAt(ctx: Context) = sp(ctx).getLong("next_trigger_at", 0L)
    fun setNextTriggerAt(ctx: Context, t: Long) = sp(ctx).edit().putLong("next_trigger_at", t).apply()

    /** 最近一次应用壁纸的位图尺寸，用于排查"壁纸被放大"类问题 */
    fun getLastApplied(ctx: Context) = sp(ctx).getString("last_applied", "") ?: ""
    fun setLastApplied(ctx: Context, v: String) =
        sp(ctx).edit().putString("last_applied", v).apply()

    // ── 手动裁剪区域 ──
    // 存归一化矩形（0..1，相对源图），与图片分辨率无关，换机/重装都能用
    fun getCrop(ctx: Context, name: String): RectF? {
        val raw = sp(ctx).getString("crop_$name", null) ?: return null
        val p = raw.split(",")
        if (p.size != 4) return null
        return try {
            val l = p[0].toFloat()
            val t = p[1].toFloat()
            val r = p[2].toFloat()
            val b = p[3].toFloat()
            if (r <= l || b <= t) null else RectF(l, t, r, b)
        } catch (e: Exception) {
            null
        }
    }

    fun setCrop(ctx: Context, name: String, r: RectF) = sp(ctx).edit()
        .putString("crop_$name", "${r.left},${r.top},${r.right},${r.bottom}")
        .apply()

    fun clearCrop(ctx: Context, name: String) = sp(ctx).edit().remove("crop_$name").apply()
}
