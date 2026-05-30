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

    /** NSFW content on Wallhaven requires an API key — without one all purity=001 calls return 0 results. */
    override fun canServe(nsfw: Boolean, source: LocalSource): Boolean {
        if (nsfw && source.apiKey.isBlank()) return false
        return super.canServe(nsfw, source)
    }

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val purity = effectivePurity(source.wallhavenPurity, nsfw)
        var urlBase = "https://wallhaven.cc/api/v1/search?q=${query.trim().urlEncode()}&categories=111&purity=$purity&sorting=random"
        when (filters.minResolution) {
            MinResolution.ANY -> Unit
            MinResolution.MY_PHONE ->
                if (filters.phoneScreenWidth > 0 && filters.phoneScreenHeight > 0)
                    urlBase += "&atleast=${filters.phoneScreenWidth}x${filters.phoneScreenHeight}"
            else -> urlBase += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        }
        when (filters.aspectRatio) {
            AspectRatio.ANY -> Unit
            AspectRatio.MY_PHONE ->
                urlBase += "&ratios=9x16"
            else -> urlBase += "&ratios=${filters.aspectRatio.wallhavenKey}"
        }
        val url = if (source.apiKey.isNotBlank()) "$urlBase&apikey=${source.apiKey.urlEncode()}" else urlBase
        val json = getJson(url) ?: return@onIO null
        val data = json.optJSONArray("data") ?: return@onIO null
        val post = pickRandom(data, exclude) ?: return@onIO null
        val id = post.optString("id")
        val fullUrl = post.optString("path").ifBlank { return@onIO null }
        val thumbs = post.optJSONObject("thumbs")
        val thumbUrl = thumbs?.optString("small").takeUnless { it.isNullOrBlank() }
            ?: thumbs?.optString("original").takeUnless { it.isNullOrBlank() }
            ?: fullUrl
        // cards. Use fullUrl so the grid gets the correct proportions (same trade-off as booru sources).
        val sampleUrl = fullUrl
        val tags = post.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
        } ?: emptyList()
        // Wallhaven's search API omits tag objects — fall back to the search query as synthetic tags
        val effectiveTags = tags.ifEmpty { query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() } }
        BrainrotWallpaper(
            id = id, source = "wallhaven",
            thumbUrl = thumbUrl, sampleUrl = sampleUrl, fullUrl = fullUrl,
            resolution = post.optString("resolution").ifBlank { "" },
            pageUrl = "https://wallhaven.cc/w/$id",
            tags = effectiveTags
        )
    }

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> = onIO {
        val purity = effectivePurity(source.wallhavenPurity, nsfw)
        var urlBase = "https://wallhaven.cc/api/v1/search?q=${query.trim().urlEncode()}&categories=111&purity=$purity&sorting=random"
        when (filters.minResolution) {
            MinResolution.ANY -> Unit
            MinResolution.MY_PHONE ->
                if (filters.phoneScreenWidth > 0 && filters.phoneScreenHeight > 0)
                    urlBase += "&atleast=${filters.phoneScreenWidth}x${filters.phoneScreenHeight}"
            else -> urlBase += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        }
        when (filters.aspectRatio) {
            AspectRatio.ANY -> Unit
            AspectRatio.MY_PHONE ->
                urlBase += "&ratios=9x16"
            else -> urlBase += "&ratios=${filters.aspectRatio.wallhavenKey}"
        }
        val url = if (source.apiKey.isNotBlank()) "$urlBase&apikey=${source.apiKey.urlEncode()}" else urlBase
        val json = getJson(url) ?: return@onIO emptyList()
        val data = json.optJSONArray("data") ?: return@onIO emptyList()
        (0 until data.length()).mapNotNull { i ->
            val post = data.optJSONObject(i) ?: return@mapNotNull null
            val id = post.optString("id")
            if (exclude.contains(id)) return@mapNotNull null
            val fullUrl = post.optString("path").ifBlank { return@mapNotNull null }
            val thumbs = post.optJSONObject("thumbs")
            val thumbUrl = thumbs?.optString("small").takeUnless { it.isNullOrBlank() }
                ?: thumbs?.optString("original").takeUnless { it.isNullOrBlank() }
                ?: fullUrl
            val tags = post.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
            } ?: emptyList()
            val effectiveTags = tags.ifEmpty { query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() } }
            BrainrotWallpaper(
                id = id, source = "wallhaven",
                thumbUrl = thumbUrl, sampleUrl = fullUrl, fullUrl = fullUrl,
                resolution = post.optString("resolution").ifBlank { "" },
                pageUrl = "https://wallhaven.cc/w/$id",
                tags = effectiveTags
            )
        }
    }

    /**
     * Computes the effective Wallhaven purity string.
     * - nsfw=true  → "001" (NSFW only, requires API key — enforced via canServe())
     * - nsfw=false → mask off NSFW bit from stored preference; fall back to "100" if all zero
     * Format: 3-char "SFW Sketchy NSFW", e.g. "110" = SFW+Sketchy, "001" = NSFW only.
     */
    private fun effectivePurity(stored: String, nsfw: Boolean): String {
        if (nsfw) return "001"
        val s = stored.takeIf { it.length == 3 } ?: "110"
        val effective = "${s[0]}${s[1]}0"
        return if (effective == "000") "100" else effective
    }
}
