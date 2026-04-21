package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
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
    private const val GOLD_LEVEL = 20

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
        val normalized = normalizeBooruQuery(query)
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
        val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("image_width") to it.optInt("image_height")) } ?: return@onIO null
        val fullUrl = post.optString("file_url").ifBlank { return@onIO null }
        val id = post.optInt("id", 0).toString()
        BrainrotWallpaper(
            id = id, source = "danbooru",
            thumbUrl = post.optString("preview_file_url").ifBlank { fullUrl },
            fullUrl = fullUrl,
            resolution = "${post.optInt("image_width")}x${post.optInt("image_height")}",
            pageUrl = "https://danbooru.donmai.us/posts/$id",
            tags = (post.optString("tag_string_general") + " " + post.optString("tag_string_character"))
                .trim().split(" ").filter { it.isNotBlank() }.take(12)
        )
    }
}
