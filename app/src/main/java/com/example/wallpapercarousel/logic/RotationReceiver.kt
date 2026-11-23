package com.example.wallpapercarousel.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 闹钟到期：换壁纸，然后排定下一次 */
class RotationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RotationScheduler.ACTION_ROTATE) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WallpaperApplier.applyNext(context, reschedule = true)
            } catch (e: Exception) {
                Log.e("RotationReceiver", "rotate failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
