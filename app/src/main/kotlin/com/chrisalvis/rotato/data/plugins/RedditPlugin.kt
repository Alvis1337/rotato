package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.DiscoverMode
import com.chrisalvis.rotato.data.LocalSource
import org.json.JSONObject

object RedditPlugin : SourcePlugin() {
    override val id = "REDDIT"
    override val displayName = "Reddit"
    override val description = "Fetch images from any public subreddit. Add multiple subreddits as separate sources."
    override val isPremium = false
    override val needsApiKey = false
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = true

    override suspend fun fetch(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = onIO {
        val subreddit = source.instanceId.trim().ifBlank { return@onIO null }
        fetchImagePosts(subreddit, nsfw, filters, exclude, limit = 100).shuffled().firstOrNull()
    }

    override suspend fun fetchPage(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> = onIO {
        val subreddit = source.instanceId.trim().ifBlank { return@onIO emptyList() }
        fetchImagePosts(subreddit, nsfw, filters, exclude, limit)
    }

    private fun fetchImagePosts(
        subreddit: String,
        nsfw: Boolean,
        filters: BrainrotFilters,
        exclude: List<String>,
        limit: Int,
    ): List<BrainrotWallpaper> {
        val sort = if (filters.discoverMode == DiscoverMode.RECENT) "new" else "top"
        val timeParam = if (sort == "top") "&t=month" else ""
        val url = "https://www.reddit.com/r/${subreddit.urlEncode()}/$sort.json?limit=100&raw_json=1$timeParam"
        val json = getJson(url) ?: return emptyList()
        val children = json.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()

        return (0 until children.length()).mapNotNull { i ->
            val post = children.optJSONObject(i)?.optJSONObject("data") ?: return@mapNotNull null
            if (!nsfw && post.optBoolean("over_18", false)) return@mapNotNull null
            if (!isImagePost(post)) return@mapNotNull null
            val id = post.optString("id").ifBlank { return@mapNotNull null }
            if (id in exclude) return@mapNotNull null
            extractWallpaper(post, subreddit)
        }.take(limit)
    }

    private fun isImagePost(post: JSONObject): Boolean {
        if (post.optBoolean("is_video", false)) return false
        val hint = post.optString("post_hint")
        if (hint == "image") return true
        val url = post.optString("url_overridden_by_dest").ifBlank { post.optString("url") }
        if (url.contains("i.redd.it")) return true
        if (url.contains("i.imgur.com")) {
            return url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".webp")
        }
        return false
    }

    private fun extractWallpaper(post: JSONObject, subreddit: String): BrainrotWallpaper? {
        val id = post.optString("id").ifBlank { return null }
        val rawUrl = post.optString("url_overridden_by_dest")
            .ifBlank { post.optString("url") }
            .ifBlank { return null }

        // Always use rawUrl (i.redd.it / i.imgur.com) as fullUrl — it's permanent.
        // preview.redd.it URLs carry time-limited signatures and would break after expiry.
        val fullUrl = rawUrl

        val previewImages = post.optJSONObject("preview")?.optJSONArray("images")
        val previewSource = previewImages?.optJSONObject(0)?.optJSONObject("source")

        val resolutions = previewImages?.optJSONObject(0)?.optJSONArray("resolutions")
        val thumbUrl = if (resolutions != null && resolutions.length() > 0) {
            resolutions.optJSONObject(resolutions.length() - 1)
                ?.optString("url")?.unescape()?.ifBlank { null } ?: fullUrl
        } else previewSource?.optString("url")?.unescape()?.ifBlank { null } ?: fullUrl

        val width = previewSource?.optInt("width") ?: 0
        val height = previewSource?.optInt("height") ?: 0
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""

        val permalink = post.optString("permalink")
        val pageUrl = if (permalink.isNotBlank()) "https://reddit.com$permalink" else "https://reddit.com/r/$subreddit"

        return BrainrotWallpaper(
            id = id,
            source = "reddit",
            thumbUrl = thumbUrl,
            sampleUrl = thumbUrl,
            fullUrl = fullUrl,
            resolution = resolution,
            pageUrl = pageUrl,
            tags = listOf(subreddit),
        )
    }

    private fun String.unescape() = replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
}
