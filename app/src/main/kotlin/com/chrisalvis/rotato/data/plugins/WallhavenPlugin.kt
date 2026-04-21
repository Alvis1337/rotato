package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.MinResolution

object WallhavenPlugin : SourcePlugin() {
    override val id = "WALLHAVEN"
    override val displayName = "Wallhaven"
    override val description = "Curated high-resolution wallpapers. API key enables NSFW content."
    override val isPremium = true
    override val needsApiKey = true
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = true

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val purity = if (nsfw) "111" else "110"
        var urlBase = "https://wallhaven.cc/api/v1/search?q=${query.trim().urlEncode()}&categories=111&purity=$purity&sorting=random"
        if (filters.minResolution != MinResolution.ANY)
            urlBase += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        if (filters.aspectRatio != AspectRatio.ANY)
            urlBase += "&ratios=${filters.aspectRatio.wallhavenKey}"
        val url = if (source.apiKey.isNotBlank()) "$urlBase&apikey=${source.apiKey.urlEncode()}" else urlBase
        val json = getJson(url) ?: return@onIO null
        val data = json.optJSONArray("data") ?: return@onIO null
        val post = pickRandom(data, exclude) ?: return@onIO null
        val id = post.optString("id")
        val fullUrl = post.optString("path").ifBlank { return@onIO null }
        val thumbs = post.optJSONObject("thumbs")
        val thumbUrl = thumbs?.optString("small") ?: thumbs?.optString("original") ?: fullUrl
        val tags = post.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
        } ?: emptyList()
        BrainrotWallpaper(
            id = id, source = "wallhaven",
            thumbUrl = thumbUrl, fullUrl = fullUrl,
            resolution = post.optString("resolution").ifBlank { "" },
            pageUrl = "https://wallhaven.cc/w/$id",
            tags = tags.take(12)
        )
    }
}
