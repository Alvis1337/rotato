package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import okhttp3.Request
import org.json.JSONObject

object Rule34Plugin : SourcePlugin() {
    override val id = "RULE34"
    override val displayName = "Rule34"
    override val description = "Rule34 booru. Requires a free account — get your User ID and API Key from rule34.xxx account options."
    override val isPremium = false
    override val needsApiKey = true
    override val needsApiUser = true
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "User ID"
    override val safeContent = false
    override val requiresCredentials = true

    private val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov")

    override suspend fun fetch(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = onIO {
        val limit = 20
        val tagQuery = buildTagQuery(query, nsfw)
        val authSuffix = buildAuthSuffix(source)
        val maxPid = fetchMaxPid(tagQuery, limit, authSuffix)
        val pid = if (maxPid > 0) (0..maxPid).random() else 0
        val arr = getJsonArray(buildJsonUrl(tagQuery, pid, limit, authSuffix)) ?: return@onIO null
        val post = pickFiltered(arr, filters, exclude) {
            postId(it) to (it.optInt("width") to it.optInt("height"))
        } ?: return@onIO null
        post.toWallpaper()
    }

    override suspend fun fetchPage(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        val pageSize = limit.coerceIn(1, 100)
        val tagQuery = buildTagQuery(query, nsfw)
        val authSuffix = buildAuthSuffix(source)
        val maxPid = fetchMaxPid(tagQuery, pageSize, authSuffix)
        val pid = if (maxPid > 0) (0..maxPid).random() else 0
        val arr = getJsonArray(buildJsonUrl(tagQuery, pid, pageSize, authSuffix)) ?: return@onIO emptyList()
        (0 until arr.length()).mapNotNull { index ->
            val post = arr.optJSONObject(index) ?: return@mapNotNull null
            if (exclude.contains(postId(post))) return@mapNotNull null
            val width = post.optInt("width")
            val height = post.optInt("height")
            if (!filters.matches(width, height)) return@mapNotNull null
            post.toWallpaper()
        }
    }

    private fun buildAuthSuffix(source: LocalSource): String =
        if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "&user_id=${source.apiUser.urlEncode()}&api_key=${source.apiKey.urlEncode()}"
        else ""

    private fun buildTagQuery(query: String, nsfw: Boolean): String = buildString {
        val normalized = normalizeUserQuery(query)
        if (normalized.isNotBlank()) append(normalized)
        if (isNotBlank()) append(' ')
        append(if (nsfw) "rating:explicit" else "rating:safe")
    }.trim()

    private fun buildJsonUrl(tagQuery: String, pid: Int, limit: Int, authSuffix: String): String =
        "https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=$limit&pid=$pid&tags=${tagQuery.urlEncode()}$authSuffix"

    private fun fetchMaxPid(tagQuery: String, limit: Int, authSuffix: String): Int {
        val req = Request.Builder()
            .url("https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&limit=1&tags=${tagQuery.urlEncode()}$authSuffix")
            .header("User-Agent", BROWSER_UA)
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use 0
                val body = resp.body?.string().orEmpty()
                val count = Regex("count=\"(\\d+)\"").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                ((count - 1) / limit).coerceIn(0, 100)
            }
        }.getOrDefault(0)
    }

    private fun postId(post: JSONObject): String = post.opt("id")?.toString().orEmpty()

    private fun JSONObject.toWallpaper(): BrainrotWallpaper? {
        val id = postId(this).ifBlank { return null }
        val fullUrl = optString("file_url").ifBlank { return null }
        if (videoExtensions.any { fullUrl.endsWith(it, ignoreCase = true) }) return null
        val sampleUrl = optString("sample_url")
            .ifBlank { fullUrl }
            .let { candidate ->
                if (videoExtensions.any { ext -> candidate.endsWith(ext, ignoreCase = true) }) fullUrl else candidate
            }
        return BrainrotWallpaper(
            id = id,
            source = "rule34",
            thumbUrl = optString("preview_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${optInt("width")}x${optInt("height")}",
            pageUrl = "https://rule34.xxx/index.php?page=post&s=view&id=$id",
            tags = optString("tags").split(' ').filter { it.isNotBlank() }
        )
    }
}
