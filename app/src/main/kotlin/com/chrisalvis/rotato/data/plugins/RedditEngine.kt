package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.MediaType
import com.chrisalvis.rotato.data.matches
import org.json.JSONObject

/** Engine for public Reddit subreddits (`/r/{sub}/top.json`). */
object RedditEngine : PluginEngine() {
    override val protocol = Protocol.REDDIT

    override suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = onIO {
        val subreddit = source.instanceId.trim().ifBlank { return@onIO null }
        fetchPosts(subreddit, nsfw, exclude, filters, limit = 100).shuffled().firstOrNull()
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
        val subreddit = source.instanceId.trim().ifBlank { return@onIO emptyList() }
        fetchPosts(subreddit, nsfw, exclude, filters, limit)
    }

    private fun fetchPosts(subreddit: String, nsfw: Boolean, exclude: List<String>, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> {
        val url = "https://www.reddit.com/r/${subreddit.urlEncode()}/top.json?limit=100&raw_json=1&t=month"
        val json = getJson(url) ?: return emptyList()
        val children = json.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return (0 until children.length()).mapNotNull { i ->
            val post = children.optJSONObject(i)?.optJSONObject("data") ?: return@mapNotNull null
            if (!nsfw && post.optBoolean("over_18", false)) return@mapNotNull null
            if (!isSupportedPost(post)) return@mapNotNull null
            val id = post.optString("id").ifBlank { return@mapNotNull null }
            if (id in exclude) return@mapNotNull null
            val previewSource = post.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("source")
            val w = previewSource?.optInt("width") ?: 0
            val h = previewSource?.optInt("height") ?: 0
            if (!filters.matches(w, h)) return@mapNotNull null
            extractWallpaper(post, subreddit)
        }.take(limit)
    }

    private fun isSupportedPost(post: JSONObject): Boolean {
        if (post.optBoolean("is_video", false)) {
            return post.optJSONObject("media")?.optJSONObject("reddit_video")
                ?.optString("fallback_url")?.isNotBlank() == true
        }
        val hint = post.optString("post_hint")
        if (hint == "image") return true
        val url = post.optString("url_overridden_by_dest").ifBlank { post.optString("url") }
        if (url.contains("i.redd.it")) return true
        if (url.contains("i.imgur.com"))
            return url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".webp")
        if (url.endsWith(".gifv", ignoreCase = true)) return true
        return MediaType.isVideoUrl(url)
    }

    /** Resolves the playable media URL for a post, converting imgur .gifv links to their .mp4 equivalent. */
    private fun resolveMediaUrl(post: JSONObject): String? {
        if (post.optBoolean("is_video", false)) {
            post.optJSONObject("media")?.optJSONObject("reddit_video")
                ?.optString("fallback_url")?.takeIf { it.isNotBlank() }?.let { return it.unescape() }
        }
        val url = post.optString("url_overridden_by_dest").ifBlank { post.optString("url") }
        if (url.isBlank()) return null
        if (url.endsWith(".gifv", ignoreCase = true)) return url.dropLast(5) + ".mp4"
        return url
    }

    private fun extractWallpaper(post: JSONObject, subreddit: String): BrainrotWallpaper? {
        val id = post.optString("id").ifBlank { return null }
        val fullUrl = resolveMediaUrl(post) ?: return null
        val isVideo = MediaType.isVideoUrl(fullUrl)
        val previewImages = post.optJSONObject("preview")?.optJSONArray("images")
        val previewSource = previewImages?.optJSONObject(0)?.optJSONObject("source")
        val resolutions = previewImages?.optJSONObject(0)?.optJSONArray("resolutions")
        val thumbUrl = if (resolutions != null && resolutions.length() > 0) {
            resolutions.optJSONObject(resolutions.length() - 1)
                ?.optString("url")?.unescape()?.ifBlank { null } ?: fullUrl
        } else previewSource?.optString("url")?.unescape()?.ifBlank { null } ?: fullUrl
        val width = previewSource?.optInt("width") ?: 0
        val height = previewSource?.optInt("height") ?: 0
        val permalink = post.optString("permalink")
        return BrainrotWallpaper(
            id = id, source = "reddit",
            thumbUrl = thumbUrl, sampleUrl = if (isVideo) fullUrl else thumbUrl, fullUrl = fullUrl,
            resolution = if (width > 0 && height > 0) "${width}x${height}" else "",
            pageUrl = if (permalink.isNotBlank()) "https://reddit.com$permalink" else "https://reddit.com/r/$subreddit",
            tags = listOf(subreddit),
            isVideo = isVideo,
            isNsfw = post.optBoolean("over_18", false)
        )
    }

    private fun String.unescape() = replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
}
