package com.chrisalvis.rotato.data

import android.util.Log
import com.chrisalvis.rotato.data.plugins.normalizeBooruQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** A booru tag and how many posts carry it. */
data class TagMatch(val name: String, val postCount: Int) {
    /** "my_hero_academia" → "My Hero Academia". */
    val label: String get() = name.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

/**
 * Turns a MAL show into the tags image sites actually use. Booru sites tag a series once
 * (no "3rd Season", no subtitles), often under a name that differs from MAL's, so the English
 * title, MAL's title and its synonyms are cleaned up and looked up as series tags (aliases
 * included) on Danbooru, whose tag names the other boorus largely share.
 */
object AnimeTagResolver {
    private const val TAG = "AnimeTagResolver"
    private const val CATEGORY_COPYRIGHT = 3
    private const val CATEGORY_CHARACTER = 4

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val SEASON_SUFFIX = Regex(
        "(?i)(?:\\s+|\\s*[:\\-–]\\s*)(the\\s+)?(\\d+(st|nd|rd|th)\\s+season|season\\s*\\d+|s\\d+|part\\s*\\d+|cour\\s*\\d+|" +
            "final\\s+season|the\\s+final|the\\s+movie|movie|film|ova|specials?|recap|ii+|iv|\\d+)\\s*$"
    )

    /** Title variants worth looking up, most specific first, without season/part suffixes. */
    fun candidateQueries(entry: MalAnimeEntry): List<String> {
        val titles = (listOf(entry.englishTitle, entry.title) + entry.synonyms).filter { it.isNotBlank() }
        val out = LinkedHashSet<String>()
        for (t in titles) {
            var base = t
            repeat(3) { base = base.replace(SEASON_SUFFIX, "") }
            val beforeColon = base.substringBefore(':').trim()
            listOf(base, beforeColon).forEach { v ->
                normalizeBooruQuery(v.replace(Regex("[\"'’!?.,]"), "")).takeIf { it.length >= 2 }?.let(out::add)
            }
        }
        // The titles as they are, in case a suffix was really part of the name ("Mob Psycho 100").
        titles.forEach { t -> normalizeBooruQuery(t).takeIf { it.length >= 2 }?.let(out::add) }
        return out.toList()
    }

    /**
     * Series tags that match [entry], best first (most posts). Empty when nothing was found or
     * the lookup failed; callers then fall back to the cleaned title.
     */
    suspend fun seriesTags(entry: MalAnimeEntry): List<TagMatch> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, TagMatch>()
        for (q in candidateQueries(entry).take(6)) {
            // Exact name or alias first, then a prefix match ("one_piece" → "one_piece_(anime)").
            for (pattern in listOf(q, "$q*")) {
                lookup(pattern, CATEGORY_COPYRIGHT).forEach { m -> found.putIfAbsent(m.name, m) }
            }
            if (found.values.any { it.postCount >= 200 }) break
        }
        found.values.filter { it.postCount > 0 }.sortedByDescending { it.postCount }.take(5)
    }

    /** The most-drawn characters of a series tag. */
    suspend fun characters(seriesTag: String, limit: Int = 16): List<TagMatch> = withContext(Dispatchers.IO) {
        val url = "https://danbooru.donmai.us/related_tag.json?query=${enc(seriesTag)}&category=$CATEGORY_CHARACTER&limit=$limit"
        val body = get(url) ?: return@withContext emptyList()
        runCatching {
            val json = JSONObject(body)
            val related = json.optJSONArray("related_tags")
            if (related != null) {
                (0 until related.length()).mapNotNull { i ->
                    val tag = related.optJSONObject(i)?.optJSONObject("tag") ?: return@mapNotNull null
                    if (tag.optInt("category", CATEGORY_CHARACTER) != CATEGORY_CHARACTER) return@mapNotNull null
                    TagMatch(tag.optString("name"), tag.optInt("post_count"))
                }
            } else {
                // Older response shape: "tags": [["name", count], ...]
                val tags = json.optJSONArray("tags") ?: JSONArray()
                (0 until tags.length()).mapNotNull { i ->
                    val pair = tags.optJSONArray(i) ?: return@mapNotNull null
                    TagMatch(pair.optString(0), pair.optInt(1))
                }
            }
        }.getOrDefault(emptyList())
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
            .take(limit)
    }

    /** Tag autocomplete for a typed name (any category), used for "add a character". */
    suspend fun search(text: String, category: Int? = CATEGORY_CHARACTER): List<TagMatch> = withContext(Dispatchers.IO) {
        val q = normalizeBooruQuery(text)
        if (q.length < 2) return@withContext emptyList()
        lookup("$q*", category, limit = 8)
    }

    private fun lookup(pattern: String, category: Int?, limit: Int = 5): List<TagMatch> {
        val cat = category?.let { "&search[category]=$it" }.orEmpty()
        val url = "https://danbooru.donmai.us/tags.json?search[name_or_alias_matches]=${enc(pattern)}$cat" +
            "&search[hide_empty]=true&search[order]=count&limit=$limit"
        val body = get(url) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                TagMatch(o.optString("name"), o.optInt("post_count")).takeIf { it.name.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun get(url: String): String? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", "Rotato/1.0").build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "lookup failed: ${e.message}")
        null
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
}
