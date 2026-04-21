package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource

object GelbooruPlugin : SourcePlugin() {
    override val id = "GELBOORU"
    override val displayName = "Gelbooru"
    override val description = "High-volume anime/game art booru with optional API credentials."
    override val isPremium = true
    override val needsApiKey = true
    override val needsApiUser = true
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "User ID"
    override val safeContent = false

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val normalized = normalizeBooruQuery(query)
        val tagQuery = buildString {
            if (normalized.isNotBlank()) append(normalized)
            if (!nsfw) { if (normalized.isNotBlank()) append(" "); append("rating:general") }
        }.trim()
        val urlBase = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=20&tags=${tagQuery.urlEncode()}"
        val url = if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "$urlBase&api_key=${source.apiKey.urlEncode()}&user_id=${source.apiUser.urlEncode()}"
        else urlBase
        val json = getJson(url) ?: return@onIO null
        val arr = json.optJSONArray("post") ?: return@onIO null
        val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return@onIO null
        val id = post.optInt("id", 0).toString()
        val fullUrl = post.optString("file_url").ifBlank { return@onIO null }
        BrainrotWallpaper(
            id = id, source = "gelbooru",
            thumbUrl = post.optString("preview_url").ifBlank { fullUrl },
            fullUrl = fullUrl,
            resolution = "${post.optInt("width")}x${post.optInt("height")}",
            pageUrl = "https://gelbooru.com/index.php?page=post&s=view&id=$id",
            tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
        )
    }
}
