package com.example.wallpapercarousel.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import kotlin.math.abs

object BitmapUtil {

    /**
     * 解码图片，并按 cover 方式裁剪+缩放到 targetW × targetH。
     *
     * @param crop 手动指定的裁剪区域（归一化 0..1，相对源图）；为 null 时居中裁剪。
     *
     * 输出的位图尺寸严格等于目标尺寸，交给壁纸服务后系统无需再做任何缩放/裁剪，
     * 从而避免"宽图靠左""被系统放大数倍"等与厂商实现相关的差异。
     */
    fun decodeCenterCrop(
        file: File,
        targetW: Int,
        targetH: Int,
        crop: RectF? = null
    ): Bitmap? {
        if (!file.exists() || targetW <= 0 || targetH <= 0) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 先按目标宽高比算出真正会用到的裁剪区域，再按"裁剪区域"决定采样率。
        // 之前按整图宽高判断：遇到超长图（如 8000×1000 全景）第二维早早不满足条件，
        // 采样会退化成 1，整图解码进内存 —— 既慢又容易 OOM。
        val cropRect = sourceRect(bounds.outWidth, bounds.outHeight, targetW, targetH, crop)
        var sample = 1
        while (cropRect.width() / (sample * 2) >= targetW &&
            cropRect.height() / (sample * 2) >= targetH
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
                        // 壁纸不需要透明通道，RGB_565 省一半内存
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                )
            } catch (e: OutOfMemoryError) {
                null
            }
            if (decoded == null) s *= 2
        }
        val src = decoded ?: return null

        try {
            // 解码后的实际尺寸可能与 bounds/sample 略有出入，按解码后的尺寸再算一次
            val srcRect = sourceRect(src.width, src.height, targetW, targetH, crop)

            val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
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

    /**
     * 按目标宽高比，算出源图上需要使用的像素矩形：
     * 有手动裁剪就用裁剪区域（并按比例校正），否则居中 cover 裁剪。
     */
    private fun sourceRect(
        w: Int,
        h: Int,
        targetW: Int,
        targetH: Int,
        crop: RectF?
    ): Rect {
        val tgtAspect = targetW.toDouble() / targetH
        if (w <= 0 || h <= 0) return Rect(0, 0, 1, 1)

        if (crop != null) {
            // 用户手动选定的裁剪区域，按目标宽高比做一次校正后使用
            return normalizeCrop(crop, w, h, tgtAspect)
        }

        val srcAspect = w.toDouble() / h
        return if (srcAspect > tgtAspect) {
            // 图片相对更宽：左右居中裁剪（如 1680x2160 → 屏 1080x2160 时取 [300,0]-[1380,2160]）
            val cropW = (h * tgtAspect).toInt().coerceIn(1, w)
            val left = (w - cropW) / 2
            Rect(left, 0, left + cropW, h)
        } else {
            // 图片相对更高：上下居中裁剪
            val cropH = (w / tgtAspect).toInt().coerceIn(1, h)
            val top = (h - cropH) / 2
            Rect(0, top, w, top + cropH)
        }
    }

    /**
     * 把归一化裁剪矩形换算成源图像素矩形，并校正为目标宽高比。
     * 用户手动选定的区域比例理论上已等于目标比例，这里做一次兜底，
     * 防止存储值被外部改动或屏幕方向变化导致输出变形。
     */
    private fun normalizeCrop(crop: RectF, w: Int, h: Int, tgtAspect: Double): Rect {
        var l = (crop.left * w).toInt().coerceIn(0, w - 1)
        var t = (crop.top * h).toInt().coerceIn(0, h - 1)
        var r = (crop.right * w).toInt().coerceIn(l + 1, w)
        var b = (crop.bottom * h).toInt().coerceIn(t + 1, h)

        val cw = r - l
        val ch = b - t
        if (ch <= 0 || cw <= 0) return Rect(0, 0, w, h)

        val cur = cw.toDouble() / ch
        if (abs(cur - tgtAspect) > 0.01) {
            if (cur > tgtAspect) {
                // 太宽：以中心为基准收窄
                val nw = (ch * tgtAspect).toInt().coerceIn(1, w)
                val cx = (l + r) / 2
                l = (cx - nw / 2).coerceIn(0, maxOf(0, w - nw))
                r = l + nw
            } else {
                // 太高：以中心为基准压低
                val nh = (cw / tgtAspect).toInt().coerceIn(1, h)
                val cy = (t + b) / 2
                t = (cy - nh / 2).coerceIn(0, maxOf(0, h - nh))
                b = t + nh
            }
        }
        return Rect(l, t, r, b)
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