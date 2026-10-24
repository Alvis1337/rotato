package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Auto-tag your photos: names what's in your own pictures ("beach", "sunset", "cat") on the phone
 * with ML Kit, so search and smart collections work on them like on tagged booru images. Photos
 * in collections get the tags saved on their entry; Library images keep them here, by file.
 */
object PhotoTagger {
    private const val PREFS = "photo_labels"
    private const val MIN_CONFIDENCE = 0.7f
    private const val MAX_TAGS = 8

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tags for each Library file name that has been tagged. */
    fun libraryTags(context: Context): Map<String, List<String>> =
        sp(context).all.mapNotNull { (name, raw) ->
            val tags = runCatching { JSONObject(raw as String).optJSONArray("t") }.getOrNull() ?: return@mapNotNull null
            name to (0 until tags.length()).map { tags.optString(it) }
        }.toMap()

    private fun decodeSmall(file: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 640) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** "Sky" → "sky", "Hand rail" → "hand_rail", in the booru style the rest of the app uses. */
    private fun asTag(label: String) = label.trim().lowercase().replace(Regex("\\s+"), "_")

    private fun label(labeler: com.google.mlkit.vision.label.ImageLabeler, file: File): List<String>? {
        val bmp = decodeSmall(file) ?: return null
        return try {
            Tasks.await(labeler.process(InputImage.fromBitmap(bmp, 0)), 20, TimeUnit.SECONDS)
                .filter { it.confidence >= MIN_CONFIDENCE }
                .sortedByDescending { it.confidence }
                .take(MAX_TAGS)
                .map { asTag(it.text) }
        } catch (e: Exception) {
            null
        }
    }

    /** How many photos still need tags: collection photos from this phone and untagged Library images. */
    suspend fun pending(context: Context): Int = withContext(Dispatchers.IO) { work(context).let { it.first.size + it.second.size } }

    private suspend fun work(context: Context): Pair<List<LocalWallpaperEntry>, List<File>> {
        val tagged = libraryTags(context).keys
        val entries = LocalListsPreferences(context).allWallpapers.first()
            .filter { it.source == "device" && it.fullUrl.startsWith("list_images/") && it.tags.isEmpty() }
        val library = ImageRepository(context).getImages().filter { it.name !in tagged }
        return entries to library
    }

    /** Tags everything [pending] counts. Returns how many photos got tags. */
    suspend fun tagAll(context: Context, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val (entries, library) = work(context)
        val total = entries.size + library.size
        if (total == 0) return@withContext 0
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        var done = 0
        var tagged = 0
        try {
            // One file is often in several collections: label it once.
            val byFile = entries.groupBy { it.fullUrl }
            val updates = mutableListOf<LocalWallpaperEntry>()
            for ((path, group) in byFile) {
                val tags = label(labeler, File(context.filesDir, path))
                if (!tags.isNullOrEmpty()) { updates += group.map { it.copy(tags = tags) }; tagged++ }
                done += group.size
                onProgress(done, total)
            }
            LocalListsPreferences(context).updateEntries(updates)
            val edit = sp(context).edit()
            for (file in library) {
                // Empty results are saved too, so a photo isn't re-checked every time.
                val tags = label(labeler, file)
                if (tags != null) {
                    edit.putString(file.name, JSONObject().put("t", JSONArray(tags)).toString())
                    if (tags.isNotEmpty()) tagged++
                }
                onProgress(++done, total)
            }
            edit.apply()
        } finally {
            labeler.close()
        }
        tagged
    }
}
