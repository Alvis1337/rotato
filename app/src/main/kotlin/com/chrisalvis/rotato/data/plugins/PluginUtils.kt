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
 * Normalises a free-text anime title into a single booru compound tag.
 * Converts spaces to underscores so a multi-word title maps to one tag.
 *   "Re:ZERO - Starting Life in Another World" → "re_zero_-_starting_life_in_another_world"
 *   "Steins;Gate" → "steinsgate"
 * Use this only for MAL-derived titles; for user search queries use [normalizeUserQuery].
 */
internal fun normalizeBooruQuery(q: String): String =
    q.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9 _-]"), "")
        .trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("-+"), "-")
        .replace(Regex("_+"), "_")
        .trim('_', '-')

/**
 * Normalises an explicit user search query for booru APIs.
 * Each space-separated token is individually cleaned (special chars stripped, lowercased)
 * and tokens are re-joined with spaces so the booru API treats them as separate AND tags.
 *   "anime 1girl" → "anime 1girl"   (two tags, ANDed)
 *   "Steins;Gate" → "steinsgate"    (one tag, special char stripped)
 *   "attack_on_titan" → "attack_on_titan"  (pre-normalised MAL titles pass through unchanged)
 */
internal fun normalizeUserQuery(q: String): String =
    q.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase()
                .replace(Regex("[^a-z0-9_-]"), "")
                .trim('_', '-')
        }
        .trim()

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
        if (filters.matches(w, h)) return obj
    }
    return null
}

/** Wraps a blocking fetch block in IO context — all plugin fetch() implementations use this. */
internal suspend fun <T> onIO(block: () -> T): T = withContext(Dispatchers.IO) { block() }
