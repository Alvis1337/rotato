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
    override val requiresCredentials = true

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val normalized = normalizeBooruQuery(query)
        val tagQuery = buildString {
            if (normalized.isNotBlank()) append(normalized)
            if (!nsfw) { if (normalized.isNotBlank()) append(" "); append("rating:general") }
        }.trim()
        val authSuffix = if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "&api_key=${source.apiKey.urlEncode()}&user_id=${source.apiUser.urlEncode()}"
        else ""
        val baseUrl = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=20&tags=${tagQuery.urlEncode()}$authSuffix"

        // Fetch page 0 first to discover total count, then pick a random valid page.
        // Without this, a random pid can exceed the result count and Gelbooru returns "Too deep!".
        val page0 = getJson("$baseUrl&pid=0") ?: return@onIO null
        val count = page0.optJSONObject("@attributes")?.optInt("count", 0) ?: 0
        val maxPid = ((count - 1) / 20).coerceIn(0, 100)

        val arr = if (maxPid > 0) {
            val randomPid = (1..maxPid).random()
            getJson("$baseUrl&pid=$randomPid")?.optJSONArray("post")
        } else {
            page0.optJSONArray("post")
        } ?: return@onIO null

        val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return@onIO null
        val id = post.optInt("id", 0).toString()
        val fullUrl = post.optString("file_url").ifBlank { return@onIO null }
        // Skip video posts — Coil can't render mp4/webm and shows blank cards
        val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov")
        if (videoExtensions.any { fullUrl.endsWith(it, ignoreCase = true) }) return@onIO null
        val sampleUrl = post.optString("sample_url").ifBlank { fullUrl }
            .let { if (videoExtensions.any { ext -> it.endsWith(ext, ignoreCase = true) }) fullUrl else it }
        BrainrotWallpaper(
            id = id, source = "gelbooru",
            thumbUrl = post.optString("preview_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${post.optInt("width")}x${post.optInt("height")}",
            pageUrl = "https://gelbooru.com/index.php?page=post&s=view&id=$id",
            tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
        )
    }
}
