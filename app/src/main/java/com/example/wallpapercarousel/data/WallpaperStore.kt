package com.example.wallpapercarousel.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 壁纸文件仓库：把用户选中的图片复制到应用私有目录
 */
object WallpaperStore {

    private fun dir(ctx: Context) =
        File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** 从内容 URI 复制图片，成功返回文件名，失败返回 null */
    fun add(ctx: Context, uri: Uri): String? {
        val name = "wp_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
        val f = file(ctx, name)
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                f.outputStream().use { ins.copyTo(it) }
            }
            // 校验是可解码的图片
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                f.delete()
                null
            } else {
                name
            }
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    fun remove(ctx: Context, name: String) {
        file(ctx, name).delete()
    }
}
