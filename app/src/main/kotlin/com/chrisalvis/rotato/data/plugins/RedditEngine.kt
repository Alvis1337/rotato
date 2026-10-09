package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.BuildConfig
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.MediaType
import com.chrisalvis.rotato.data.matches
import okhttp3.Request
import org.json.JSONObject

/** Engine for public Reddit subreddits (`/r/{sub}/top.json`). */
object RedditEngine : PluginEngine() {
    override val protocol = Protocol.REDDIT
    /** Reddit asks API clients for a descriptive user agent. */
    private val REDDIT_UA = "android:com.chrisalvis.rotato:v${BuildConfig.VERSION_NAME} (wallpaper app)"

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
        val json = listing(subreddit)
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
            val video = post.optBoolean("is_video", false) ||
                MediaType.isVideoUrl(post.optString("url_overridden_by_dest").ifBlank { post.optString("url") })
            if (!filters.matches(w, h, video)) return@mapNotNull null
            extractWallpaper(post, subreddit)
        }.take(limit)
    }

    /**
     * The subreddit's top posts this month. With a client ID Rotato signs in as an app (no user
     * account) and uses oauth.reddit.com; anonymous `.json` is refused (HTTP 403/429) from many
     * networks now. Refusals throw, so Source Health says why Reddit is empty.
     */
    private fun listing(subreddit: String): JSONObject {
        val path = "/r/${subreddit.urlEncode()}/top?limit=100&raw_json=1&t=month"
        val token = accessToken()
        val req = Request.Builder()
            .url(if (token != null) "https://oauth.reddit.com$path" else "https://www.reddit.com${path.replace("/top?", "/top.json?")}")
            .header("User-Agent", REDDIT_UA)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401 && token != null) cachedToken = null
            if (resp.code == 403 || resp.code == 429) {
                throw IllegalStateException(
                    if (token == null) "Reddit refused anonymous access (HTTP ${resp.code}). Builds with a Reddit client ID avoid this."
                    else "Reddit refused the request (HTTP ${resp.code})"
                )
            }
            if (!resp.isSuccessful) throw IllegalStateException("Reddit returned HTTP ${resp.code}")
            return JSONObject(resp.body?.string() ?: throw IllegalStateException("Empty Reddit response"))
        }
    }

    @Volatile private var cachedToken: Pair<String, Long>? = null
    private val deviceId = java.util.UUID.randomUUID().toString()

    /** App-only OAuth token (installed_client grant), cached until shortly before it expires. */
    private fun accessToken(): String? {
        val clientId = BuildConfig.REDDIT_CLIENT_ID.ifBlank { return null }
        cachedToken?.let { (t, expires) -> if (System.currentTimeMillis() < expires) return t }
        val body = okhttp3.FormBody.Builder()
            .add("grant_type", "https://oauth.reddit.com/grants/installed_client")
            .add("device_id", deviceId)
            .build()
        val req = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("User-Agent", REDDIT_UA)
            .header("Authorization", okhttp3.Credentials.basic(clientId, ""))
            .post(body)
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body?.string() ?: return@use null)
                val token = o.optString("access_token").ifBlank { return@use null }
                cachedToken = token to System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 120) * 1000
                token
            }
        }.getOrNull()
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
