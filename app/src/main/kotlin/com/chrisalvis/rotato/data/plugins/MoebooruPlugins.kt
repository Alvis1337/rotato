package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches

object KonachanPlugin : SourcePlugin() {
    override val id = "KONACHAN"
    override val displayName = "Konachan"
    override val description = "High-quality anime wallpapers (Moebooru). No account needed."
    override val isPremium = true
    override val needsApiKey = false
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = true

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? =
        fetchMoebooru(query, exclude, nsfw, "konachan.com", "konachan", filters)

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> =
        fetchMoebooruPage(query, exclude, nsfw, "konachan.com", "konachan", filters, limit)
}

object YanderePlugin : SourcePlugin() {
    override val id = "YANDERE"
    override val displayName = "Yande.re"
    override val description = "Anime art archive with high resolution scans (Moebooru). No account needed."
    override val isPremium = true
    override val needsApiKey = false
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = false

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? =
        fetchMoebooru(query, exclude, nsfw, "yande.re", "yandere", filters)

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> =
        fetchMoebooruPage(query, exclude, nsfw, "yande.re", "yandere", filters, limit)
}

private suspend fun fetchMoebooru(
    query: String,
    exclude: List<String>,
    nsfw: Boolean,
    host: String,
    sourceName: String,
    filters: BrainrotFilters,
): BrainrotWallpaper? = onIO {
    val tagQuery = buildString {
        val normalized = normalizeUserQuery(query)
        if (normalized.isNotBlank()) append("$normalized ")
        if (!nsfw) append("rating:safe ")
        append("order:random")
    }.trim()
    val url = "https://$host/post.json?tags=${tagQuery.urlEncode()}&limit=20"
    val arr = getJsonArray(url) ?: return@onIO null
    // Iterate shuffled posts to find one that passes filters and has an accessible file_url
    val post = run {
        val indices = (0 until arr.length()).shuffled()
        for (i in indices) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0).toString()
            if (exclude.contains(id)) continue
            val w = obj.optInt("width"); val h = obj.optInt("height")
            if (!filters.matches(w, h)) continue
            if (obj.optString("file_url").isNotBlank()) return@run obj
        }
        null
    } ?: return@onIO null
    val id = post.optInt("id", 0).toString()
    val fullUrl = post.optString("file_url")
    val sampleUrl = post.optString("sample_url").ifBlank { fullUrl }
    BrainrotWallpaper(
        id = id, source = sourceName,
        thumbUrl = post.optString("preview_url").ifBlank { sampleUrl },
        sampleUrl = sampleUrl,
        fullUrl = fullUrl,
        resolution = "${post.optInt("width")}x${post.optInt("height")}",
        pageUrl = "https://$host/post/show/$id",
        tags = post.optString("tags").split(" ").filter { it.isNotBlank() }
    )
}

private suspend fun fetchMoebooruPage(
    query: String,
    exclude: List<String>,
    nsfw: Boolean,
    host: String,
    sourceName: String,
    filters: BrainrotFilters,
    limit: Int,
): List<BrainrotWallpaper> = onIO {
    val tagQuery = buildString {
        val normalized = normalizeUserQuery(query)
        if (normalized.isNotBlank()) append("$normalized ")
        if (!nsfw) append("rating:safe ")
        append("order:random")
    }.trim()
    val url = "https://$host/post.json?tags=${tagQuery.urlEncode()}&limit=$limit"
    val arr = getJsonArray(url) ?: return@onIO emptyList()
    (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = obj.optInt("id", 0).toString()
        if (exclude.contains(id)) return@mapNotNull null
        val w = obj.optInt("width"); val h = obj.optInt("height")
        if (!filters.matches(w, h)) return@mapNotNull null
        val fullUrl = obj.optString("file_url").ifBlank { return@mapNotNull null }
        val sampleUrl = obj.optString("sample_url").ifBlank { fullUrl }
        BrainrotWallpaper(
            id = id, source = sourceName,
            thumbUrl = obj.optString("preview_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl, fullUrl = fullUrl,
            resolution = "${w}x${h}",
            pageUrl = "https://$host/post/show/$id",
            tags = obj.optString("tags").split(" ").filter { it.isNotBlank() }
        )
    }
}
