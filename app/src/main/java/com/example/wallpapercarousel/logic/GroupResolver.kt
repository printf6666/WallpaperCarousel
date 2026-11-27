package com.example.wallpapercarousel.logic

import com.example.wallpapercarousel.data.Prefs
import java.util.Calendar

/**
 * 壁纸集的自动切换规则。
 *
 * 规则很朴素：每个组可以限定「星期」和「时间段」，
 * - 多个组同时命中时，排在前面的组优先（列表顺序就是优先级）；
 * - 一个都没命中时，保持当前组不动（不会把用户手动选的组挤掉）；
 * - 没有配置规则的组不参与自动切换，只能手动选。
 */
object GroupResolver {

    /** 当前时刻命中的组（按列表顺序取第一个）；都不命中返回 null */
    fun matching(groups: List<Prefs.Group>): Prefs.Group? =
        matching(groups, Calendar.getInstance())

    fun matching(groups: List<Prefs.Group>, cal: Calendar): Prefs.Group? =
        groups.firstOrNull { it.hasRule && it.matches(cal) }

    private fun activeId(groups: List<Prefs.Group>, cal: Calendar): String? =
        matching(groups, cal)?.id

    /**
     * 下一次"生效组发生变化"的时刻（毫秒）；8 天内没有变化则返回 null。
     *
     * 用途：间隔设成几小时/一天时，如果只靠轮换闹钟去解析规则，
     * 早上 8 点该切到白天组，可能要等到中午换壁纸才生效。
     * 这里算出切换点，让调度器额外在那个时刻补一次闹钟。
     *
     * 按分钟步进扫描，最多 8 天（约 1.1 万次轻量比较），只在排闹钟时算一次；
     * 全程复用同一个 Calendar，避免大量临时对象。
     */
    fun nextBoundary(groups: List<Prefs.Group>, fromMillis: Long): Long? {
        if (groups.none { it.hasRule }) return null

        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startId = activeId(groups, cal)
        val limit = fromMillis + 8L * 24 * 60 * 60 * 1000

        // 从下一分钟开始逐分钟检查，一旦生效组变了，变化就发生在这一步之前
        while (true) {
            cal.add(Calendar.MINUTE, 1)
            val at = cal.timeInMillis
            if (at > limit) return null
            if (activeId(groups, cal) != startId) return at
        }
    }
}
