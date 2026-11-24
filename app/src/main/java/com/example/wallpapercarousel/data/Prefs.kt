package com.example.wallpapercarousel.data

import android.content.Context
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
}
