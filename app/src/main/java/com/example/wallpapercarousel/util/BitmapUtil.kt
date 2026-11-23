package com.example.wallpapercarousel.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File

object BitmapUtil {

    /**
     * 解码图片，并按 cover 方式居中裁剪+缩放到 targetW × targetH。
     *
     * 输出的位图尺寸严格等于目标尺寸，交给壁纸服务后系统无需再做任何缩放/裁剪，
     * 从而避免"宽图靠左""被系统放大数倍"等与厂商实现相关的差异。
     */
    fun decodeCenterCrop(file: File, targetW: Int, targetH: Int): Bitmap? {
        if (!file.exists() || targetW <= 0 || targetH <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 采样到"刚好不小于目标尺寸"，兼顾清晰度与内存
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW &&
            bounds.outHeight / (sample * 2) >= targetH
        ) {
            sample *= 2
        }

        var decoded: Bitmap? = null
        var s = sample
        while (decoded == null && s <= 64) {
            decoded = try {
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = s
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                )
            } catch (e: OutOfMemoryError) {
                null
            }
            if (decoded == null) s *= 2
        }
        val src = decoded ?: return null

        try {
            val srcAspect = src.width.toDouble() / src.height
            val tgtAspect = targetW.toDouble() / targetH

            val srcRect: Rect
            if (srcAspect > tgtAspect) {
                // 图片相对更宽：左右居中裁剪（如 1680x2160 → 屏 1080x2160 时取 [300,0]-[1380,2160]）
                val cropW = (src.height * tgtAspect).toInt().coerceIn(1, src.width)
                val left = (src.width - cropW) / 2
                srcRect = Rect(left, 0, left + cropW, src.height)
            } else {
                // 图片相对更高：上下居中裁剪
                val cropH = (src.width / tgtAspect).toInt().coerceIn(1, src.height)
                val top = (src.height - cropH) / 2
                srcRect = Rect(0, top, src.width, top + cropH)
            }

            val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(
                src,
                srcRect,
                RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            return out
        } catch (e: Throwable) {
            return null
        } finally {
            src.recycle()
        }
    }

    /** 列表缩略图解码 */
    fun decodeScaled(path: String, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqSize && bounds.outHeight / (sample * 2) >= reqSize) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }
}