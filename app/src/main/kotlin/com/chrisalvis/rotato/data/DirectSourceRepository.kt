package com.chrisalvis.rotato.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "DirectSource"
private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun String.encode() = URLEncoder.encode(this, "UTF-8")

private fun getJson(url: String, vararg headers: Pair<String, String>): JSONObject? = try {
    val req = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
    http.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) { Log.w(TAG, "HTTP ${resp.code} for $url"); null }
        else JSONObject(resp.body!!.string())
    }
} catch (e: Exception) { Log.e(TAG, "getJson failed: $url", e); null }

private fun getJsonArray(url: String, vararg headers: Pair<String, String>): JSONArray? = try {
    val req = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
    http.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) { Log.w(TAG, "HTTP ${resp.code} for $url"); null }
        else JSONArray(resp.body!!.string())
    }
} catch (e: Exception) { Log.e(TAG, "getJsonArray failed: $url", e); null }

suspend fun fetchFromSource(
    source: LocalSource,
    query: String,
    exclude: List<String> = emptyList(),
    nsfwMode: Boolean = false,
    filters: BrainrotFilters = BrainrotFilters(),
): BrainrotWallpaper? = withContext(Dispatchers.IO) {
    when (source.type) {
        SourceType.DANBOORU   -> fetchDanbooru(source, query, exclude, nsfwMode, filters)
        SourceType.GELBOORU   -> fetchGelbooru(source, query, exclude, nsfwMode, filters)
        SourceType.SAFEBOORU  -> fetchSafebooru(query, exclude, filters)
        SourceType.WALLHAVEN  -> fetchWallhaven(source, query, exclude, nsfwMode, filters)
        SourceType.KONACHAN   -> fetchKonachan(query, exclude, nsfwMode, "konachan.com", filters)
        SourceType.YANDERE    -> fetchKonachan(query, exclude, nsfwMode, "yande.re", filters)
    }
}

// --- Danbooru ---
private fun fetchDanbooru(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? {
    val tagQuery = buildString {
        append(query.trim().replace(' ', '_'))
        if (!nsfw) append(" rating:general")
        // Do NOT append -id:X exclusion tags — free Danbooru accounts have a 2-tag limit.
        // Client-side filtering via pickFiltered handles deduplication instead.
    }.trim()
    val url = "https://danbooru.donmai.us/posts.json?tags=${tagQuery.encode()}&limit=20&random=true"
    val auth = if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
        "Basic ${ android.util.Base64.encodeToString("${source.apiUser}:${source.apiKey}".toByteArray(), android.util.Base64.NO_WRAP) }"
    else null
    val hdrs = buildList { if (auth != null) add("Authorization" to auth) }.toTypedArray()
    val arr = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
            .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONArray(resp.body!!.string())
        }
    }.getOrNull() ?: return null

    val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("image_width") to it.optInt("image_height")) } ?: return null
    val fullUrl = post.optString("file_url").ifBlank { return null }
    val id = post.optInt("id", 0).toString()
    val tags = (post.optString("tag_string_general") + " " + post.optString("tag_string_character"))
        .trim().split(" ").filter { it.isNotBlank() }.take(12)
    return BrainrotWallpaper(
        id = id,
        source = "danbooru",
        thumbUrl = post.optString("preview_file_url").ifBlank { fullUrl },
        fullUrl = fullUrl,
        resolution = "${post.optInt("image_width")}x${post.optInt("image_height")}",
        pageUrl = "https://danbooru.donmai.us/posts/$id",
        tags = tags
    )
}

// --- Gelbooru ---
private fun fetchGelbooru(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? {
    val tagQuery = buildString {
        append(query.trim().replace(' ', '_'))
        if (!nsfw) append(" rating:general")
    }.trim()
    val urlBase = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=20&tags=${tagQuery.encode()}"
    val url = if (source.apiKey.isNotBlank() && source.apiUser.isNotBlank())
        "$urlBase&api_key=${source.apiKey.encode()}&user_id=${source.apiUser.encode()}"
    else urlBase
    val json = getJson(url) ?: return null
    val arr = json.optJSONArray("post") ?: return null
    val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return null
    val id = post.optInt("id", 0).toString()
    val fullUrl = post.optString("file_url").ifBlank { return null }
    val tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
    return BrainrotWallpaper(
        id = id,
        source = "gelbooru",
        thumbUrl = post.optString("preview_url").ifBlank { fullUrl },
        fullUrl = fullUrl,
        resolution = "${post.optInt("width")}x${post.optInt("height")}",
        pageUrl = "https://gelbooru.com/index.php?page=post&s=view&id=$id",
        tags = tags
    )
}

// --- Safebooru ---
private fun fetchSafebooru(query: String, exclude: List<String>, filters: BrainrotFilters): BrainrotWallpaper? {
    val tagQuery = query.trim().replace(' ', '_')
    val url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=20&tags=${tagQuery.encode()}"
    val arr = getJsonArray(url) ?: return null
    val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return null
    val id = post.optInt("id", 0).toString()
    val directory = post.optString("directory")
    val image = post.optString("image")
    val fullUrl = "https://safebooru.org/images/$directory/$image"
    val thumbUrl = "https://safebooru.org/thumbnails/$directory/thumbnail_$image"
    val tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
    return BrainrotWallpaper(
        id = id,
        source = "safebooru",
        thumbUrl = thumbUrl,
        fullUrl = fullUrl,
        resolution = "${post.optInt("width")}x${post.optInt("height")}",
        pageUrl = "https://safebooru.org/index.php?page=post&s=view&id=$id",
        tags = tags
    )
}

// --- Wallhaven ---
private fun fetchWallhaven(source: LocalSource, query: String, exclude: List<String>, nsfw: Boolean, filters: BrainrotFilters): BrainrotWallpaper? {
    val purity = if (nsfw) "111" else "110" // sfw=1, sketchy=1, nsfw=1 or sfw+sketchy only
    val categories = "111"
    var urlBase = "https://wallhaven.cc/api/v1/search?q=${query.trim().encode()}&categories=$categories&purity=$purity&sorting=random"
    if (filters.minResolution != MinResolution.ANY) {
        urlBase += "&atleast=${filters.minResolution.width}x${filters.minResolution.height}"
    }
    if (filters.aspectRatio != AspectRatio.ANY) {
        urlBase += "&ratios=${filters.aspectRatio.wallhavenKey}"
    }
    val url = if (source.apiKey.isNotBlank()) "$urlBase&apikey=${source.apiKey.encode()}" else urlBase
    val json = getJson(url) ?: return null
    val data = json.optJSONArray("data") ?: return null
    val post = pickRandom(data, exclude) ?: return null
    val id = post.optString("id")
    val fullUrl = post.optString("path").ifBlank { return null }
    val thumbs = post.optJSONObject("thumbs")
    val thumbUrl = thumbs?.optString("small") ?: thumbs?.optString("original") ?: fullUrl
    val resolution = post.optString("resolution").ifBlank { "" }
    val tags = post.optJSONArray("tags")?.let { arr ->
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
    } ?: emptyList()
    return BrainrotWallpaper(
        id = id,
        source = "wallhaven",
        thumbUrl = thumbUrl,
        fullUrl = fullUrl,
        resolution = resolution,
        pageUrl = "https://wallhaven.cc/w/$id",
        tags = tags.take(12)
    )
}

// --- Konachan / Yande.re (same Moebooru API) ---
private fun fetchKonachan(query: String, exclude: List<String>, nsfw: Boolean, host: String, filters: BrainrotFilters): BrainrotWallpaper? {
    val tagQuery = buildString {
        val q = query.trim().replace(' ', '_')
        if (q.isNotBlank()) append("$q ")
        if (!nsfw) append("rating:safe ")
        append("order:random") // Moebooru: randomisation is a tag, not a query param
    }.trim()
    val url = "https://$host/post.json?tags=${tagQuery.encode()}&limit=20"
    val arr = getJsonArray(url) ?: return null
    val post = pickFiltered(arr, filters, exclude) { it.optInt("id", 0).toString() to (it.optInt("width") to it.optInt("height")) } ?: return null
    val id = post.optInt("id", 0).toString()
    val fullUrl = post.optString("file_url").ifBlank { return null }
    val thumbUrl = post.optString("preview_url").ifBlank { fullUrl }
    val tags = post.optString("tags").split(" ").filter { it.isNotBlank() }.take(12)
    val sourceName = if (host.contains("yande")) "yandere" else "konachan"
    return BrainrotWallpaper(
        id = id,
        source = sourceName,
        thumbUrl = thumbUrl,
        fullUrl = fullUrl,
        resolution = "${post.optInt("width")}x${post.optInt("height")}",
        pageUrl = "https://$host/post/show/$id",
        tags = tags
    )
}

private fun pickRandom(arr: JSONArray, exclude: List<String> = emptyList()): JSONObject? {
    if (arr.length() == 0) return null
    val indices = (0 until arr.length()).shuffled()
    for (i in indices) {
        val obj = arr.optJSONObject(i) ?: continue
        if (exclude.isEmpty() || !exclude.contains(obj.optString("id"))) return obj
    }
    return null
}

/**
 * Picks a random item from [arr] that satisfies [filters] and is not in [exclude].
 * [entry] extracts a (id, dimensions) pair from each item.
 */
private fun pickFiltered(
    arr: JSONArray,
    filters: BrainrotFilters,
    exclude: List<String> = emptyList(),
    entry: (JSONObject) -> Pair<String, Pair<Int, Int>>,
): JSONObject? {
    if (arr.length() == 0) return null
    val indices = (0 until arr.length()).shuffled()
    for (i in indices) {
        val obj = arr.optJSONObject(i) ?: continue
        val (id, dims) = entry(obj)
        if (exclude.contains(id)) continue
        val (w, h) = dims
        if (filters.matches(w, h)) return obj
    }
    return null
}
