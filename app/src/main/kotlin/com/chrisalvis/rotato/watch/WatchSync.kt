package com.chrisalvis.rotato.watch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.live.LiveWallpaper
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sends the wallpaper on screen to the Rotato watch app over the Wearable Data Layer.
 *
 * The phone writes one data item at [WatchPaths.NOW]; the watch's tile, photo complication and
 * app all read it. Nothing is sent unless a watch with Rotato installed is paired, so phones
 * without a watch (or without Play services) pay nothing.
 */
object WatchSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pending: Job? = null

    /** Rotation, Previous and the widget all refresh at once; coalesce them into one send. */
    fun push(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(600)
            runCatching { pushNow(app) }
        }
    }

    suspend fun pushNow(context: Context) {
        if (!hasWatch(context)) return
        val prefs = RotatoPreferences(context)
        val file = currentWallpaperFile(context, prefs)
        val bitmap = file?.let(::decodeForWatch)
        val isNsfw = file != null && file.name in prefs.nsfwFileNames.first()
        // Same rule as the "wallpaper changed" notification: a watch face is on show to anyone.
        val blur = isNsfw && prefs.nsfwBlurEnabled.first()

        val request = PutDataMapRequest.create(WatchPaths.NOW).apply {
            dataMap.putLong(WatchPaths.KEY_UPDATED_AT, System.currentTimeMillis())
            dataMap.putString(WatchPaths.KEY_CAPTION, caption(prefs))
            dataMap.putBoolean(WatchPaths.KEY_BLURRED, blur)
            if (bitmap != null) {
                val square = centerSquare(bitmap, WATCH_SIZE).let { if (blur) blurred(it) else it }
                dataMap.putAsset(WatchPaths.KEY_IMAGE, Asset.createFromBytes(square.toJpeg(88)))
                val small = Bitmap.createScaledBitmap(square, TILE_SIZE, TILE_SIZE, true)
                dataMap.putAsset(WatchPaths.KEY_TILE_IMAGE, Asset.createFromBytes(small.toJpeg(82)))
            }
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    /** Sends a short line the watch shows as a confirmation (e.g. after Save). */
    suspend fun reply(context: Context, nodeId: String, text: String) {
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, WatchPaths.TOAST, text.toByteArray(Charsets.UTF_8))
                .await()
        }
    }

    private suspend fun hasWatch(context: Context): Boolean = runCatching {
        Wearable.getCapabilityClient(context)
            .getCapability(WatchPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes.isNotEmpty()
    }.getOrDefault(false)

    private suspend fun currentWallpaperFile(context: Context, prefs: RotatoPreferences): File? {
        if (LiveWallpaper.isActive(context)) {
            val path = context.getSharedPreferences(LiveWallpaper.PREFS, Context.MODE_PRIVATE)
                .getString(LiveWallpaper.KEY_PATH, null)
            path?.let(::File)?.takeIf { it.isFile }?.let { return it }
        }
        return prefs.appliedWallpaperPaths.first().lastOrNull()?.let(::File)?.takeIf { it.isFile }
    }

    private suspend fun caption(prefs: RotatoPreferences): String = runCatching {
        historyFromJson(prefs.historyJson.first()).firstOrNull()
            ?.source?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
    }.getOrNull().orEmpty()

    private fun decodeForWatch(file: File): Bitmap? = runCatching {
        if (LiveWallpaper.isVideoFile(file)) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1024, 1024)
                    ?: retriever.frameAtTime
            } finally {
                retriever.release()
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WATCH_SIZE) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    /**
     * Watches are round or square, so the middle of the wallpaper is cut out as a square, a little
     * above centre on tall images where faces and subjects usually sit.
     */
    private fun centerSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = if (src.height > src.width) ((src.height - side) * 0.4f).toInt() else (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    private fun blurred(src: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(src, (src.width / 24).coerceAtLeast(1), (src.height / 24).coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(tiny, src.width, src.height, true)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private const val WATCH_SIZE = 480
    private const val TILE_SIZE = 256
}

/** Data Layer paths and keys; the watch app (wear module) uses the same values. */
object WatchPaths {
    const val NOW = "/rotato/now"
    const val NEXT = "/rotato/next"
    const val PREVIOUS = "/rotato/previous"
    const val SAVE = "/rotato/save"
    const val SYNC = "/rotato/sync"
    const val TOAST = "/rotato/toast"

    const val KEY_IMAGE = "image"
    const val KEY_TILE_IMAGE = "tile_image"
    const val KEY_CAPTION = "caption"
    const val KEY_BLURRED = "blurred"
    const val KEY_UPDATED_AT = "updated_at"

    const val CAPABILITY_WATCH = "rotato_watch"
}
