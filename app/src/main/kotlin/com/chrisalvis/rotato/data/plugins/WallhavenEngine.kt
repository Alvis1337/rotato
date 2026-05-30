package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
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
        var url = "$base/api/v1/search?q=${query.trim().urlEncode()}&categories=111&purity=$purity&sorting=random"
        when (filters.minResolution) {
            MinResolution.ANY -> Unit
            MinResolution.MY_PHONE ->
                if (filters.phoneScreenWidth > 0 && filters.phoneScreenHeight > 0)
                    url += "&atleast=${filters.phoneScreenWidth}x${filters.phoneScreenHeight}"
            else -> url += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
        }
        when (filters.aspectRatio) {
            AspectRatio.ANY -> Unit
            AspectRatio.MY_PHONE -> url += "&ratios=9x16"
            else -> url += "&ratios=${filters.aspectRatio.wallhavenKey}"
        }
        if (source.apiKey.isNotBlank()) url += "&apikey=${source.apiKey.urlEncode()}"
        return url
    }

    private fun buildWallpaper(post: org.json.JSONObject, base: String, manifest: PluginManifest, query: String): BrainrotWallpaper? {
        val id = post.optString("id").ifBlank { return null }
        val fullUrl = post.optString("path").ifBlank { return null }
        val thumbs = post.optJSONObject("thumbs")
        val thumbUrl = thumbs?.optString("small").takeUnless { it.isNullOrBlank() }
            ?: thumbs?.optString("original").takeUnless { it.isNullOrBlank() }
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
            tags = effectiveTags
        )
    }

    private fun effectivePurity(stored: String, nsfw: Boolean): String {
        if (nsfw) return "001"
        val s = stored.takeIf { it.length == 3 } ?: "110"
        val effective = "${s[0]}${s[1]}0"
        return if (effective == "000") "100" else effective
    }
}
