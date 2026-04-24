package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import okhttp3.Request
import org.json.JSONArray

object DanbooruPlugin : SourcePlugin() {
    override val id = "DANBOORU"
    override val displayName = "Danbooru"
    override val description = "Large anime art booru. Free accounts limited to 2 search tags."
    override val isPremium = true
    override val needsApiKey = true
    override val needsApiUser = true
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "Username"
    override val safeContent = false

    /** Cache of account level per username to avoid repeated /profile.json calls. */
    private val accountLevelCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Danbooru account level threshold for Gold (can use more tags server-side). */
    private const val GOLD_LEVEL = 30

    private fun authHeader(source: LocalSource): String? =
        if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "Basic ${android.util.Base64.encodeToString("${source.apiUser}:${source.apiKey}".toByteArray(), android.util.Base64.NO_WRAP)}"
        else null

    private fun accountLevel(source: LocalSource, auth: String): Int {
        val cached = accountLevelCache[source.apiUser]
        if (cached != null) return cached
        val level = runCatching {
            val req = Request.Builder()
                .url("https://danbooru.donmai.us/profile.json")
                .header("User-Agent", BROWSER_UA)
                .addHeader("Authorization", auth)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching 0
                org.json.JSONObject(resp.body?.string() ?: return@runCatching 0).optInt("level", 0)
            }
        }.getOrDefault(0)
        accountLevelCache[source.apiUser] = level
        return level
    }

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val normalized = normalizeUserQuery(query)
        val auth = authHeader(source)

        // Determine if user has Gold/Platinum (allows more than 2 search tags)
        val isPremiumAccount = auth != null && accountLevel(source, auth) >= GOLD_LEVEL

        val tagQuery = buildString {
            if (normalized.isNotBlank()) append(normalized)
            if (!nsfw) { if (normalized.isNotBlank()) append(" "); append("rating:general") }
            // Premium accounts can use server-side ID exclusion for better dedup
            if (isPremiumAccount && exclude.isNotEmpty()) {
                exclude.take(3).forEach { id -> append(" -id:$id") }
            }
        }.trim()

        val url = "https://danbooru.donmai.us/posts.json?tags=${tagQuery.urlEncode()}&limit=20&random=true"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { if (auth != null) addHeader("Authorization", auth) }.build()
        val arr = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@onIO null
                JSONArray(resp.body?.string() ?: return@onIO null)
            }
        }.getOrNull() ?: return@onIO null
        // Iterate shuffled posts to find one that passes filters and has an accessible file_url
        // (some Danbooru posts are gold-only or deleted and have a blank file_url)
        val post = run {
            val indices = (0 until arr.length()).shuffled()
            for (i in indices) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0).toString()
                if (exclude.contains(id)) continue
                val w = obj.optInt("image_width"); val h = obj.optInt("image_height")
                if (!filters.matches(w, h)) continue
                if (obj.optString("file_url").isNotBlank()) return@run obj
            }
            null
        } ?: return@onIO null
        val fullUrl = post.optString("file_url")
        val sampleUrl = post.optString("large_file_url").ifBlank { fullUrl }
        // Skip video posts (ugoira/mp4/webm) — Coil can't render them
        val videoExts = listOf(".mp4", ".webm", ".zip")
        if (videoExts.any { fullUrl.endsWith(it, ignoreCase = true) }) return@onIO null
        val id = post.optInt("id", 0).toString()
        BrainrotWallpaper(
            id = id, source = "danbooru",
            thumbUrl = post.optString("preview_file_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${post.optInt("image_width")}x${post.optInt("image_height")}",
            pageUrl = "https://danbooru.donmai.us/posts/$id",
            tags = (post.optString("tag_string_general") + " " + post.optString("tag_string_character"))
                .trim().split(" ").filter { it.isNotBlank() }
        )
    }

    override suspend fun fetchPage(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters, limit: Int): List<BrainrotWallpaper> = onIO {
        val normalized = normalizeUserQuery(query)
        val auth = authHeader(source)
        val isPremiumAccount = auth != null && accountLevel(source, auth) >= GOLD_LEVEL
        val tagQuery = buildString {
            if (normalized.isNotBlank()) append(normalized)
            if (!nsfw) { if (normalized.isNotBlank()) append(" "); append("rating:general") }
            if (isPremiumAccount && exclude.isNotEmpty()) {
                exclude.take(3).forEach { id -> append(" -id:$id") }
            }
        }.trim()
        val url = "https://danbooru.donmai.us/posts.json?tags=${tagQuery.urlEncode()}&limit=$limit&random=true"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { if (auth != null) addHeader("Authorization", auth) }.build()
        val arr = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@onIO emptyList()
                JSONArray(resp.body?.string() ?: return@onIO emptyList())
            }
        }.getOrNull() ?: return@onIO emptyList()
        val videoExts = listOf(".mp4", ".webm", ".zip")
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", 0).toString()
            if (exclude.contains(id)) return@mapNotNull null
            val w = obj.optInt("image_width"); val h = obj.optInt("image_height")
            if (!filters.matches(w, h)) return@mapNotNull null
            val fullUrl = obj.optString("file_url").ifBlank { return@mapNotNull null }
            if (videoExts.any { fullUrl.endsWith(it, ignoreCase = true) }) return@mapNotNull null
            val sampleUrl = obj.optString("large_file_url").ifBlank { fullUrl }
            BrainrotWallpaper(
                id = id, source = "danbooru",
                thumbUrl = obj.optString("preview_file_url").ifBlank { sampleUrl },
                sampleUrl = sampleUrl, fullUrl = fullUrl,
                resolution = "${w}x${h}",
                pageUrl = "https://danbooru.donmai.us/posts/$id",
                tags = (obj.optString("tag_string_general") + " " + obj.optString("tag_string_character"))
                    .trim().split(" ").filter { it.isNotBlank() }
            )
        }
    }
}
