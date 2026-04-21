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
        val fullUrl = "https://safebooru.org/images/$directory/$image"
        val thumbUrl = post.optString("preview_url").ifBlank { fullUrl }
        BrainrotWallpaper(
            id = id, source = "safebooru",
            thumbUrl = thumbUrl, fullUrl = fullUrl,
            resolution = "${post.optInt("width")}x${post.optInt("height")}",
            pageUrl = "https://safebooru.org/index.php?page=post&s=view&id=$id",
            tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
        )
    }
}
