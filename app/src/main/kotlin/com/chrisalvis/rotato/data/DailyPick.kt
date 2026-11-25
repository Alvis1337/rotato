package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.edit
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * Once a day, one fresh find from the user's Discover sources, ranked the same way "For you"
 * ranks the feed. It shows as a notification and on the Daily pick widget, with Set and Save.
 *
 * Always SFW: the notification and the widget can be seen by anyone who glances at the phone.
 */
object DailyPick {

    private const val PREFS = "rotato_daily_pick"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_PICK = "pick"
    private const val KEY_DATE = "date"
    private const val KEY_PAST = "past_keys"
    private const val KEY_ERROR = "error"
    private const val KEY_SAVED = "saved"
    private const val KEY_APPLIED = "applied"
    private const val PREVIEW_FILE = "daily_pick_preview.jpg"
    private const val MAX_PAST = 200
    const val DEFAULT_HOUR = 9

    fun store(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the daily notification is on. The widget works with or without it. */
    fun isEnabled(context: Context) = store(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = store(context).edit { putBoolean(KEY_ENABLED, enabled) }

    /** Hour of the day (0–23) the pick arrives. */
    fun hour(context: Context) = store(context).getInt(KEY_HOUR, DEFAULT_HOUR)
    fun setHour(context: Context, hour: Int) = store(context).edit { putInt(KEY_HOUR, hour.coerceIn(0, 23)) }

    fun lastError(context: Context): String? = store(context).getString(KEY_ERROR, null)

    /** Today's pick (or the latest one, if today's hasn't arrived yet). */
    fun current(context: Context): BrainrotWallpaper? =
        store(context).getString(KEY_PICK, null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

    /** Whether today's pick has been saved / set already, so the actions can say so. */
    fun isSaved(context: Context) = store(context).getBoolean(KEY_SAVED, false)
    fun isApplied(context: Context) = store(context).getBoolean(KEY_APPLIED, false)
    fun markSaved(context: Context) = store(context).edit { putBoolean(KEY_SAVED, true) }
    fun markApplied(context: Context) = store(context).edit { putBoolean(KEY_APPLIED, true) }

    fun pickDate(context: Context): String? = store(context).getString(KEY_DATE, null)

    /** Small local copy of the pick for the widget, so it never needs the network to draw. */
    fun previewFile(context: Context) = File(context.filesDir, PREVIEW_FILE)

    fun loadPreview(context: Context, maxDimension: Int = 1024): Bitmap? {
        val file = previewFile(context)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var w = bounds.outWidth.coerceAtLeast(1)
        var h = bounds.outHeight.coerceAtLeast(1)
        while (w > maxDimension || h > maxDimension) { sample *= 2; w /= 2; h /= 2 }
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Finds a new pick, stores it with a local preview and returns it with the preview bitmap.
     * Null when no source returned anything usable (the reason is kept for Settings).
     */
    suspend fun refresh(context: Context): Pair<BrainrotWallpaper, Bitmap?>? = withContext(Dispatchers.IO) {
        val pick = try {
            findPick(context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppErrorLog.log("DailyPick", "Couldn't find a pick", e)
            null
        }
        if (pick == null) {
            store(context).edit { putString(KEY_ERROR, "No new images from your sources. Check Discover › Sources.") }
            return@withContext null
        }
        val bitmap = downloadPreview(context, pick)
        val past = pastKeys(context) + keyOf(pick)
        store(context).edit {
            putString(KEY_PICK, toJson(pick).toString())
            putString(KEY_DATE, LocalDate.now().toString())
            putString(KEY_PAST, JSONArray(past.toList().takeLast(MAX_PAST)).toString())
            remove(KEY_ERROR)
            remove(KEY_SAVED)
            remove(KEY_APPLIED)
        }
        pick to bitmap
    }

    private fun keyOf(wp: BrainrotWallpaper) = "${wp.source}:${wp.id}"

    private fun pastKeys(context: Context): LinkedHashSet<String> {
        val raw = store(context).getString(KEY_PAST, null) ?: return LinkedHashSet()
        return runCatching {
            val arr = JSONArray(raw)
            LinkedHashSet<String>().apply { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(LinkedHashSet())
    }

    /**
     * Asks every enabled source for a page (its own tags, plus a couple of the user's most-liked
     * learned tags so the pool leans their way), drops anything already seen, saved, blocked or
     * picked before, and returns the best "For you" score.
     */
    private suspend fun findPick(context: Context): BrainrotWallpaper? = coroutineScope {
        val prefs = RotatoPreferences(context)
        val pluginRepo = PluginRepository(context)
        val learned = LearnedTaste(context)
        val weights = learned.weights.first()
        val blocklist = ContentBlocklist.load(context, nsfw = false)
        val blockedKeys = prefs.blockedImageKeys.first()
        val seen = prefs.seenWallpaperKeys.first()
        val past = pastKeys(context)
        val saved = LocalListsPreferences(context).allWallpapers.first()
        val savedIds = saved.mapTo(HashSet()) { "${it.source}:${it.sourceId}" }
        val savedUrls = saved.mapNotNullTo(HashSet()) { it.fullUrl.takeIf(String::isNotBlank) }
        val filters = prefs.brainrotFilters.first().copy(videoOnly = false)
        val foldCanvas = if (isFoldable(context)) wallpaperTargetSize(context) else null

        // The tags the user has leaned into most; one or two of them join each source's searches.
        val likedTags = weights.entries.filter { it.value > 1f }
            .sortedByDescending { it.value }.take(12).map { it.key }
            .filterNot { it in blocklist.tags }

        val sources = LocalSourcesPreferences(context).sources.first().filter { it.enabled }
        val candidates = sources.map { source ->
            async {
                val manifest = pluginRepo.getManifest(source.pluginId) ?: return@async emptyList<BrainrotWallpaper>()
                if (!PluginEntitlement.isUnlocked(manifest)) return@async emptyList<BrainrotWallpaper>()
                if (manifest.requiresCredentials &&
                    (source.apiKey.isBlank() || (manifest.needsApiUser && source.apiUser.isBlank()))
                ) return@async emptyList<BrainrotWallpaper>()
                if (!PluginExecutor.canServe(manifest, false, source)) return@async emptyList<BrainrotWallpaper>()
                val queries = buildList {
                    add(source.tags.trim())
                    if (source.tags.isBlank()) likedTags.shuffled().take(2).forEach { add(it) }
                }.map { q ->
                    if (manifest.maxTagCount == Int.MAX_VALUE) q
                    else q.split(Regex("\\s+")).filter { it.isNotBlank() }.take(manifest.maxTagCount).joinToString(" ")
                }.distinct()
                queries.map { q ->
                    async {
                        try {
                            PluginExecutor.fetchPage(manifest, source, q, emptyList(), false, filters, 60)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppErrorLog.log("DailyPick", "${source.pluginId} q=$q", e)
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }.awaitAll().flatten()

        val usable = candidates
            .distinctBy(::keyOf)
            .filter { wp ->
                val key = keyOf(wp)
                !wp.isVideo && !wp.isNsfw &&
                    !MediaType.isVideoUrl(wp.fullUrl) &&
                    key !in past && key !in seen && key !in blockedKeys && key !in savedIds &&
                    wp.fullUrl !in savedUrls && !blocklist.blocks(wp)
            }
        // Never-seen images first; if the user has scrolled past everything, allow seen ones.
        val pool = usable.ifEmpty {
            candidates.distinctBy(::keyOf).filter { wp ->
                val key = keyOf(wp)
                !wp.isVideo && !wp.isNsfw && key !in past && key !in blockedKeys && key !in savedIds && !blocklist.blocks(wp)
            }
        }
        pool.maxByOrNull { LearnedTaste.score(it, weights, foldCanvas) + Random.nextFloat() * 0.6f }
    }

    private suspend fun downloadPreview(context: Context, wp: BrainrotWallpaper): Bitmap? {
        val url = wp.sampleUrl.ifBlank { wp.fullUrl }.takeIf { !MediaType.isVideoUrl(it) } ?: wp.thumbUrl
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(1080, 1080)
            .scale(Scale.FILL)
            .build()
        val bitmap = runCatching {
            ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
        if (bitmap == null) {
            // Don't leave yesterday's picture on the widget under today's pick.
            previewFile(context).delete()
            return null
        }
        runCatching {
            val tmp = File(context.filesDir, "$PREVIEW_FILE.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            tmp.renameTo(previewFile(context))
        }
        return bitmap
    }

    /** History entry for when the pick is set as the wallpaper. */
    fun historyItem(wp: BrainrotWallpaper) = WallpaperHistoryItem(
        thumbUrl = wp.thumbUrl, sampleUrl = wp.sampleUrl, fullUrl = wp.fullUrl,
        source = wp.source, timestamp = System.currentTimeMillis(),
        tags = wp.tags, pageUrl = wp.pageUrl,
    )

    private fun toJson(wp: BrainrotWallpaper) = JSONObject().apply {
        put("id", wp.id)
        put("source", wp.source)
        put("thumbUrl", wp.thumbUrl)
        put("sampleUrl", wp.sampleUrl)
        put("fullUrl", wp.fullUrl)
        put("resolution", wp.resolution)
        put("pageUrl", wp.pageUrl)
        put("tags", JSONArray(wp.tags))
    }

    private fun fromJson(o: JSONObject): BrainrotWallpaper {
        val tags = o.optJSONArray("tags")
        return BrainrotWallpaper(
            id = o.getString("id"),
            source = o.optString("source"),
            thumbUrl = o.optString("thumbUrl"),
            sampleUrl = o.optString("sampleUrl"),
            fullUrl = o.optString("fullUrl"),
            resolution = o.optString("resolution"),
            pageUrl = o.optString("pageUrl"),
            tags = if (tags == null) emptyList() else List(tags.length()) { tags.optString(it) },
        )
    }
}
