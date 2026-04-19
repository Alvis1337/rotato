package com.chrisalvis.rotato.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BrainrotRepository"

data class DiscoverSettings(
    val sorting: String = "relevance",
    val minResolution: String = "1920x1080",
    val aspectRatio: String = "",
    val nsfwMode: Boolean = false
)

class BrainrotRepository(
    private val baseUrl: String,
    private val headers: Map<String, String>
) {
    private val httpClient = OkHttpClient()

    private suspend fun effectiveHeaders(): Map<String, String> = withContext(Dispatchers.IO) {
        if (headers.containsKey("Authorization")) return@withContext headers
        val req = Request.Builder()
            .url("$baseUrl/api/settings")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
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
            buildMap {
                put("Authorization", "Bearer $apiKey")
                putAll(headers)
            }
        }
    }

    suspend fun fetchWallpaper(
        exclude: List<String> = emptyList()
    ): BrainrotWallpaper? = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder("$baseUrl/api/brainrot?q=anime")
            if (exclude.isNotEmpty()) {
                sb.append("&exclude=${exclude.takeLast(60).joinToString(",")}")
            }
            val hdrs = effectiveHeaders()
            val req = Request.Builder()
                .url(sb.toString())
                .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetchWallpaper HTTP ${resp.code}")
                    return@withContext null
                }
                val json = JSONObject(resp.body!!.string())
                val wp = json.optJSONObject("wallpaper") ?: return@withContext null
                BrainrotWallpaper(
                    id = wp.optString("id"),
                    source = json.optString("source"),
                    thumbUrl = wp.optString("thumbUrl"),
                    fullUrl = wp.optString("fullUrl"),
                    resolution = wp.optString("resolution"),
                    pageUrl = wp.optString("pageUrl"),
                    tags = wp.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchWallpaper failed", e)
            null
        }
    }

    suspend fun fetchLists(): List<RemoteList> = withContext(Dispatchers.IO) {
        try {
            val hdrs = effectiveHeaders()
            val req = Request.Builder()
                .url("$baseUrl/api/lists")
                .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
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
        } catch (e: Exception) {
            Log.e(TAG, "fetchLists failed", e)
            emptyList()
        }
    }

    suspend fun addToList(listId: Int, wallpaper: BrainrotWallpaper): Boolean = withContext(Dispatchers.IO) {
        try {
            val hdrs = effectiveHeaders()
            val body = JSONObject().apply {
                put("sourceId", wallpaper.id)
                put("source", wallpaper.source)
                put("thumbUrl", wallpaper.thumbUrl)
                put("fullUrl", wallpaper.fullUrl)
                put("resolution", wallpaper.resolution)
                put("pageUrl", wallpaper.pageUrl)
                put("tags", JSONArray(wallpaper.tags))
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/lists/$listId/wallpapers")
                .post(body)
                .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                Log.d(TAG, "addToList $listId: ${resp.code} body=${resp.body?.string()?.take(200)}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "addToList failed", e)
            false
        }
    }

    suspend fun fetchSettings(): DiscoverSettings = withContext(Dispatchers.IO) {
        try {
            val hdrs = effectiveHeaders()
            val req = Request.Builder()
                .url("$baseUrl/api/settings")
                .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext DiscoverSettings()
                val j = JSONObject(resp.body!!.string())
                DiscoverSettings(
                    sorting = j.optString("sorting", "relevance").ifBlank { "relevance" },
                    minResolution = j.optString("minResolution", "1920x1080").ifBlank { "1920x1080" },
                    aspectRatio = j.optString("aspectRatio", ""),
                    nsfwMode = j.optBoolean("nsfwMode", false)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSettings failed", e)
            DiscoverSettings()
        }
    }

    suspend fun updateSettings(settings: DiscoverSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val hdrs = effectiveHeaders()
            val body = JSONObject().apply {
                put("sorting", settings.sorting)
                put("minResolution", settings.minResolution)
                put("aspectRatio", settings.aspectRatio)
                put("nsfwMode", settings.nsfwMode)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/settings")
                .post(body)
                .apply { hdrs.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                Log.d(TAG, "updateSettings: ${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateSettings failed", e)
            false
        }
    }

    companion object {
        // no duplicate TAG here — top-level TAG constant is used by all functions
    }
}
