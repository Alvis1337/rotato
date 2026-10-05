package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.MediaType
import com.chrisalvis.rotato.data.MinResolution

/** Engine for the Wallhaven API (`/api/v1/search?sorting=random`). */
object WallhavenEngine : PluginEngine() {
    override val protocol = Protocol.WALLHAVEN

    override fun canServe(manifest: PluginManifest, nsfw: Boolean, source: LocalSource): Boolean {
        if (nsfw && source.apiKey.isBlank()) return false
        return super.canServe(manifest, nsfw, source)
    }

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
        val url = buildUrl(base, query, source, nsfw, filters)
        val json = getJson(url) ?: return@onIO null
        val data = json.optJSONArray("data") ?: return@onIO null
        val post = pickRandom(data, exclude) ?: return@onIO null
        buildWallpaper(post, base, manifest, query)
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
        val url = buildUrl(base, query, source, nsfw, filters)
        val json = getJson(url) ?: return@onIO emptyList()
        val data = json.optJSONArray("data") ?: return@onIO emptyList()
        (0 until data.length()).mapNotNull { i ->
            val post = data.optJSONObject(i) ?: return@mapNotNull null
            if (exclude.contains(post.optString("id"))) return@mapNotNull null
            buildWallpaper(post, base, manifest, query)
        }
    }

    private fun buildUrl(base: String, query: String, source: LocalSource, nsfw: Boolean, filters: BrainrotFilters): String {
        val purity = effectivePurity(source.wallhavenPurity, nsfw)
        // categories = general/anime/people bits.
        val categories = if (filters.animeOnly) "010" else "111"
        // Wallhaven tags use spaces; MAL and tier queries arrive booru-style ("shingeki_no_kyojin").
        var url = "$base/api/v1/search?q=${query.trim().replace('_', ' ').urlEncode()}&categories=$categories&purity=$purity&sorting=random"
        when (filters.minResolution) {
            MinResolution.ANY -> Unit
            MinResolution.MY_PHONE ->
                if (filters.phoneScreenWidth > 0 && filters.phoneScreenHeight > 0)
                    url += "&atleast=${filters.phoneMinWidth}x${filters.phoneMinHeight}"
            else -> url += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        }
        when (filters.aspectRatio) {
            AspectRatio.ANY -> Unit
            // Foldables: the unfolded screen is near-square, so tall-to-square portrait ratios all fit.
            AspectRatio.MY_PHONE -> url += if (filters.phoneMaxAspect > 0.7f) "&ratios=9x16,10x16,9x18,1x1" else "&ratios=9x16"
            else -> url += "&ratios=${filters.aspectRatio.wallhavenKey}"
        }
        if (source.apiKey.isNotBlank()) url += "&apikey=${source.apiKey.urlEncode()}"
        return url
    }

    private fun buildWallpaper(post: org.json.JSONObject, base: String, manifest: PluginManifest, query: String): BrainrotWallpaper? {
        val id = post.optString("id").ifBlank { return null }
        val fullUrl = post.optString("path").ifBlank { return null }
        val thumbs = post.optJSONObject("thumbs")
        // "original" keeps the image's aspect ratio (small/large are 3:2 crops), which matters
        // for the aspect-ratio-sized Discover tiles.
        val thumbUrl = thumbs?.optString("original").takeUnless { it.isNullOrBlank() }
            ?: thumbs?.optString("large").takeUnless { it.isNullOrBlank() }
            ?: thumbs?.optString("small").takeUnless { it.isNullOrBlank() }
            ?: fullUrl
        val tags = post.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
        } ?: emptyList()
        val effectiveTags = tags.ifEmpty { query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() } }
        return BrainrotWallpaper(
            id = id,
            source = manifest.id.lowercase(),
            thumbUrl = thumbUrl, sampleUrl = fullUrl, fullUrl = fullUrl,
            resolution = post.optString("resolution").ifBlank { "" },
            pageUrl = "$base/w/$id",
            tags = effectiveTags,
            isVideo = MediaType.isVideoUrl(fullUrl),
            isNsfw = post.optString("purity") != "sfw"
        )
    }

    private fun effectivePurity(stored: String, nsfw: Boolean): String {
        val s = stored.takeIf { it.length == 3 } ?: "110"
        return if (nsfw) {
            "${s[0]}${s[1]}1"
        } else {
            val effective = "${s[0]}${s[1]}0"
            if (effective == "000") "100" else effective
        }
    }
}
