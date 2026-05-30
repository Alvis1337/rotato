package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import okhttp3.Request
import org.json.JSONArray

/** Engine for Danbooru-compatible APIs (Basic auth, `/posts.json?random=true`). */
object DanbooruEngine : PluginEngine() {
    override val protocol = Protocol.DANBOORU
    private val videoExts = listOf(".mp4", ".webm", ".zip")
    private val accountLevelCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private const val GOLD_LEVEL = 30

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
        val auth = authHeader(source)
        val isPremium = auth != null && accountLevel(source, auth, base) >= GOLD_LEVEL
        val tagQuery = buildTagQuery(query, nsfw, isPremium, exclude)
        val url = "$base/posts.json?tags=${tagQuery.urlEncode()}&limit=20&random=true"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { if (auth != null) addHeader("Authorization", auth) }.build()
        val arr = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@onIO null
                JSONArray(resp.body?.string() ?: return@onIO null)
            }
        }.getOrNull() ?: return@onIO null

        val post = run {
            for (i in (0 until arr.length()).shuffled()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0).toString()
                if (exclude.contains(id)) continue
                val w = obj.optInt("image_width"); val h = obj.optInt("image_height")
                if (!filters.matches(w, h)) continue
                if (obj.optString("file_url").isNotBlank()) return@run obj
            }
            null
        } ?: return@onIO null
        buildWallpaper(post, base, manifest)
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
        val auth = authHeader(source)
        val isPremium = auth != null && accountLevel(source, auth, base) >= GOLD_LEVEL
        val tagQuery = buildTagQuery(query, nsfw, isPremium, exclude)
        val url = "$base/posts.json?tags=${tagQuery.urlEncode()}&limit=$limit&random=true"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { if (auth != null) addHeader("Authorization", auth) }.build()
        val arr = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@onIO emptyList()
                JSONArray(resp.body?.string() ?: return@onIO emptyList())
            }
        }.getOrNull() ?: return@onIO emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id", 0).toString()
            if (exclude.contains(id)) return@mapNotNull null
            val w = obj.optInt("image_width"); val h = obj.optInt("image_height")
            if (!filters.matches(w, h)) return@mapNotNull null
            if (obj.optString("file_url").isBlank()) return@mapNotNull null
            buildWallpaper(obj, base, manifest)
        }
    }

    private fun authHeader(source: LocalSource): String? =
        if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "Basic ${android.util.Base64.encodeToString(
                "${source.apiUser}:${source.apiKey}".toByteArray(), android.util.Base64.NO_WRAP)}"
        else null

    private fun accountLevel(source: LocalSource, auth: String, base: String): Int {
        val cacheKey = "${source.apiUser}:${source.apiKey}@$base"
        accountLevelCache[cacheKey]?.let { return it }
        val level = runCatching {
            val req = Request.Builder()
                .url("$base/profile.json")
                .header("User-Agent", BROWSER_UA)
                .addHeader("Authorization", auth)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching 0
                org.json.JSONObject(resp.body?.string() ?: return@runCatching 0).optInt("level", 0)
            }
        }.getOrDefault(0)
        accountLevelCache[cacheKey] = level
        return level
    }

    private fun buildTagQuery(query: String, nsfw: Boolean, isPremium: Boolean, exclude: List<String>): String {
        val normalized = normalizeUserQuery(query)
        return buildString {
            val tokens = if (normalized.isNotBlank()) normalized.split(' ').filter { it.isNotBlank() } else emptyList()
            val maxTokens = if (isPremium) tokens.size else 1
            val effective = tokens.take(maxTokens).joinToString(" ")
            if (effective.isNotBlank()) { append(effective); append(' ') }
            append(if (nsfw) "rating:explicit" else "rating:general")
            if (isPremium && exclude.isNotEmpty()) {
                exclude.take(3).forEach { id -> append(" -id:$id") }
            }
        }.trim()
    }

    private fun buildWallpaper(post: org.json.JSONObject, base: String, manifest: PluginManifest): BrainrotWallpaper? {
        val largeUrl = post.optString("large_file_url")
        val sampleUrl = largeUrl.ifBlank { post.optString("file_url") }
        val fullUrl = post.optString("file_url").ifBlank { largeUrl }
        if (videoExts.any { fullUrl.endsWith(it, ignoreCase = true) }) return null
        val id = post.optInt("id", 0).toString()
        return BrainrotWallpaper(
            id = id,
            source = manifest.id.lowercase(),
            thumbUrl = post.optString("preview_file_url").ifBlank { sampleUrl },
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${post.optInt("image_width")}x${post.optInt("image_height")}",
            pageUrl = "$base/posts/$id",
            tags = (post.optString("tag_string_general") + " " + post.optString("tag_string_character"))
                .trim().split(' ').filter { it.isNotBlank() }
                .map { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
        )
    }
}
