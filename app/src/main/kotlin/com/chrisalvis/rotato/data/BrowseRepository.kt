package com.chrisalvis.rotato.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class BrowseRepository(
    private val baseUrl: String,
    private val headers: Map<String, String>
) {
    private val httpClient = OkHttpClient()

    suspend fun fetchLists(): List<RemoteList> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/lists")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONArray(resp.body!!.string())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RemoteList(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    count = obj.optJSONObject("_count")?.optInt("wallpapers") ?: 0
                )
            }
        }
    }

    suspend fun fetchWallpapers(listId: Int, page: Int, limit: Int = 30): BrowsePage = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/lists/$listId/wallpapers?page=$page&limit=$limit")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext BrowsePage(emptyList(), page, page)
            val json = JSONObject(resp.body!!.string())
            val arr = json.optJSONArray("wallpapers") ?: return@withContext BrowsePage(emptyList(), page, page)
            val wallpapers = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BrowseWallpaper(
                    sourceId = obj.optString("sourceId").ifBlank { obj.optString("id") },
                    fullUrl = obj.optString("fullUrl"),
                    thumbUrl = obj.optString("thumbUrl"),
                    animeTitle = obj.optString("animeTitle")
                )
            }
            BrowsePage(
                wallpapers = wallpapers,
                page = json.optInt("page", page),
                pages = json.optInt("pages", 1)
            )
        }
    }
}
