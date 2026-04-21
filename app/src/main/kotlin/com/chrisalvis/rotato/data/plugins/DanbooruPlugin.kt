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

    override suspend fun fetch(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? = onIO {
        val normalized = normalizeBooruQuery(query)
        val tagQuery = buildString {
            if (normalized.isNotBlank()) append(normalized)
            if (!nsfw) { if (normalized.isNotBlank()) append(" "); append("rating:general") }
        }.trim()
        val url = "https://danbooru.donmai.us/posts.json?tags=${tagQuery.urlEncode()}&limit=20&random=true"
        val auth = if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "Basic ${android.util.Base64.encodeToString("${source.apiUser}:${source.apiKey}".toByteArray(), android.util.Base64.NO_WRAP)}"
        else null
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { if (auth != null) addHeader("Authorization", auth) }.build()
        val arr = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@onIO null
                JSONArray(resp.body!!.string())
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
