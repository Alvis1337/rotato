package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.matches
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Engine for Zerochan's JSON API. Requires a custom User-Agent. */
object ZerochanEngine : PluginEngine() {
    override val protocol = Protocol.ZEROCHAN
    private const val PAGE_SIZE = 20
    private const val MAX_PAGE = 50
    private const val ZEROCHAN_UA = "Rotato wallpaper app - alvis"

    override suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? {
        if (!canServe(manifest, nsfw, source)) return null
        val base = baseUrl(manifest, source)
        val items = search(base, query, (1..MAX_PAGE).random())?.takeIf { it.length() > 0 }
            ?: search(base, query, 1) ?: return null
        val match = pickFiltered(items, filters, exclude) {
            itemId(it) to (it.optInt("width") to it.optInt("height"))
        } ?: return null
        return buildWallpaper(base, match, manifest)
    }

    override suspend fun fetchPage(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> {
        if (!canServe(manifest, nsfw, source)) return emptyList()
        val base = baseUrl(manifest, source)
        val items = search(base, query, (1..MAX_PAGE).random())?.takeIf { it.length() > 0 }
            ?: search(base, query, 1) ?: return emptyList()
        val candidates = (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val id = itemId(item)
            if (id.isBlank() || exclude.contains(id)) return@mapNotNull null
            val w = item.optInt("width"); val h = item.optInt("height")
            if (!filters.matches(w, h)) return@mapNotNull null
            item
        }.take(limit.coerceAtMost(PAGE_SIZE))
        return coroutineScope {
            candidates.map { item -> async { buildWallpaper(base, item, manifest) } }.awaitAll().filterNotNull()
        }
    }

    private suspend fun search(base: String, query: String, page: Int): JSONArray? = onIO {
        val normalized = query.trim().replace('_', ' ').replace('+', ' ')
        val url = if (normalized.isBlank()) "$base/?json&l=$PAGE_SIZE&p=$page"
                  else "$base/?q=${normalized.urlEncode()}&json&l=$PAGE_SIZE&p=$page"
        fetchJson(url)?.optJSONArray("items")
    }

    private suspend fun buildWallpaper(base: String, item: JSONObject, manifest: PluginManifest): BrainrotWallpaper? {
        val id = itemId(item).ifBlank { return null }
        val detail = onIO { fetchJson("$base/$id?json") } ?: return null
        val fullUrl = detail.optString("full").ifBlank { detail.optString("large") }.ifBlank { return null }
        val sampleUrl = detail.optString("large").ifBlank { detail.optString("medium") }
            .ifBlank { item.optString("thumbnail") }.ifBlank { fullUrl }
        val thumbUrl = item.optString("thumbnail").ifBlank { detail.optString("medium") }.ifBlank { sampleUrl }
        val tags = detail.optJSONArray("tags")?.toStringList()?.takeIf { it.isNotEmpty() }
            ?: item.optJSONArray("tags")?.toStringList().orEmpty()
        return BrainrotWallpaper(
            id = id, source = manifest.id.lowercase(),
            thumbUrl = thumbUrl, sampleUrl = sampleUrl, fullUrl = fullUrl,
            resolution = "${detail.optInt("width", item.optInt("width"))}x${detail.optInt("height", item.optInt("height"))}",
            pageUrl = "$base/$id",
            tags = tags
        )
    }

    private fun fetchJson(url: String): JSONObject? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", ZEROCHAN_UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else JSONObject(resp.body?.string() ?: return@use null)
        }
    }.getOrNull()

    private fun itemId(item: JSONObject): String = item.opt("id")?.toString().orEmpty()
    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}
