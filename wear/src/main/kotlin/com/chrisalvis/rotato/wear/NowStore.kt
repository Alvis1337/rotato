package com.chrisalvis.rotato.wear

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** What the phone is showing, as last received. */
data class NowShowing(
    val updatedAt: Long = 0L,
    val caption: String = "",
    val blurred: Boolean = false,
    val image: File? = null,
    val tileImage: File? = null,
) {
    val hasImage: Boolean get() = image != null
}

/**
 * Keeps the phone's current wallpaper on the watch's disk, so the tile, complication and app can
 * show it instantly (and while the phone is out of range).
 */
object NowStore {

    private val _state = MutableStateFlow<NowShowing?>(null)
    val state: StateFlow<NowShowing?> = _state.asStateFlow()

    /** Reads the cached copy (no Data Layer call). */
    fun load(context: Context): NowShowing {
        _state.value?.let { return it }
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val image = File(context.filesDir, IMAGE_FILE).takeIf { it.isFile && sp.getBoolean(KEY_HAS_IMAGE, false) }
        val tile = File(context.filesDir, TILE_FILE).takeIf { it.isFile && image != null }
        return NowShowing(
            updatedAt = sp.getLong(KEY_UPDATED, 0L),
            caption = sp.getString(KEY_CAPTION, "").orEmpty(),
            blurred = sp.getBoolean(KEY_BLURRED, false),
            image = image,
            tileImage = tile,
        ).also { _state.value = it }
    }

    /** Pulls the phone's latest data item, for when the watch missed an update. */
    suspend fun refresh(context: Context) {
        val client = Wearable.getDataClient(context)
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WatchPaths.NOW).build()
        val buffer = runCatching { client.getDataItems(uri, DataClient.FILTER_PREFIX).await() }.getOrNull() ?: return
        try {
            val newest = buffer.map { it.freeze() }.maxByOrNull {
                DataMapItem.fromDataItem(it).dataMap.getLong(WatchPaths.KEY_UPDATED_AT)
            } ?: return
            save(context, newest)
        } finally {
            buffer.release()
        }
    }

    suspend fun save(context: Context, item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val updatedAt = map.getLong(WatchPaths.KEY_UPDATED_AT)
        if (updatedAt != 0L && updatedAt == load(context).updatedAt) return
        val client = Wearable.getDataClient(context)
        val image = map.getAsset(WatchPaths.KEY_IMAGE)
        val tile = map.getAsset(WatchPaths.KEY_TILE_IMAGE)
        val hasImage = image != null &&
            writeAsset(client, image, File(context.filesDir, IMAGE_FILE)) &&
            (tile == null || writeAsset(client, tile, File(context.filesDir, TILE_FILE)))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UPDATED, updatedAt)
            .putString(KEY_CAPTION, map.getString(WatchPaths.KEY_CAPTION).orEmpty())
            .putBoolean(KEY_BLURRED, map.getBoolean(WatchPaths.KEY_BLURRED))
            .putBoolean(KEY_HAS_IMAGE, hasImage)
            .apply()
        _state.value = null
        load(context)
        notifySurfaces(context)
    }

    private suspend fun writeAsset(client: DataClient, asset: Asset, dest: File): Boolean = runCatching {
        val response = client.getFdForAsset(asset).await()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        withContext(Dispatchers.IO) {
            response.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        }
        tmp.renameTo(dest)
    }.getOrDefault(false)

    /** Asks the tile and any watch face using the photo complication to redraw. */
    fun notifySurfaces(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(NowTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, PhotoComplicationService::class.java))
                .requestUpdateAll()
        }
    }

    private const val PREFS = "now_showing"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_CAPTION = "caption"
    private const val KEY_BLURRED = "blurred"
    private const val KEY_HAS_IMAGE = "has_image"
    private const val IMAGE_FILE = "now.jpg"
    private const val TILE_FILE = "now_tile.jpg"
}
