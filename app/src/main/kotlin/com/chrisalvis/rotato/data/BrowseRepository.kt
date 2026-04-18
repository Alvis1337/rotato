package com.chrisalvis.rotato.data

import android.util.Log
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

    /** Fetch API key from /api/settings and merge into effective headers. */
    private suspend fun effectiveHeaders(): Map<String, String> = withContext(Dispatchers.IO) {
        if (headers.containsKey("Authorization")) return@withContext headers
        val req = Request.Builder().url("$baseUrl/api/settings").build()
        val apiKey = runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body!!.string()).optString("feedApiKey", "").ifBlank { null }
            }
        }.getOrNull()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "No API key found in /api/settings")
            headers
        } else {
            Log.d(TAG, "Auto-fetched API key from /api/settings")
            buildMap {
                put("Authorization", "Bearer $apiKey")
                putAll(headers)
            }
        }
    }

    suspend fun fetchLists(): List<RemoteList> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/lists"
        val hdrs = effectiveHeaders()
        Log.d(TAG, "fetchLists: GET $url headers=${hdrs.keys}")
        val req = Request.Builder()
            .url(url)
            .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        httpClient.newCall(req).execute().use { resp ->
            Log.d(TAG, "fetchLists: ${resp.code}")
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchLists failed: ${resp.code} ${resp.message}")
                return@withContext emptyList()
            }
            val body = resp.body!!.string()
            Log.d(TAG, "fetchLists body: ${body.take(200)}")
            val arr = JSONArray(body)
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
        val url = "$baseUrl/api/lists/$listId/wallpapers?page=$page&limit=$limit"
        val hdrs = effectiveHeaders()
        Log.d(TAG, "fetchWallpapers: GET $url headers=${hdrs.keys}")
        val req = Request.Builder()
            .url(url)
            .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        httpClient.newCall(req).execute().use { resp ->
            Log.d(TAG, "fetchWallpapers: ${resp.code}")
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchWallpapers failed: ${resp.code} ${resp.message}")
                return@withContext BrowsePage(emptyList(), page, page)
            }
            val body = resp.body!!.string()
            Log.d(TAG, "fetchWallpapers body: ${body.take(200)}")
            val json = JSONObject(body)
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

    companion object {
        private const val TAG = "BrowseRepository"
    }
}
