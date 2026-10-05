package com.chrisalvis.rotato.data

import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.ExifInterface
import android.media.FaceDetector
import android.os.Build
import android.util.Log
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import java.lang.reflect.InvocationTargetException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
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

/** Screens a rotated wallpaper has to cover, each in portrait (short side first). */
fun knownDisplaySizes(context: Context): List<Size> {
    val current = recordDisplaySize(context)
    val sp = context.applicationContext.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)
    val known = (parseSizes(sp.getString(KEY_SIZES, null)) + current).distinct()
    // A foldable that has only ever been seen folded doesn't know its inner panel yet. Assume a
    // near-square one so the first wallpapers aren't sized for the narrow outer screen only;
    // the real size replaces this as soon as the app runs unfolded.
    val looksFoldedOnly = known.all { it.width.toFloat() / it.height < 0.7f }
    if (looksFoldedOnly && isFoldable(context)) {
        val h = known.maxOf { it.height }
        return known + Size((h * ASSUMED_INNER_ASPECT).roundToInt(), h)
    }
    return known
}

private const val ASSUMED_INNER_ASPECT = 0.95f

private fun isFoldable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

/**
 * Canvas a wallpaper is rendered onto: [width] x [height] covers every known screen, and
 * [narrowestWidth] is the width (at [height]) of the narrowest one, which Fit mode letterboxes
 * into so the whole picture stays visible on every screen.
 */
data class WallpaperCanvas(val width: Int, val height: Int, val narrowestWidth: Int, val screens: List<Size>)

/**
 * Pixel size the wallpaper bitmap should be rendered at.
 *
 * A single-screen phone gets its own portrait resolution. A foldable gets a canvas wide
 * enough for the near-square inner panel and tall enough for the outer panel, so each
 * screen's crop is a full-bleed image instead of a narrow outer-screen crop stretched across
 * the unfolded display.
 */
fun wallpaperTargetSize(context: Context): WallpaperCanvas {
    val screens = knownDisplaySizes(context)
    val height = screens.maxOf { it.height }
    val maxAspect = screens.maxOf { it.width.toFloat() / it.height }
    val minAspect = screens.minOf { it.width.toFloat() / it.height }
    val width = ceil(height * maxAspect).toInt()
    val narrowest = ceil(height * minAspect).toInt().coerceAtMost(width)
    return WallpaperCanvas(width, height, narrowest, screens)
}

fun fitWallpaperBitmap(src: Bitmap, fit: WallpaperFit, canvas: WallpaperCanvas): Bitmap =
    fitWallpaperBitmap(src, fit, canvas.width, canvas.height, canvas.narrowestWidth)

/**
 * Scales [src] onto a [targetW]x[targetH] canvas according to [fit]. Fit mode sizes the image
 * to [fitW] (the narrowest screen) so it isn't cut off there. Always returns a new bitmap
 * (never [src] itself), so the caller can safely recycle [src] afterwards.
 */
fun fitWallpaperBitmap(src: Bitmap, fit: WallpaperFit, targetW: Int, targetH: Int, fitW: Int = targetW): Bitmap {
    val out = when (fit) {
        WallpaperFit.STRETCH -> Bitmap.createScaledBitmap(src, targetW, targetH, true)
        WallpaperFit.FIT -> {
            val scale = minOf(fitW.toFloat() / src.width, targetH.toFloat() / src.height)
            val scaledW = (src.width * scale).roundToInt().coerceIn(1, targetW)
            val scaledH = (src.height * scale).roundToInt().coerceIn(1, targetH)
            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(scaled, (targetW - scaledW) / 2f, (targetH - scaledH) / 2f, null)
            if (scaled !== src) scaled.recycle()
            result
        }
        WallpaperFit.FILL, WallpaperFit.SMART -> {
            val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
            val scaledW = (src.width * scale).roundToInt().coerceAtLeast(targetW)
            val scaledH = (src.height * scale).roundToInt().coerceAtLeast(targetH)
            val focus = if (fit == WallpaperFit.SMART) findFocusPoint(src) else PointF(0.5f, 0.5f)
            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val srcX = (focus.x * scaledW - targetW / 2f).roundToInt().coerceIn(0, scaledW - targetW)
            val srcY = (focus.y * scaledH - targetH / 2f).roundToInt().coerceIn(0, scaledH - targetH)
            val cropped = Bitmap.createBitmap(scaled, srcX, srcY, targetW, targetH)
            if (scaled !== src && scaled !== cropped) scaled.recycle()
            cropped
        }
    }
    // createScaledBitmap/createBitmap hand back the source when no change is needed
    // (e.g. a wallpaper already at screen resolution); copy so recycling src stays safe.
    return if (out === src) src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) else out
}

/**
 * Sets [bitmap] (rendered with [wallpaperTargetSize]) as the wallpaper for [which].
 *
 * On Android 15+ with multi-crop support, each known screen (folded and unfolded, portrait and
 * landscape) gets an explicit crop (centred, or around the subject for [WallpaperFit.SMART]), so the system shows exactly that framing instead of
 * applying its own scaling/parallax to the shared canvas. Older versions fall back to setBitmap.
 */
fun setWallpaperBitmap(
    context: Context,
    wm: WallpaperManager,
    bitmap: Bitmap,
    which: Int,
    fit: WallpaperFit = WallpaperFit.FILL,
) {
    if (Build.VERSION.SDK_INT >= 35) {
        val focus = if (fit == WallpaperFit.SMART) findFocusPoint(bitmap) else PointF(0.5f, 0.5f)
        val crops = screenCrops(bitmap.width, bitmap.height, knownDisplaySizes(context), focus)
        if (setBitmapWithCrops(wm, bitmap, crops, which)) return
    }
    wm.setBitmap(bitmap, null, true, which)
}

private fun screenCrops(bitmapW: Int, bitmapH: Int, screens: List<Size>, focus: PointF): Map<Point, Rect> {
    val crops = LinkedHashMap<Point, Rect>()
    screens.forEach { s ->
        crops[Point(s.width, s.height)] = focusedCrop(bitmapW, bitmapH, s.width.toFloat() / s.height, focus)
        crops[Point(s.height, s.width)] = focusedCrop(bitmapW, bitmapH, s.height.toFloat() / s.width, focus)
    }
    return crops
}

/** Largest [aspect] crop of the bitmap, as close to centred on [focus] as the edges allow. */
private fun focusedCrop(bitmapW: Int, bitmapH: Int, aspect: Float, focus: PointF): Rect {
    return if (bitmapW.toFloat() / bitmapH > aspect) {
        val w = (bitmapH * aspect).roundToInt().coerceIn(1, bitmapW)
        val left = (focus.x * bitmapW - w / 2f).roundToInt().coerceIn(0, bitmapW - w)
        Rect(left, 0, left + w, bitmapH)
    } else {
        val h = (bitmapW / aspect).roundToInt().coerceIn(1, bitmapH)
        val top = (focus.y * bitmapH - h / 2f).roundToInt().coerceIn(0, bitmapH - h)
        Rect(0, top, bitmapW, top + h)
    }
}

/**
 * Estimates where the subject of [bitmap] is, as fractions of its width and height.
 *
 * Photos with detectable faces centre on the faces. Everything else (most anime art, where
 * the platform face detector finds nothing) uses a saliency map from a small thumbnail: busy,
 * saturated detail scores high and flat backgrounds (sky, gradients, solid fills) score low,
 * with a mild pull towards the middle so a noisy border doesn't drag the crop off the subject.
 * Returns the centre when the image gives no clear signal.
 */
fun findFocusPoint(bitmap: Bitmap): PointF {
    if (bitmap.width < 8 || bitmap.height < 8) return PointF(0.5f, 0.5f)
    return try {
        detectFaceCentre(bitmap) ?: saliencyCentre(bitmap)
    } catch (e: RuntimeException) {
        PointF(0.5f, 0.5f)
    }
}

private fun detectFaceCentre(bitmap: Bitmap): PointF? {
    val scale = 320f / maxOf(bitmap.width, bitmap.height)
    // FaceDetector needs an RGB_565 bitmap with an even width.
    val w = ((bitmap.width * scale).roundToInt().coerceAtLeast(2)) and 1.inv()
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(2)
    val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val rgb565 = small.copy(Bitmap.Config.RGB_565, false)
    if (small !== bitmap && small !== rgb565) small.recycle()
    try {
        val faces = arrayOfNulls<FaceDetector.Face>(4)
        val found = FaceDetector(w, h, faces.size).findFaces(rgb565, faces)
        var sumX = 0f
        var sumY = 0f
        var weight = 0f
        val mid = PointF()
        for (i in 0 until found) {
            val face = faces[i] ?: continue
            if (face.confidence() < 0.4f) continue
            face.getMidPoint(mid)
            // Weight by eye distance so the largest (closest) face dominates.
            val wt = face.eyesDistance()
            sumX += mid.x * wt
            sumY += mid.y * wt
            weight += wt
        }
        if (weight <= 0f) return null
        return PointF((sumX / weight / w).coerceIn(0f, 1f), (sumY / weight / h).coerceIn(0f, 1f))
    } finally {
        rgb565.recycle()
    }
}

private fun saliencyCentre(bitmap: Bitmap): PointF {
    val scale = 64f / maxOf(bitmap.width, bitmap.height)
    val w = (bitmap.width * scale).roundToInt().coerceAtLeast(4)
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(4)
    val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    if (small !== bitmap) small.recycle()

    val luma = FloatArray(px.size)
    val sat = FloatArray(px.size)
    val hsv = FloatArray(3)
    for (i in px.indices) {
        val c = px[i]
        luma[i] = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        Color.colorToHSV(c, hsv)
        sat[i] = hsv[1] * hsv[2]
    }
    val meanLuma = luma.average().toFloat()

    var sumX = 0.0
    var sumY = 0.0
    var total = 0.0
    var peak = 0.0
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            val gradient = abs(luma[i + 1] - luma[i - 1]) + abs(luma[i + w] - luma[i - w])
            // Contrast against the image's overall tone picks out a subject on a plain backdrop.
            val contrast = abs(luma[i] - meanLuma) / 255f
            val dx = (x + 0.5f) / w - 0.5f
            val dy = (y + 0.5f) / h - 0.5f
            val centreBias = exp(-(dx * dx + dy * dy) / 0.18f)
            val energy = (gradient / 255f + 0.5f * contrast) * (0.4f + sat[i]) * centreBias
            val e = (energy * energy).toDouble()
            sumX += (x + 0.5) * e
            sumY += (y + 0.5) * e
            total += e
            if (e > peak) peak = e
        }
    }
    if (total <= 0.0 || peak <= 0.0) return PointF(0.5f, 0.5f)
    val fx = (sumX / total / w).toFloat()
    val fy = (sumY / total / h).toFloat()
    // The centre bias compresses the weighted mean towards the middle; stretch it back out.
    return PointF((0.5f + (fx - 0.5f) * 1.5f).coerceIn(0f, 1f), (0.5f + (fy - 0.5f) * 1.5f).coerceIn(0f, 1f))
}

/**
 * WallpaperManager.setBitmapWithCrops (API 35) via reflection: it is gated behind the platform's
 * multi-crop flag, so it is only used when the device reports that flag enabled. Returns false
 * when unavailable so the caller falls back to plain setBitmap.
 */
private fun setBitmapWithCrops(wm: WallpaperManager, bitmap: Bitmap, crops: Map<Point, Rect>, which: Int): Boolean {
    return try {
        val enabled = WallpaperManager::class.java.getMethod("isMultiCropEnabled").invoke(null) as? Boolean
        if (enabled != true) return false
        WallpaperManager::class.java
            .getMethod("setBitmapWithCrops", Bitmap::class.java, Map::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(wm, bitmap, crops, true, which)
        true
    } catch (e: InvocationTargetException) {
        Log.w("ImageUtils", "setBitmapWithCrops failed, falling back to setBitmap", e.targetException)
        false
    } catch (e: ReflectiveOperationException) {
        false
    } catch (e: RuntimeException) {
        false
    }
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
