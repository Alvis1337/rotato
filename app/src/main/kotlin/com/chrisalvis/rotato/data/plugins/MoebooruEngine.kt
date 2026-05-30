package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches

/** Engine for Moebooru-compatible APIs (`/post.json?tags=...order:random`). */
object MoebooruEngine : PluginEngine() {
    override val protocol = Protocol.MOEBOORU

    override suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = onIO {
        if (!canServe(manifest, nsfw, source)) return@onIO null
        val base = baseUrl(manifest, source)
        val tagQuery = buildTagQuery(query, nsfw)
        val arr = getJsonArray("$base/post.json?tags=${tagQuery.urlEncode()}&limit=20") ?: return@onIO null
        val post = run {
            for (i in (0 until arr.length()).shuffled()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (exclude.contains(obj.optInt("id", 0).toString())) continue
                val w = obj.optInt("width"); val h = obj.optInt("height")
                if (!filters.matches(w, h)) continue
                if (obj.optString("file_url").isNotBlank()) return@run obj
            }
            null
        } ?: return@onIO null
        buildWallpaper(post, base, manifest)
    }

    override suspend fun fetchPage(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        if (!canServe(manifest, nsfw, source)) return@onIO emptyList()
        val base = baseUrl(manifest, source)
        val tagQuery = buildTagQuery(query, nsfw)
        val arr = getJsonArray("$base/post.json?tags=${tagQuery.urlEncode()}&limit=$limit") ?: return@onIO emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", 0).toString()
            if (exclude.contains(id)) return@mapNotNull null
            val w = obj.optInt("width"); val h = obj.optInt("height")
            if (!filters.matches(w, h)) return@mapNotNull null
            buildWallpaper(obj, base, manifest)
        }
    }

    private fun buildTagQuery(query: String, nsfw: Boolean): String = buildString {
        val normalized = normalizeUserQuery(query)
        if (normalized.isNotBlank()) { append(normalized); append(' ') }
        append(if (nsfw) "rating:explicit" else "rating:safe")
        append(" order:random")
    }.trim()

    private fun buildWallpaper(obj: org.json.JSONObject, base: String, manifest: PluginManifest): BrainrotWallpaper? {
        val id = obj.optInt("id", 0).toString()
        val fullUrl = obj.optString("file_url").ifBlank { return null }
        val sampleUrl = obj.optString("sample_url").ifBlank { fullUrl }
        return BrainrotWallpaper(
            id = id,
            source = manifest.id.lowercase(),
            thumbUrl = obj.optString("preview_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${obj.optInt("width")}x${obj.optInt("height")}",
            pageUrl = "$base/post/show/$id",
            tags = obj.optString("tags").split(' ').filter { it.isNotBlank() }
        )
    }
}
