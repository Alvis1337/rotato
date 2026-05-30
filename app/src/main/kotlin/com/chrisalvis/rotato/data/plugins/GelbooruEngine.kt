package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Engine for Gelbooru-compatible booru APIs.
 *
 * Supports three API variants via [PluginManifest.extras]:
 *
 * `response` extra ("object" | "array"):
 * - "object" (default): response is `{"@attributes": {"count": N}, "post": [...]}` — Gelbooru
 * - "array": response is a bare JSON array — Rule34, Safebooru
 *
 * `count` extra ("json" | "xml" | "random"):
 * - "json" (default): read `@attributes.count` from a pid=0 prefetch, then pick random page
 * - "xml": separate XML endpoint returns count (Rule34)
 * - "random": pick a random pid in [0, extras["countMax"]?.toInt() ?: 200] — Safebooru
 *
 * `imageUrl` extra ("file_url" | "safebooru"):
 * - "file_url" (default): use `file_url` field directly
 * - "safebooru": construct `{base}/images/{directory}/{image}` — Safebooru
 *
 * `ratingTag` extra ("general" | "safe"):
 * - "general" (default): SFW→`rating:general`, NSFW→`rating:explicit`
 * - "safe": SFW→`rating:safe`, NSFW→`rating:explicit`
 *
 * `pageUrlTemplate` extra (optional): overrides post page URL, uses `{base}` and `{id}` placeholders.
 * If absent, defaults to `{base}/index.php?page=post&s=view&id={id}`.
 */
object GelbooruEngine : PluginEngine() {
    override val protocol = Protocol.GELBOORU
    private val videoExts = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov")

    override suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = onIO {
        if (!canServe(manifest, nsfw, source)) return@onIO null
        val limit = 20
        val base = baseUrl(manifest, source)
        val apiBase = "$base/index.php?page=dapi&s=post&q=index&json=1"
        val extras = manifest.extras
        val tagQuery = buildTagQuery(query, nsfw, extras)
        val authSuffix = buildAuthSuffix(source)
        val url = "$apiBase&limit=$limit&tags=${tagQuery.urlEncode()}$authSuffix"

        val pid = resolvePid(url, base, tagQuery, authSuffix, limit, extras)
        val arr = loadArray(url, pid, extras["response"] ?: "object") ?: return@onIO null

        val post = pickFiltered(arr, filters, exclude) { obj ->
            postId(obj) to (obj.optInt("width") to obj.optInt("height"))
        } ?: return@onIO null
        buildWallpaper(post, base, manifest, extras)
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
        val apiBase = "$base/index.php?page=dapi&s=post&q=index&json=1"
        val extras = manifest.extras
        val tagQuery = buildTagQuery(query, nsfw, extras)
        val authSuffix = buildAuthSuffix(source)
        val url = "$apiBase&limit=$limit&tags=${tagQuery.urlEncode()}$authSuffix"

        val pid = resolvePid(url, base, tagQuery, authSuffix, limit, extras)
        val arr = loadArray(url, pid, extras["response"] ?: "object") ?: return@onIO emptyList()

        (0 until arr.length()).mapNotNull { i ->
            val post = arr.optJSONObject(i) ?: return@mapNotNull null
            if (exclude.contains(postId(post))) return@mapNotNull null
            val w = post.optInt("width"); val h = post.optInt("height")
            if (!filters.matches(w, h)) return@mapNotNull null
            buildWallpaper(post, base, manifest, extras)
        }
    }

    private fun buildTagQuery(query: String, nsfw: Boolean, extras: Map<String, String>): String {
        val normalized = normalizeUserQuery(query)
        val safeTag = if (nsfw) "rating:explicit"
                      else if ((extras["ratingTag"] ?: "general") == "safe") "rating:safe"
                      else "rating:general"
        return buildString {
            if (normalized.isNotBlank()) { append(normalized); append(' ') }
            append(safeTag)
        }.trim()
    }

    private fun buildAuthSuffix(source: LocalSource): String =
        if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
            "&api_key=${source.apiKey.urlEncode()}&user_id=${source.apiUser.urlEncode()}"
        else ""

    /** Determines the pid to fetch based on the `count` strategy in extras. */
    private fun resolvePid(
        apiBaseWithTags: String,
        host: String,
        tagQuery: String,
        authSuffix: String,
        limit: Int,
        extras: Map<String, String>,
    ): Int = when (extras["count"] ?: "json") {
        "xml" -> {
            val countUrl = "$host/index.php?page=dapi&s=post&q=index&limit=1&tags=${tagQuery.urlEncode()}$authSuffix"
            val count = runCatching {
                val req = Request.Builder().url(countUrl).header("User-Agent", BROWSER_UA).build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching 0
                    val body = resp.body?.string().orEmpty()
                    Regex("count=\"(\\d+)\"").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                }
            }.getOrDefault(0)
            if (count > 0) (0..((count - 1) / limit).coerceIn(0, 100)).random() else 0
        }
        "random" -> {
            val max = extras["countMax"]?.toIntOrNull() ?: 200
            (0..max).random()
        }
        else -> { // "json"
            val page0Json = getJson("$apiBaseWithTags&pid=0") ?: return 0
            val count = page0Json.optJSONObject("@attributes")?.optInt("count", 0) ?: 0
            if (count > 0) (0..((count - 1) / limit).coerceIn(0, 100)).random() else 0
        }
    }

    /** Fetches the post array at the given pid. Returns null on failure. */
    private fun loadArray(apiUrl: String, pid: Int, responseFormat: String): JSONArray? {
        val resp = if (pid == 0 && responseFormat == "object") {
            getJson("$apiUrl&pid=0")
        } else {
            when (responseFormat) {
                "object" -> getJson("$apiUrl&pid=$pid")
                else -> null  // will use array path below
            }
        }
        return when (responseFormat) {
            "object" -> (if (pid == 0) resp else getJson("$apiUrl&pid=$pid"))
                ?.optJSONArray("post")
            else -> getJsonArray("$apiUrl&pid=$pid")
        }
    }

    private fun postId(obj: JSONObject): String = obj.opt("id")?.toString().orEmpty()

    private fun buildWallpaper(
        post: JSONObject,
        base: String,
        manifest: PluginManifest,
        extras: Map<String, String>,
    ): BrainrotWallpaper? {
        val id = postId(post).ifBlank { return null }
        val fullUrl = resolveImageUrl(post, base, extras)?.ifBlank { return null } ?: return null
        if (videoExts.any { fullUrl.endsWith(it, ignoreCase = true) }) return null
        val sampleUrl = post.optString("sample_url").ifBlank { fullUrl }
            .let { if (videoExts.any { ext -> it.endsWith(ext, ignoreCase = true) }) fullUrl else it }
        val thumbUrl = post.optString("preview_url").ifBlank { sampleUrl }
        val pageUrlTemplate = extras["pageUrlTemplate"] ?: "{base}/index.php?page=post&s=view&id={id}"
        val pageUrl = pageUrlTemplate.replace("{base}", base).replace("{id}", id)
        return BrainrotWallpaper(
            id = id,
            source = manifest.id.lowercase(),
            thumbUrl = thumbUrl,
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${post.optInt("width")}x${post.optInt("height")}",
            pageUrl = pageUrl,
            tags = post.optString("tags").split(' ').filter { it.isNotBlank() }
        )
    }

    private fun resolveImageUrl(post: JSONObject, base: String, extras: Map<String, String>): String? =
        when (extras["imageUrl"] ?: "file_url") {
            "safebooru" -> {
                val dir = post.optString("directory").ifBlank { return null }
                val img = post.optString("image").ifBlank { return null }
                "$base/images/$dir/$img"
            }
            else -> post.optString("file_url").ifBlank { null }
        }
}
