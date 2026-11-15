package com.heysem.alwayson

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the blurry backgrounds. Everything is done on small bitmaps
 * (a few hundred pixels wide) and stretched to the screen when drawn:
 * after a blur nobody can tell, and it keeps memory and battery use tiny.
 */
object Backgrounds {

    const val MODE_MOUNTAIN = "mountain"
    const val MODE_PHOTO = "photo"
    const val MODE_BLACK = "black"

    private const val PREFS = "aod"
    private const val KEY_MODE = "bg_mode"
    private const val PHOTO_FILE = "bg_photo.png"

    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, MODE_MOUNTAIN)!!

    fun setMode(ctx: Context, mode: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply()

    /** Returns the bitmap for the current mode, or null for pure black. */
    fun load(ctx: Context): Bitmap? = when (mode(ctx)) {
        MODE_PHOTO -> File(ctx.filesDir, PHOTO_FILE).takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) } ?: mountain(ctx)
        MODE_BLACK -> null
        else -> mountain(ctx)
    }

    /** Built-in blurred mountain lake at dusk (half resolution is plenty, it's blurred). */
    fun mountain(ctx: Context): Bitmap = BitmapFactory.decodeResource(
        ctx.resources, R.drawable.bg_mountain,
        BitmapFactory.Options().apply { inSampleSize = 2 }
    )

    /**
     * Reads a picked photo, shrinks it, blurs it and stores the result in the app's
     * private folder, so the original photo is never needed again.
     */
    fun importPhoto(ctx: Context, uri: Uri): Boolean = runCatching {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return false

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 360) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false

        val rotation = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        // Shrink so the short side is ~240 px, applying the photo's rotation at the same time.
        val scale = 240f / min(decoded.width, decoded.height)
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val small = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (small !== decoded) decoded.recycle()

        val blurred = blur(small, radius = 10, passes = 3)
        File(ctx.filesDir, PHOTO_FILE).outputStream().use {
            blurred.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        setMode(ctx, MODE_PHOTO)
        true
    }.getOrDefault(false)

    /** Repeated box blur, which looks like a Gaussian blur after 3 passes. */
    fun blur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var a = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        var b = IntArray(w * h)
        repeat(passes) {
            boxPass(a, b, w, h, radius, horizontal = true)
            boxPass(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + k else k * w + line
            }
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val px = src[idx(i)]
                sr += (px shr 16) and 0xFF; sg += (px shr 8) and 0xFF; sb += px and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[idx(i + r + 1)]
                val rem = src[idx(i - r)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }

    /** Center-crop source rect so the bitmap fills a view of the given aspect. */
    fun centerCrop(bmp: Bitmap, viewW: Float, viewH: Float): android.graphics.Rect {
        val viewAspect = viewW / viewH
        val bmpAspect = bmp.width.toFloat() / bmp.height
        return if (bmpAspect > viewAspect) {
            val cw = (bmp.height * viewAspect).toInt()
            val x = (bmp.width - cw) / 2
            android.graphics.Rect(x, 0, x + cw, bmp.height)
        } else {
            val ch = (bmp.width / max(viewAspect, 0.01f)).toInt()
            val y = (bmp.height - ch) / 2
            android.graphics.Rect(0, y, bmp.width, y + ch)
        }
    }
}
