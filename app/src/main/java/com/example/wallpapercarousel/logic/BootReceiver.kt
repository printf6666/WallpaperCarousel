package com.example.wallpapercarousel.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wallpapercarousel.data.Prefs

/** 开机完成：如果之前开着轮换，重新排定闹钟 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.load(context).enabled) {
            RotationScheduler.scheduleNext(context)
        }
    }
}
