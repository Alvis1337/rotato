@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.chrisalvis.rotato.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.chrisalvis.rotato.ui.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** One thing taking up space, and whether Rotato can clear it without losing anything you chose. */
data class StorageItem(
    val kind: StorageKind,
    val bytes: Long,
    val files: Int,
)

enum class StorageKind(val label: String, val detail: String, val clearable: Boolean) {
    LIBRARY("Library", "Wallpapers in your rotation pool", false),
    COLLECTION_FILES("Collection photos", "Photos you added to collections from this phone", false),
    UNUSED_COLLECTION_FILES("Unused collection photos", "Photos no collection uses any more", true),
    OFFLINE_PACK("Offline pack", "Wallpapers saved ahead for schedules", true),
    VIDEO_CACHE("Video cache", "Videos kept so loops don't download again", true),
    IMAGE_CACHE("Image cache", "Thumbnails and previews from Discover and Collections", true),
    LIVE_FRAMES("Live wallpaper frames", "Old frames the live wallpaper no longer shows", true),
    LEFTOVERS("Unfinished downloads", "Partial files from downloads that never completed", true),
}

/**
 * Storage cleaner: what Rotato keeps on the phone, by kind, and one-tap clearing for the kinds
 * that are safe to drop (caches, leftovers, photos nothing uses). Your Library and the photos
 * your collections use are only ever counted.
 */
object StorageTools {

    private fun dirSize(dir: File, filter: (File) -> Boolean = { true }): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().filter { it.isFile && filter(it) }.forEach { bytes += it.length(); count++ }
        return bytes to count
    }

    private fun leftovers(context: Context): List<File> =
        listOf(context.filesDir, context.cacheDir).flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.name.endsWith(".part") || it.name.endsWith(".tmp")) }.toList()
        }

    /** Live wallpaper frames other than the two newest (the one showing and the one fading out). */
    private fun oldLiveFrames(context: Context): List<File> =
        File(context.filesDir, "live").listFiles { f -> f.name.startsWith("frame_") }
            ?.sortedByDescending { it.lastModified() }?.drop(2).orEmpty()

    /** Files in list_images (stored as list_images/<listId>/<file>) that no collection entry points at. */
    private suspend fun unusedCollectionFiles(context: Context): List<File> {
        val dir = File(context.filesDir, "list_images")
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return emptyList()
        val used = LocalListsPreferences(context).allWallpapers.first()
            .flatMap { listOf(it.fullUrl, it.thumbUrl, it.sampleUrl) }
            .filter { it.startsWith("list_images/") }
            .mapTo(HashSet()) { it.removePrefix("list_images/") }
        val covers = LocalListsPreferences(context).lists.first()
            .map { it.coverUrl }.filter { it.startsWith("list_images/") }
            .mapTo(HashSet()) { it.removePrefix("list_images/") }
        return files.filter {
            val rel = it.relativeTo(dir).invariantSeparatorsPath
            rel !in used && rel !in covers
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun scan(context: Context): List<StorageItem> = withContext(Dispatchers.IO) {
        val unused = unusedCollectionFiles(context)
        val unusedPaths = unused.mapTo(HashSet()) { it.absolutePath }
        val (libBytes, libCount) = dirSize(File(context.filesDir, "rotato_images")) { !it.name.endsWith(".part") }
        val (colBytes, colCount) = dirSize(File(context.filesDir, "list_images")) { it.absolutePath !in unusedPaths }
        val (offBytes, offCount) = dirSize(OfflinePack.dir(context))
        val (vidBytes, vidCount) = dirSize(File(context.cacheDir, "video"))
        val imageCache = context.imageLoader.diskCache
        val frames = oldLiveFrames(context)
        val left = leftovers(context)
        listOf(
            StorageItem(StorageKind.LIBRARY, libBytes, libCount),
            StorageItem(StorageKind.COLLECTION_FILES, colBytes, colCount),
            StorageItem(StorageKind.UNUSED_COLLECTION_FILES, unused.sumOf { it.length() }, unused.size),
            StorageItem(StorageKind.OFFLINE_PACK, offBytes, offCount),
            StorageItem(StorageKind.VIDEO_CACHE, vidBytes, vidCount),
            StorageItem(StorageKind.IMAGE_CACHE, imageCache?.size ?: 0L, 0),
            StorageItem(StorageKind.LIVE_FRAMES, frames.sumOf { it.length() }, frames.size),
            StorageItem(StorageKind.LEFTOVERS, left.sumOf { it.length() }, left.size),
        )
    }

    /** Clears one kind. Returns the bytes freed. Kinds that hold your own choices are refused. */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clear(context: Context, kind: StorageKind): Long = withContext(Dispatchers.IO) {
        if (!kind.clearable) return@withContext 0L
        when (kind) {
            StorageKind.UNUSED_COLLECTION_FILES -> deleteAll(unusedCollectionFiles(context))
            StorageKind.OFFLINE_PACK -> deleteAll(OfflinePack.dir(context).listFiles()?.toList().orEmpty())
            StorageKind.VIDEO_CACHE -> {
                // Through the cache's own API: deleting its files under it would corrupt its index.
                val cache = VideoCache.get(context)
                val before = cache.cacheSpace
                runCatching { cache.keys.toList().forEach { cache.removeResource(it) } }
                (before - cache.cacheSpace).coerceAtLeast(0L)
            }
            StorageKind.IMAGE_CACHE -> {
                val loader = context.imageLoader
                val before = loader.diskCache?.size ?: 0L
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                before
            }
            StorageKind.LIVE_FRAMES -> deleteAll(oldLiveFrames(context))
            // Older than an hour, so a download still running isn't pulled out from under it.
            StorageKind.LEFTOVERS -> deleteAll(leftovers(context).filter { System.currentTimeMillis() - it.lastModified() > 3_600_000L })
            else -> 0L
        }
    }

    private fun deleteAll(files: List<File>): Long = files.sumOf { f -> val n = f.length(); if (f.delete()) n else 0L }

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }
}
