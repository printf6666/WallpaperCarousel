package com.example.wallpapercarousel.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.wallpapercarousel.data.Prefs

/**
 * 用 AlarmManager 排定下一次轮换：
 * - 有精确闹钟权限时用 setExactAndAllowWhileIdle
 * - 没有时自动降级 setAndAllowWhileIdle（间隔可能有小幅偏差）
 */
object RotationScheduler {

    const val ACTION_ROTATE = "com.example.wallpapercarousel.ACTION_ROTATE"
    private const val REQUEST_CODE = 1001

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, RotationReceiver::class.java).setAction(ACTION_ROTATE)
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 按"现在 + 间隔"排定下一次轮换；未启用或没有壁纸时取消 */
    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val s = Prefs.load(ctx)
        if (!s.enabled || Prefs.getWallpaperNames(ctx).isEmpty()) {
            cancel(ctx)
            return
        }

        val now = System.currentTimeMillis()
        var triggerAt = now + s.intervalMinutes * 60_000L

        // 开着自动切组：如果下一次"规则切换点"比正常轮换更早，
        // 就在切换点补一次闹钟，让白天/夜晚组按点生效，而不是等下一个间隔。
        if (s.autoSwitch) {
            GroupResolver.nextBoundary(Prefs.groups(ctx), now)?.let { boundary ->
                if (boundary in (now + 5_000)..triggerAt) triggerAt = boundary
            }
        }

        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(ctx))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(ctx))
        }
        Prefs.setNextTriggerAt(ctx, triggerAt)
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
        Prefs.setNextTriggerAt(ctx, 0L)
    }

    /** 设置变化后调用：重新排定或取消 */
    fun onSettingsChanged(ctx: Context) = scheduleNext(ctx)
}
