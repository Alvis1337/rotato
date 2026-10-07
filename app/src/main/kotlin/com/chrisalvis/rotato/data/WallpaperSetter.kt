package com.chrisalvis.rotato.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.chrisalvis.rotato.live.LiveWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * What Coil should load for a stored image URL: collection and Library images saved on the
 * device are kept as paths relative to filesDir ("list_images/…", "rotato_images/…") or absolute.
 */
fun imageSourceFor(context: Context, url: String): Any = when {
    url.startsWith("list_images/") || url.startsWith("rotato_images/") -> File(context.filesDir, url)
    url.startsWith("/") -> File(url)
    url.startsWith("file://") -> File(url.removePrefix("file://"))
    else -> url
}

/**
 * Sets the image at [url] (remote or on the device) as the wallpaper with the user's target, fit
 * and effects, and records it in history. Used by Discover's and Collections' Set buttons.
 * Returns null on success or a short message to show.
 */
suspend fun applyWallpaperFromUrl(
    context: Context,
    url: String,
    isNsfw: Boolean,
    history: WallpaperHistoryItem,
): String? = withContext(Dispatchers.IO) {
    val prefs = RotatoPreferences(context)
    val target = wallpaperTargetSize(context)
    val request = ImageRequest.Builder(context)
        .data(imageSourceFor(context, url))
        .allowHardware(false)
        // Decode near wallpaper resolution instead of the original, which can be huge.
        .size(target.width, target.height)
        .scale(Scale.FILL)
        .build()
    val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        ?: return@withContext "Couldn't load the image"
    try {
        val settings = prefs.settings.first()
        val effectiveTarget = if (isNsfw && prefs.nsfwHomeOnly.first()) WallpaperTarget.HOME_ONLY else settings.wallpaperTarget
        val flags = when (effectiveTarget) {
            WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        // The source bitmap belongs to Coil's memory cache, so only the copy is recycled.
        val screenBitmap = fitWallpaperBitmap(bitmap, settings.wallpaperFit, target)
        try {
            setWallpaperBitmap(context, WallpaperManager.getInstance(context), screenBitmap, flags, settings.wallpaperFit, settings.wallpaperEffects)
        } finally {
            screenBitmap.recycle()
        }
        prefs.recordWallpaperShown(history)
        null
    } catch (e: Exception) {
        "Couldn't set the wallpaper"
    }
}

/**
 * Plays a video as the wallpaper through Rotato's live wallpaper (which has to be on).
 * Returns null on success or a short message to show.
 */
suspend fun applyVideoWallpaperFromUrl(
    context: Context,
    url: String,
    referer: String,
    history: WallpaperHistoryItem,
): String? = withContext(Dispatchers.IO) {
    if (!LiveWallpaper.isActive(context)) return@withContext "Turn on the Rotato live wallpaper (Settings › Rotation) to use videos"
    val dir = File(context.filesDir, "live").apply { mkdirs() }
    val local = imageSourceFor(context, url) as? File
    val ext = url.substringBefore('?').substringAfterLast('.', "mp4").lowercase().takeIf { it.length in 2..4 } ?: "mp4"
    val file = File(dir, "video_${System.currentTimeMillis()}.$ext")
    val ok = try {
        if (local != null) {
            local.copyTo(file, overwrite = true)
            true
        } else {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .build()
            FeedRepository.httpClient.newCall(req).execute().use { resp ->
                val body = resp.body
                if (!resp.isSuccessful || body == null) false
                else { file.outputStream().use { body.byteStream().copyTo(it) }; file.length() > 0 }
            }
        }
    } catch (e: Exception) {
        false
    }
    if (!ok) {
        file.delete()
        return@withContext "Couldn't download the video"
    }
    // Keep just this video; older ones are no longer on screen.
    dir.listFiles { f -> f.name.startsWith("video_") && f != file }?.forEach { it.delete() }
    LiveWallpaper.showVideo(context, file)
    RotatoPreferences(context).recordWallpaperShown(history)
    null
}
