package com.example.wallpapercarousel.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.wallpapercarousel.data.Prefs
import com.example.wallpapercarousel.data.WallpaperStore
import com.example.wallpapercarousel.util.BitmapUtil
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 成品壁纸缓存。
 *
 * 把"解码原图 → 裁剪 → 缩放到屏幕尺寸"的结果预渲染成一张 JPEG 存在私有目录，
 * 换壁纸时直接把文件流交给系统（WallpaperManager.setStream），
 * 这样每次闹钟触发都不用在本进程里解一张 4K 原图：
 * - 峰值内存从"原图 + 全屏位图"（可达三四十 MB）降到接近 0（系统自己流式解码）；
 * - 换图耗时从数百毫秒降到几十毫秒，低端机上尤其明显。
 *
 * 有效性判断不靠额外的元数据文件，而是把签名写进文件名：
 * 渲染版本 + 目标尺寸 + 源图长度/修改时间 + 裁剪区域。
 * 任何一项变了都会生成新文件，旧文件随即删掉，天然不会读到过期缓存。
 */
object WallpaperCache {

    private const val TAG = "WallpaperCache"

    /** 渲染算法版本：改动 decodeCenterCrop 的像素逻辑时递增，让旧缓存自动失效 */
    private const val RENDER_VERSION = 1

    /** 95 足够肉眼无损，又能把体积压到 1~3 MB */
    private const val JPEG_QUALITY = 95

    /** 壁纸文件名与签名之间的分隔符，也用于按前缀找同一张图的所有缓存 */
    private const val SEP = "__"

    private fun dir(ctx: Context) = File(ctx.filesDir, "applied").apply { mkdirs() }

    /** 该壁纸名下的所有缓存文件（含历史版本） */
    private fun filesOf(ctx: Context, wallpaperName: String): List<File> {
        val prefix = wallpaperName + SEP
        return dir(ctx).listFiles()?.filter { it.name.startsWith(prefix) } ?: emptyList()
    }

    /**
     * 取（必要时生成）该壁纸在当前屏幕尺寸与裁剪下的成品图。
     * @return 可用的缓存文件；生成失败返回 null，调用方回退到内存位图路径
     */
    fun ensure(ctx: Context, file: File, crop: RectF?): File? {
        if (!file.exists()) return null
        val target = WallpaperApplier.targetSizePx(ctx)
        val out = targetFile(ctx, file, crop, target[0], target[1])
        if (out.exists() && out.length() > 0) return out

        val bmp = try {
            BitmapUtil.decodeCenterCrop(file, target[0], target[1], crop)
        } catch (t: Throwable) {
            Log.w(TAG, "decode for cache failed: ${file.name}", t)
            null
        } ?: return null

        return try {
            write(ctx, out, bmp)
        } finally {
            bmp.recycle()
        }
    }

    /**
     * 已经解好的位图直接落盘（换壁纸走了内存兜底路径时顺手补缓存，下次即可走快路径）。
     * 注意：不回收传入的位图，由调用方管理。
     */
    fun saveFromBitmap(ctx: Context, file: File, crop: RectF?, bmp: Bitmap): File? {
        val target = WallpaperApplier.targetSizePx(ctx)
        val out = targetFile(ctx, file, crop, target[0], target[1])
        if (out.exists() && out.length() > 0) return out
        return write(ctx, out, bmp)
    }

    /** 删除某张壁纸的全部缓存（壁纸被删除时调用） */
    fun remove(ctx: Context, wallpaperName: String) {
        filesOf(ctx, wallpaperName).forEach { it.delete() }
    }

    /** 清理孤儿缓存：源图已被删掉/列表里不存在的，以及上次写入中断留下的 .tmp */
    fun pruneOrphans(ctx: Context) {
        val names = Prefs.getWallpaperNames(ctx).toHashSet()
        dir(ctx).listFiles()?.forEach { f ->
            val owner = f.name.substringBefore(SEP)
            val valid = owner in names && WallpaperStore.file(ctx, owner).exists()
            if (!valid || f.name.endsWith(".tmp")) f.delete()
        }
    }

    private fun targetFile(ctx: Context, file: File, crop: RectF?, w: Int, h: Int): File {
        val sig = signature(file, crop, w, h)
        return File(dir(ctx), "${file.name}$SEP${w}x${h}$SEP$sig.jpg")
    }

    private fun signature(file: File, crop: RectF?, w: Int, h: Int): String {
        val raw = buildString {
            append(RENDER_VERSION).append('|')
            append(w).append('x').append(h).append('|')
            append(file.length()).append('|').append(file.lastModified()).append('|')
            append(crop?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "center")
        }
        return try {
            MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(10)
        } catch (t: Throwable) {
            // 理论上不会发生；退化成一个可用的短摘要即可
            raw.hashCode().toUInt().toString(16)
        }
    }

    private fun write(ctx: Context, out: File, bmp: Bitmap): File? {
        // 先写临时文件再改名：进程中途被杀不会留下半截文件被当成有效缓存
        val tmp = File(out.parentFile, out.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { fos ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)) {
                    tmp.delete()
                    return null
                }
            }
            if (!tmp.renameTo(out)) {
                tmp.delete()
                return null
            }
            // 同一张图的其它尺寸/裁剪/版本缓存已无用，删掉省空间
            filesOf(ctx, out.name.substringBefore(SEP))
                .filter { it != out }
                .forEach { it.delete() }
            Log.i(TAG, "cached ${out.name} ${out.length() / 1024}KB")
            out
        } catch (t: Throwable) {
            tmp.delete()
            Log.w(TAG, "write cache failed: ${out.name}", t)
            null
        }
    }
}
