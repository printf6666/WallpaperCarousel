package com.example.wallpapercarousel.data

import android.content.Context
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 设置持久化：SharedPreferences + JSON
 */
object Prefs {

    /** 组内/全部播放：顺序 */
    const val MODE_SEQUENTIAL = 0

    /** 组内/全部播放：随机 */
    const val MODE_RANDOM = 1

    /** 找不到任何组时的兜底组 id（同时也是旧版本的迁移目标） */
    private const val DEFAULT_GROUP_ID = "g_default"

    data class Settings(
        val enabled: Boolean = false,
        val intervalMinutes: Int = 30,
        /** 「全部播放」时的切换方式；分组播放时用各组的 mode */
        val randomOrder: Boolean = false,
        /** false = 分组播放（一次只用一组），true = 全部播放（所有壁纸一个池子） */
        val scopeAll: Boolean = false,
        /** 分组播放时，是否按每组的星期/时段规则自动切组 */
        val autoSwitch: Boolean = false,
    )

    /**
     * 一组壁纸（壁纸集 / 专辑）。
     *
     * 组内顺序不单独存，取壁纸在全局有序列表中的相对顺序 —— 这样拖拽排序、添加删除
     * 都只需要维护一份列表，不会出现两份顺序互相打架。
     */
    data class Group(
        val id: String,
        val name: String,
        /** 组内切换方式：MODE_SEQUENTIAL / MODE_RANDOM */
        val mode: Int = MODE_SEQUENTIAL,
        /** 生效星期：1=周一 … 7=周日；空集合表示每天 */
        val days: Set<Int> = emptySet(),
        /** 生效时段起点（当天第几分钟，0..1439），-1 表示不限 */
        val startMin: Int = -1,
        /** 生效时段终点，-1 表示不限；start > end 表示跨天（如 19:00→07:00） */
        val endMin: Int = -1,
    ) {
        /** 是否配置了自动生效规则；没规则的组只能被手动选用 */
        val hasRule: Boolean
            get() = days.isNotEmpty() || (startMin >= 0 && endMin >= 0 && startMin != endMin)

        /** 这个时刻是否命中本组规则（不含「无规则」的兜底情况） */
        fun matches(cal: Calendar): Boolean {
            if (!hasRule) return false
            if (days.isNotEmpty()) {
                // Calendar：1=周日 … 7=周六 → 统一成 1=周一 … 7=周日
                val dow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                if (dow !in days) return false
            }
            if (startMin >= 0 && endMin >= 0 && startMin != endMin) {
                val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val inWindow = if (startMin < endMin) {
                    m >= startMin && m < endMin
                } else {
                    m >= startMin || m < endMin
                }
                if (!inWindow) return false
            }
            return true
        }
    }

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("wallpaper_carousel", Context.MODE_PRIVATE)

    // ── 基本设置 ──

    fun load(ctx: Context) = Settings(
        enabled = sp(ctx).getBoolean("enabled", false),
        intervalMinutes = sp(ctx).getInt("interval_minutes", 30),
        randomOrder = sp(ctx).getBoolean("random_order", false),
        scopeAll = sp(ctx).getBoolean("scope_all", false),
        autoSwitch = sp(ctx).getBoolean("auto_switch", false),
    )

    fun save(ctx: Context, s: Settings) {
        sp(ctx).edit()
            .putBoolean("enabled", s.enabled)
            .putInt("interval_minutes", s.intervalMinutes)
            .putBoolean("random_order", s.randomOrder)
            .putBoolean("scope_all", s.scopeAll)
            .putBoolean("auto_switch", s.autoSwitch)
            .apply()
    }

    // ── 壁纸列表 ──

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

    /** 上次实际应用的那张（按文件名记，列表增删换序都不会失效） */
    fun getLastName(ctx: Context): String? = sp(ctx).getString("last_name", null)

    fun setLastName(ctx: Context, name: String) =
        sp(ctx).edit().putString("last_name", name).apply()

    fun clearLastName(ctx: Context) = sp(ctx).edit().remove("last_name").apply()

    /** 旧版本按索引记位置，仅在 last_name 缺失时作为迁移依据 */
    fun getLastIndex(ctx: Context) = sp(ctx).getInt("last_index", -1)
    fun setLastIndex(ctx: Context, i: Int) = sp(ctx).edit().putInt("last_index", i).apply()

    fun getNextTriggerAt(ctx: Context) = sp(ctx).getLong("next_trigger_at", 0L)
    fun setNextTriggerAt(ctx: Context, t: Long) = sp(ctx).edit().putLong("next_trigger_at", t).apply()

    /** 最近一次应用壁纸的位图尺寸，用于排查"壁纸被放大"类问题 */
    fun getLastApplied(ctx: Context) = sp(ctx).getString("last_applied", "") ?: ""
    fun setLastApplied(ctx: Context, v: String) =
        sp(ctx).edit().putString("last_applied", v).apply()

    // ── 壁纸集（分组） ──

    /** 进程内缓存：分组结构小、读取频繁（每张壁纸都要查归属），避免反复解析 JSON */
    @Volatile
    private var groupCache: List<Group>? = null

    /** 全部分组，按列表顺序 = 优先级顺序；至少返回一个默认组 */
    fun groups(ctx: Context): List<Group> {
        groupCache?.let { return it }

        val raw = sp(ctx).getString("groups", null)
        val parsed = runCatching { raw?.let { parseGroups(it) } }.getOrNull().orEmpty()
        // 旧版本没有 groups：迁移出一个默认组，已有壁纸全部归入其中
        val result = parsed.ifEmpty { listOf(Group(DEFAULT_GROUP_ID, "全部壁纸")) }
        if (parsed.isEmpty()) persistGroups(ctx, result)

        groupCache = result
        return result
    }

    fun saveGroups(ctx: Context, groups: List<Group>) {
        val fixed = groups.ifEmpty { listOf(Group(DEFAULT_GROUP_ID, "全部壁纸")) }
        persistGroups(ctx, fixed)
        groupCache = fixed
        // 当前组被删掉时兜底到第一个
        if (currentGroupId(ctx) !in fixed.map { it.id }) {
            setCurrentGroupId(ctx, fixed.first().id)
        }
    }

    private fun persistGroups(ctx: Context, groups: List<Group>) {
        sp(ctx).edit().putString("groups", serializeGroups(groups)).apply()
    }

    /** 当前正在用的组（手动切换或自动解析后写入） */
    fun currentGroupId(ctx: Context): String {
        val ids = groups(ctx).map { it.id }
        val saved = sp(ctx).getString("current_group", null)
        return if (saved != null && saved in ids) saved else ids.first()
    }

    fun setCurrentGroupId(ctx: Context, id: String) =
        sp(ctx).edit().putString("current_group", id).apply()

    /** 某张壁纸属于哪个组；查不到（旧数据）就落到第一个组 */
    fun wallpaperGroup(ctx: Context, name: String): String {
        val ids = groups(ctx).map { it.id }
        val saved = sp(ctx).getString("grp_$name", null)
        return if (saved != null && saved in ids) saved else ids.first()
    }

    fun setWallpaperGroup(ctx: Context, name: String, groupId: String) =
        sp(ctx).edit().putString("grp_$name", groupId).apply()

    fun clearWallpaperGroup(ctx: Context, name: String) =
        sp(ctx).edit().remove("grp_$name").apply()

    private fun parseGroups(raw: String): List<Group> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            if (id.isBlank()) return@mapNotNull null
            val daysArr = o.optJSONArray("days")
            val days = if (daysArr == null) {
                emptySet()
            } else {
                (0 until daysArr.length()).map { daysArr.optInt(it) }.filter { it in 1..7 }.toSet()
            }
            Group(
                id = id,
                name = o.optString("name", "未命名"),
                mode = if (o.optInt("mode", MODE_SEQUENTIAL) == MODE_RANDOM) MODE_RANDOM else MODE_SEQUENTIAL,
                days = days,
                startMin = o.optInt("startMin", -1),
                endMin = o.optInt("endMin", -1),
            )
        }
    }

    private fun serializeGroups(groups: List<Group>): String {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("mode", g.mode)
                put("days", JSONArray(g.days.sorted()))
                put("startMin", g.startMin)
                put("endMin", g.endMin)
            })
        }
        return arr.toString()
    }

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
