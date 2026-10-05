package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.media.ExifInterface
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import kotlin.math.ceil
import kotlin.math.roundToInt

fun sanitizeFilename(s: String): String = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

private const val DISPLAY_PREFS = "rotato_display_sizes"
private const val KEY_SIZES = "sizes"
private const val MAX_KNOWN_SIZES = 4

/**
 * Real size of the built-in display in its current state, normalised to portrait
 * (short side first) so a rotation in landscape doesn't produce a sideways crop.
 * On foldables this is whichever panel (outer or inner) is currently active.
 */
fun currentDisplaySize(context: Context): Size {
    val dm = context.getSystemService(DisplayManager::class.java)
    val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
    val metrics = DisplayMetrics()
    if (display != null) {
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
    }
    var w = metrics.widthPixels
    var h = metrics.heightPixels
    if (w <= 0 || h <= 0) {
        val fallback = context.resources.displayMetrics
        w = fallback.widthPixels
        h = fallback.heightPixels
    }
    return Size(minOf(w, h), maxOf(w, h))
}

/**
 * Remembers the current display size so that foldables (outer + inner panel) build up the
 * set of screens a wallpaper has to cover. Cheap; safe to call on every config change.
 */
fun recordDisplaySize(context: Context): Size {
    val size = currentDisplaySize(context)
    val sp = context.applicationContext.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)
    val known = parseSizes(sp.getString(KEY_SIZES, null))
    if (known.firstOrNull() != size) {
        val updated = (listOf(size) + known.filter { it != size }).take(MAX_KNOWN_SIZES)
        sp.edit().putString(KEY_SIZES, updated.joinToString(";") { "${it.width}x${it.height}" }).apply()
    }
    return size
}

private fun parseSizes(raw: String?): List<Size> = raw.orEmpty().split(';').mapNotNull { token ->
    val parts = token.split('x')
    val w = parts.getOrNull(0)?.toIntOrNull()
    val h = parts.getOrNull(1)?.toIntOrNull()
    if (w != null && h != null && w > 0 && h > 0) Size(w, h) else null
}

/**
 * Pixel size the wallpaper bitmap should be rendered at.
 *
 * A single-screen phone gets its own portrait resolution. A foldable gets a canvas wide
 * enough for the near-square inner panel and tall enough for the outer panel, so the
 * system's per-display centre crop shows a full-bleed image on both screens instead of
 * stretching a narrow outer-screen crop across the unfolded display.
 */
fun wallpaperTargetSize(context: Context): Size {
    val current = recordDisplaySize(context)
    val sp = context.applicationContext.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)
    val known = (parseSizes(sp.getString(KEY_SIZES, null)) + current).distinct()
    val height = known.maxOf { it.height }
    val aspect = known.maxOf { it.width.toFloat() / it.height }
    val width = ceil(height * aspect).toInt().coerceAtLeast(current.width)
    return Size(width, height)
}

/**
 * Scales [src] onto a [targetW]x[targetH] canvas according to [fit]. Always returns a new
 * bitmap (never [src] itself), so the caller can safely recycle [src] afterwards.
 */
fun fitWallpaperBitmap(src: Bitmap, fit: WallpaperFit, targetW: Int, targetH: Int): Bitmap {
    val out = when (fit) {
        WallpaperFit.STRETCH -> Bitmap.createScaledBitmap(src, targetW, targetH, true)
        WallpaperFit.FIT -> {
            val scale = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
            val scaledW = (src.width * scale).roundToInt().coerceAtLeast(1)
            val scaledH = (src.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(scaled, (targetW - scaledW) / 2f, (targetH - scaledH) / 2f, null)
            if (scaled !== src) scaled.recycle()
            result
        }
        WallpaperFit.FILL -> {
            val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
            val scaledW = (src.width * scale).roundToInt().coerceAtLeast(targetW)
            val scaledH = (src.height * scale).roundToInt().coerceAtLeast(targetH)
            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val srcX = (scaledW - targetW) / 2
            val srcY = (scaledH - targetH) / 2
            val cropped = Bitmap.createBitmap(scaled, srcX, srcY, targetW, targetH)
            if (scaled !== src && scaled !== cropped) scaled.recycle()
            cropped
        }
    }
    // createScaledBitmap/createBitmap hand back the source when no change is needed
    // (e.g. a wallpaper already at screen resolution); copy so recycling src stays safe.
    return if (out === src) src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) else out
}

fun loadScaledBitmap(context: Context, path: String): Bitmap? {
    val target = wallpaperTargetSize(context)
    val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOnly)
    val rotation = exifRotationDegrees(path)
    // Compare against the image's displayed orientation, not its stored one.
    val swap = rotation == 90 || rotation == 270
    var sampleSize = 1
    var w = if (swap) boundsOnly.outHeight else boundsOnly.outWidth
    var h = if (swap) boundsOnly.outWidth else boundsOnly.outHeight
    while (w / 2 >= target.width && h / 2 >= target.height) {
        w /= 2; h /= 2; sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: return null
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

/** Camera photos are often stored sideways with an EXIF hint; BitmapFactory ignores it. */
private fun exifRotationDegrees(path: String): Int = try {
    when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (e: Exception) {
    0
}
