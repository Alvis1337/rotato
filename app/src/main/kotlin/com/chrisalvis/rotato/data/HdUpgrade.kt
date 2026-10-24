package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** A sharper copy of a saved wallpaper, found by image similarity. */
data class HdMatch(
    val entry: LocalWallpaperEntry,
    val postId: String,
    val width: Int,
    val height: Int,
    val fullUrl: String,
    val sampleUrl: String,
    val thumbUrl: String,
    val tags: List<String>,
    val explicit: Boolean,
    val similarity: Float,
) {
    val resolution: String get() = "${width}x$height"
}

/**
 * HD upgrade: finds saved wallpapers smaller than this phone's screen and looks each one up with
 * Danbooru's reverse image search (IQDB), which matches the same picture even when it was
 * resized or re-saved elsewhere. Only clearly bigger copies of a near-identical picture count.
 */
object HdUpgrade {
    private const val MIN_SIMILARITY = 88f
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Width x height of [entry], from its saved resolution or the photo itself. */
    private fun sizeOf(context: Context, entry: LocalWallpaperEntry): Pair<Int, Int>? {
        entry.resolution.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }
            .takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }?.let { return it[0] to it[1] }
        if (!entry.fullUrl.startsWith("list_images/")) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(context.filesDir, entry.fullUrl).absolutePath, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    /** Saved stills that come out soft on this phone: their short side is under the screen's. */
    suspend fun candidates(context: Context): List<LocalWallpaperEntry> = withContext(Dispatchers.IO) {
        val screens = knownDisplaySizes(context)
        val screenShort = screens.maxOfOrNull { minOf(it.width, it.height) } ?: 1080
        LocalListsPreferences(context).allWallpapers.first()
            .filter { !it.isVideo && (it.fullUrl.startsWith("http") || it.fullUrl.startsWith("list_images/")) }
            .distinctBy { it.source to it.sourceId }
            .filter { e -> sizeOf(context, e)?.let { (w, h) -> minOf(w, h) < screenShort * 0.9f } == true }
    }

    private fun danbooruAuth(sources: List<LocalSource>): String? =
        sources.firstOrNull { it.pluginId.equals("DANBOORU", true) && it.apiKey.isNotBlank() && it.apiUser.isNotBlank() }
            ?.let { s -> okhttp3.Credentials.basic(s.apiUser.trim(), s.apiKey.trim()) }

    /** Thrown when Danbooru won't run the search without an account. */
    class NeedsAccount : Exception("Danbooru's image search needs your Danbooru login. Add it under Sources › Danbooru")

    /** The best clearly-larger match for [entry], or null. */
    suspend fun find(context: Context, entry: LocalWallpaperEntry): HdMatch? = withContext(Dispatchers.IO) {
        val (w0, h0) = sizeOf(context, entry) ?: return@withContext null
        val auth = danbooruAuth(LocalSourcesPreferences(context).sources.first())
        val base = "https://danbooru.donmai.us/iqdb_queries.json"
        val request = when {
            entry.source.equals("danbooru", true) && entry.sourceId.isNotBlank() ->
                Request.Builder().url("$base?search[post_id]=${entry.sourceId}&limit=5")
            entry.fullUrl.startsWith("http") -> {
                val imageUrl = URLEncoder.encode(entry.sampleUrl.ifBlank { entry.fullUrl }, "UTF-8")
                Request.Builder().url("$base?search[url]=$imageUrl&limit=5")
            }
            else -> {
                val file = File(context.filesDir, entry.fullUrl).takeIf { it.exists() } ?: return@withContext null
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("search[file]", file.name, file.asRequestBody("image/*".toMediaType()))
                    .addFormDataPart("limit", "5")
                    .build()
                Request.Builder().url(base).post(body)
            }
        }.header("User-Agent", "Rotato/1.0 (wallpaper app)")
            .apply { if (auth != null) header("Authorization", auth) }
            .build()
        val text = http.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw NeedsAccount()
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        } ?: return@withContext null
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return@withContext null
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val score = o.optDouble("score", 0.0).toFloat()
            val post = o.optJSONObject("post") ?: return@mapNotNull null
            val w = post.optInt("image_width"); val h = post.optInt("image_height")
            val full = post.optString("file_url").ifBlank { return@mapNotNull null }
            if (score < MIN_SIMILARITY || w.toLong() * h < w0.toLong() * h0 * 3 / 2) return@mapNotNull null
            if (post.optString("file_ext") in setOf("mp4", "webm", "zip", "gif")) return@mapNotNull null
            HdMatch(
                entry = entry,
                postId = post.optInt("id").toString(),
                width = w, height = h,
                fullUrl = full,
                sampleUrl = post.optString("large_file_url").ifBlank { full },
                thumbUrl = post.optString("preview_file_url").ifBlank { post.optString("large_file_url") },
                tags = post.optString("tag_string").split(' ').filter { it.isNotBlank() },
                explicit = post.optString("rating") in setOf("q", "e"),
                similarity = score,
            )
        }.maxByOrNull { it.width.toLong() * it.height }
    }

    /** Looks up every candidate, one at a time (the search is rate-limited). */
    suspend fun findAll(context: Context, entries: List<LocalWallpaperEntry>, onProgress: (Int, Int) -> Unit): List<HdMatch> {
        val out = mutableListOf<HdMatch>()
        entries.forEachIndexed { i, e ->
            runCatching { find(context, e) }.onSuccess { m -> if (m != null) out += m }.onFailure { if (it is NeedsAccount) throw it }
            onProgress(i + 1, entries.size)
            delay(1_100)
        }
        return out
    }

    /** Swaps each match in for every saved copy of its picture, keeping collections and order. */
    suspend fun apply(context: Context, matches: List<HdMatch>) {
        val prefs = LocalListsPreferences(context)
        val all = prefs.allWallpapers.first()
        val updates = matches.flatMap { m ->
            all.filter { it.source == m.entry.source && it.sourceId == m.entry.sourceId }.map { e ->
                e.copy(
                    source = "danbooru",
                    sourceId = m.postId,
                    fullUrl = m.fullUrl,
                    sampleUrl = m.sampleUrl,
                    thumbUrl = m.thumbUrl,
                    resolution = m.resolution,
                    pageUrl = "https://danbooru.donmai.us/posts/${m.postId}",
                    tags = e.tags.ifEmpty { m.tags },
                    isNsfw = e.isNsfw || m.explicit,
                )
            }
        }
        prefs.updateEntries(updates)
        // Rotation collections download the sharper copies into the pool now.
        val rotationIds = prefs.lists.first().filter { it.useAsRotation }.mapTo(HashSet()) { it.id }
        if (rotationIds.isNotEmpty()) com.chrisalvis.rotato.worker.ScheduleReceiver.syncRotationPool(context, rotationIds, prefs)
    }
}
