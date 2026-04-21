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
        // Wallhaven purity string format: "SFW|Sketchy|NSFW" — e.g. "110" = SFW+Sketchy, "111" = all
        val requestedPurity = source.wallhavenPurity.padStart(3, '0')
        val effectivePurity = if (!nsfw) {
            // Strip NSFW bit (index 2) when nsfwMode is off
            val bits = requestedPurity.toCharArray()
            bits[2] = '0'
            String(bits)
        } else requestedPurity
        // Fall back to "100" (SFW only) if all bits are off
        val purity = if (effectivePurity.all { it == '0' }) "100" else effectivePurity
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
        // Wallhaven has no intermediate sample size — thumbs are ~300px and look zoomed in
        // cards. Use fullUrl so the grid gets the correct proportions (same trade-off as booru sources).
        val sampleUrl = fullUrl
        val tags = post.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
        } ?: emptyList()
        BrainrotWallpaper(
            id = id, source = "wallhaven",
            thumbUrl = thumbUrl, sampleUrl = sampleUrl, fullUrl = fullUrl,
            resolution = post.optString("resolution").ifBlank { "" },
            pageUrl = "https://wallhaven.cc/w/$id",
            tags = tags.take(12)
        )
    }

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> = onIO {
        val requestedPurity = source.wallhavenPurity.padStart(3, '0')
        val effectivePurity = if (!nsfw) {
            val bits = requestedPurity.toCharArray(); bits[2] = '0'; String(bits)
        } else requestedPurity
        val purity = if (effectivePurity.all { it == '0' }) "100" else effectivePurity
        var urlBase = "https://wallhaven.cc/api/v1/search?q=${query.trim().urlEncode()}&categories=111&purity=$purity&sorting=random"
        if (filters.minResolution != MinResolution.ANY)
            urlBase += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        if (filters.aspectRatio != AspectRatio.ANY)
            urlBase += "&ratios=${filters.aspectRatio.wallhavenKey}"
        val url = if (source.apiKey.isNotBlank()) "$urlBase&apikey=${source.apiKey.urlEncode()}" else urlBase
        val json = getJson(url) ?: return@onIO emptyList()
        val data = json.optJSONArray("data") ?: return@onIO emptyList()
        (0 until data.length()).mapNotNull { i ->
            val post = data.optJSONObject(i) ?: return@mapNotNull null
            val id = post.optString("id")
            if (exclude.contains(id)) return@mapNotNull null
            val fullUrl = post.optString("path").ifBlank { return@mapNotNull null }
            val thumbs = post.optJSONObject("thumbs")
            val thumbUrl = thumbs?.optString("small") ?: thumbs?.optString("original") ?: fullUrl
            val tags = post.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
            } ?: emptyList()
            BrainrotWallpaper(
                id = id, source = "wallhaven",
                thumbUrl = thumbUrl, sampleUrl = fullUrl, fullUrl = fullUrl,
                resolution = post.optString("resolution").ifBlank { "" },
                pageUrl = "https://wallhaven.cc/w/$id",
                tags = tags.take(12)
            )
        }
    }
}
