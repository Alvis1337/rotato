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

object ZerochanPlugin : SourcePlugin() {
    override val id = "ZEROCHAN"
    override val displayName = "Zerochan"
    override val description = "SFW anime wallpaper search from Zerochan's JSON API."
    override val isPremium = false
    override val needsApiKey = false
    override val needsApiUser = false
    override val apiKeyLabel = "API Key"
    override val apiUserLabel = "API User"
    override val safeContent = true

    private const val pageSize = 20
    private const val maxRandomPage = 50
    private const val zerochanUa = "Rotato wallpaper app - alvis"

    override suspend fun fetch(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? {
        val items = search(query, (1..maxRandomPage).random()) ?: return null
        val match = pickFiltered(items, filters, exclude) {
            itemId(it) to (it.optInt("width") to it.optInt("height"))
        } ?: return null
        return buildWallpaper(match)
    }

    override suspend fun fetchPage(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> {
        val items = search(query, (1..maxRandomPage).random()) ?: return emptyList()
        val candidates = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = itemId(item)
            if (id.isBlank() || exclude.contains(id)) return@mapNotNull null
            val width = item.optInt("width")
            val height = item.optInt("height")
            if (!filters.matches(width, height)) return@mapNotNull null
            item
        }.take(limit.coerceAtMost(pageSize))

        return coroutineScope {
            candidates.map { item -> async { buildWallpaper(item) } }.awaitAll().filterNotNull()
        }
    }

    private suspend fun search(query: String, page: Int): JSONArray? = onIO {
        val normalized = normalizeQuery(query)
        val url = if (normalized.isBlank()) {
            "https://www.zerochan.net/?json&l=$pageSize&p=$page"
        } else {
            "https://www.zerochan.net/?q=${normalized.urlEncode()}&json&l=$pageSize&p=$page"
        }
        fetchJson(url)?.optJSONArray("items")
    }

    private suspend fun buildWallpaper(item: JSONObject): BrainrotWallpaper? {
        val id = itemId(item).ifBlank { return null }
        val detail = fetchDetail(id) ?: return null
        val fullUrl = detail.optString("full").ifBlank { detail.optString("large") }.ifBlank { return null }
        val sampleUrl = detail.optString("large")
            .ifBlank { detail.optString("medium") }
            .ifBlank { item.optString("thumbnail") }
            .ifBlank { fullUrl }
        val thumbUrl = item.optString("thumbnail")
            .ifBlank { detail.optString("medium") }
            .ifBlank { sampleUrl }
        val tags = detail.optJSONArray("tags")?.toStringList()
            ?.takeIf { it.isNotEmpty() }
            ?: item.optJSONArray("tags")?.toStringList().orEmpty()

        return BrainrotWallpaper(
            id = id,
            source = "zerochan",
            thumbUrl = thumbUrl,
            sampleUrl = sampleUrl,
            fullUrl = fullUrl,
            resolution = "${detail.optInt("width", item.optInt("width"))}x${detail.optInt("height", item.optInt("height"))}",
            pageUrl = "https://www.zerochan.net/$id",
            tags = tags
        )
    }

    private suspend fun fetchDetail(id: String): JSONObject? = onIO {
        fetchJson("https://www.zerochan.net/$id?json")
    }

    private fun fetchJson(url: String): JSONObject? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", zerochanUa)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            JSONObject(resp.body?.string() ?: return@use null)
        }
    }.getOrNull()

    private fun normalizeQuery(query: String): String =
        query.trim().replace('_', ' ').replace('+', ' ')

    private fun itemId(item: JSONObject): String = item.opt("id")?.toString().orEmpty()

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
