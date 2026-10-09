package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.matches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

internal val http = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

internal fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")

internal fun getJson(url: String, vararg headers: Pair<String, String>): JSONObject? = try {
    val req = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
    http.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) null
        else JSONObject(resp.body?.string() ?: return@use null)
    }
} catch (e: Exception) { null }

internal fun getJsonArray(url: String, vararg headers: Pair<String, String>): JSONArray? = try {
    val req = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
    http.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) null
        else JSONArray(resp.body?.string() ?: return@use null)
    }
} catch (e: Exception) { null }

/**
 * Normalises a free-text anime title into a single booru compound tag: lowercase, spaces become
 * underscores. Punctuation is kept because it is part of real tags:
 *   "Fate/Zero" → "fate/zero", "Steins;Gate" → "steins;gate", "shaula_(re:zero)" stays as is.
 * A leading "-" or "~" is dropped so a title can't turn into an exclude or OR operator.
 * Use this only for MAL-derived titles; for user search queries use [normalizeUserQuery].
 */
internal fun normalizeBooruQuery(q: String): String =
    q.trim()
        .lowercase()
        .filterNot { it.isISOControl() }
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .trimStart('-', '~')

/**
 * Normalises an explicit user search query for booru APIs. Tokens stay space-separated (ANDed
 * tags); each is lowercased with control characters removed and trailing underscores trimmed.
 * Everything else is kept: "-tag" excludes, "~tag" ORs, "*" wildcards, and punctuation in real
 * tags such as "fate/grand_order", "k-on!" or "jojo's_bizarre_adventure". URL encoding
 * happens later.
 */
internal fun normalizeUserQuery(q: String): String =
    q.trim()
        .split(Regex("\\s+"))
        .map { token -> token.lowercase().filterNot { it.isISOControl() }.trimEnd('_') }
        .filter { it.isNotBlank() && it != "-" && it != "~" }
        .joinToString(" ")

internal fun pickRandom(arr: JSONArray, exclude: List<String> = emptyList()): JSONObject? {
    if (arr.length() == 0) return null
    val indices = (0 until arr.length()).shuffled()
    for (i in indices) {
        val obj = arr.optJSONObject(i) ?: continue
        if (exclude.isEmpty() || !exclude.contains(obj.optString("id"))) return obj
    }
    return null
}

internal fun pickFiltered(
    arr: JSONArray,
    filters: com.chrisalvis.rotato.data.BrainrotFilters,
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
        if (filters.matches(w, h, com.chrisalvis.rotato.data.MediaType.isVideoUrl(obj.optString("file_url")))) return obj
    }
    return null
}

/** Wraps a blocking fetch block in IO context — all plugin fetch() implementations use this. */
internal suspend fun <T> onIO(block: () -> T): T = withContext(Dispatchers.IO) { block() }
