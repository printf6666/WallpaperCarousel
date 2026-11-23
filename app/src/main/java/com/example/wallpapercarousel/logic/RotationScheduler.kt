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
        val triggerAt = System.currentTimeMillis() + s.intervalMinutes * 60_000L
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
