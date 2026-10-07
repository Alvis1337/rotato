package com.chrisalvis.rotato.live

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Rotato's own live wallpaper. When it's the active wallpaper, every "set wallpaper" in the app
 * hands the image to [RotatoWallpaperService] instead of the system, which crossfades to it,
 * re-crops it when the phone folds or unfolds, and can play videos.
 */
object LiveWallpaper {
    internal const val PREFS = "live_wallpaper"
    internal const val KEY_PATH = "path"
    internal const val KEY_VIDEO = "video"
    internal const val KEY_CROPS = "crops"
    internal const val KEY_STAMP = "stamp"
    internal const val KEY_FADE_MS = "fade_ms"
    internal const val KEY_DRIFT = "drift"
    internal const val KEY_PARALLAX = "parallax"
    internal const val KEY_DOUBLE_TAP = "double_tap"
    internal const val KEY_SUN = "follow_sun"

    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v")

    fun isActive(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.let {
            it.packageName == context.packageName && it.serviceName == RotatoWallpaperService::class.java.name
        } == true
    } catch (e: RuntimeException) {
        false
    }

    /** Opens the system preview for Rotato's live wallpaper, where the user taps Set. */
    fun pickerIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, RotatoWallpaperService::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isVideoFile(file: File): Boolean = file.extension.lowercase() in VIDEO_EXTENSIONS

    /**
     * Shows [bitmap] (already fitted to the wallpaper canvas). [crops] maps screen sizes to the
     * part of the bitmap each should show (fold pairs); without them each screen is centre-cropped
     * around the subject.
     */
    fun showBitmap(context: Context, bitmap: Bitmap, crops: Map<Point, Rect>? = null) {
        val dir = File(context.filesDir, "live").apply { mkdirs() }
        val file = File(dir, "frame_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        // Keep this frame and the one before it (still on screen while the crossfade runs).
        dir.listFiles { f -> f.name.startsWith("frame_") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(2)
            ?.forEach { it.delete() }
        publish(context, file.absolutePath, video = false, crops = crops)
    }

    /** Plays [file] (a downloaded video) silently on a loop. */
    fun showVideo(context: Context, file: File) = publish(context, file.absolutePath, video = true, crops = null)

    private fun publish(context: Context, path: String, video: Boolean, crops: Map<Point, Rect>?) {
        val cropsJson = crops?.let { map ->
            JSONArray().also { arr ->
                map.forEach { (p, r) ->
                    arr.put(JSONObject().put("w", p.x).put("h", p.y).put("l", r.left).put("t", r.top).put("r", r.right).put("b", r.bottom))
                }
            }.toString()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PATH, path)
            .putBoolean(KEY_VIDEO, video)
            .putString(KEY_CROPS, cropsJson)
            .putLong(KEY_STAMP, System.currentTimeMillis())
            .apply()
    }

    internal fun parseCrops(raw: String?): Map<Point, Rect> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                Point(o.getInt("w"), o.getInt("h")) to Rect(o.getInt("l"), o.getInt("t"), o.getInt("r"), o.getInt("b"))
            }
        }.getOrDefault(emptyMap())
    }

    /** Crossfade length in ms (0 = instant). */
    fun fadeMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_FADE_MS, 900L)

    fun setFadeMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_FADE_MS, ms).apply()
    }

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Living stills: a very slow zoom and drift so the wallpaper never looks frozen. */
    fun drift(context: Context) = sp(context).getBoolean(KEY_DRIFT, false)
    fun setDrift(context: Context, on: Boolean) = sp(context).edit().putBoolean(KEY_DRIFT, on).apply()

    /** The image shifts slightly as the phone tilts, like looking through a window. */
    fun parallax(context: Context) = sp(context).getBoolean(KEY_PARALLAX, false)
    fun setParallax(context: Context, on: Boolean) = sp(context).edit().putBoolean(KEY_PARALLAX, on).apply()

    /** Double-tap an empty spot on the home screen for the next wallpaper. */
    fun doubleTap(context: Context) = sp(context).getBoolean(KEY_DOUBLE_TAP, true)
    fun setDoubleTap(context: Context, on: Boolean) = sp(context).edit().putBoolean(KEY_DOUBLE_TAP, on).apply()

    /** Warm golden-hour glow in the evening, a gentle dim and cool tint at night. */
    fun followSun(context: Context) = sp(context).getBoolean(KEY_SUN, false)
    fun setFollowSun(context: Context, on: Boolean) = sp(context).edit().putBoolean(KEY_SUN, on).apply()

    /**
     * The tint laid over the wallpaper at [hour] (0–24, fractional) when "Follow the sun" is on:
     * transparent through the day, amber around sunrise and sunset, deep blue at night.
     */
    internal fun sunTint(hour: Float): Int {
        fun bump(h: Float, start: Float, peak: Float, end: Float): Float = when {
            h <= start || h >= end -> 0f
            h <= peak -> (h - start) / (peak - start)
            else -> (end - h) / (end - peak)
        }
        val night = when {
            hour >= 22f || hour < 5f -> 1f
            hour in 20f..22f -> (hour - 20f) / 2f
            hour in 5f..6.5f -> 1f - (hour - 5f) / 1.5f
            else -> 0f
        }
        val warm = maxOf(bump(hour, 5.5f, 7f, 9f) * 0.6f, bump(hour, 16.5f, 19f, 21f))
        if (night >= warm) {
            val a = (night * 110).toInt()
            return android.graphics.Color.argb(a, 8, 14, 40)
        }
        val a = (warm * 70).toInt()
        return android.graphics.Color.argb(a, 255, 140, 40)
    }
}
