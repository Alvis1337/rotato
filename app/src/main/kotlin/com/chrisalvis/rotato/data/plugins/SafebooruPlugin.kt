package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource

object SafebooruPlugin : SourcePlugin() {
    override val id = "SAFEBOORU"
    override val displayName = "Safebooru"
    override val description = "Safe-for-work booru board. No account needed."
    override val isPremium = false
    override val needsApiKey = false
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = true

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val tagQuery = normalizeBooruQuery(query)
        val pid = (0..200).random()
        val url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=20&pid=$pid&tags=${tagQuery.urlEncode()}"
        val arr = getJsonArray(url) ?: return@onIO null
        val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return@onIO null
        val id = post.optInt("id", 0).toString()
        val directory = post.optString("directory")
        val image = post.optString("image")
        // Skip video posts
        val videoExts = listOf(".mp4", ".webm", ".mkv")
        if (videoExts.any { image.endsWith(it, ignoreCase = true) }) return@onIO null
        val fullUrl = "https://safebooru.org/images/$directory/$image"
        val sampleUrl = post.optString("sample_url").ifBlank { fullUrl }
        val thumbUrl = post.optString("preview_url").ifBlank { sampleUrl }
        BrainrotWallpaper(
            id = id, source = "safebooru",
            thumbUrl = thumbUrl, sampleUrl = sampleUrl, fullUrl = fullUrl,
            resolution = "${post.optInt("width")}x${post.optInt("height")}",
            pageUrl = "https://safebooru.org/index.php?page=post&s=view&id=$id",
            tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
        )
    }

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> = onIO {
        val tagQuery = normalizeBooruQuery(query)
        val pid = (0..200).random()
        val url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=$limit&pid=$pid&tags=${tagQuery.urlEncode()}"
        val arr = getJsonArray(url) ?: return@onIO emptyList()
        val videoExts = listOf(".mp4", ".webm", ".mkv")
        (0 until arr.length()).mapNotNull { i ->
            val post = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = post.optInt("id", 0).toString()
            if (exclude.contains(id)) return@mapNotNull null
            val image = post.optString("image")
            if (videoExts.any { image.endsWith(it, ignoreCase = true) }) return@mapNotNull null
            val directory = post.optString("directory")
            val fullUrl = "https://safebooru.org/images/$directory/$image"
            val sampleUrl = post.optString("sample_url").ifBlank { fullUrl }
            val thumbUrl = post.optString("preview_url").ifBlank { sampleUrl }
            BrainrotWallpaper(
                id = id, source = "safebooru",
                thumbUrl = thumbUrl, sampleUrl = sampleUrl, fullUrl = fullUrl,
                resolution = "${post.optInt("width")}x${post.optInt("height")}",
                pageUrl = "https://safebooru.org/index.php?page=post&s=view&id=$id",
                tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
            )
        }
    }
}
