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

private const val TAG = "ServerSettingsRepo"

class ServerSettingsRepository(private val baseUrl: String) {

    private val http = OkHttpClient()
    @Volatile private var apiKey: String? = null

    private suspend fun resolveApiKey(): String? {
        apiKey?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("$baseUrl/api/settings").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    JSONObject(resp.body!!.string()).optString("feedApiKey", "").ifBlank { null }
                }
            }.getOrNull().also { apiKey = it }
        }
    }

    private suspend fun authHeader(): Map<String, String> {
        val key = resolveApiKey() ?: return emptyMap()
        return mapOf("Authorization" to "Bearer $key")
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder =
        apply { headers.forEach { (k, v) -> addHeader(k, v) } }

    suspend fun fetchSettings(): ServerConfig = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/settings").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ServerConfig()
                val j = JSONObject(resp.body!!.string())
                apiKey = j.optString("feedApiKey", "").ifBlank { null }
                ServerConfig(
                    sorting = j.optString("sorting", "relevance").ifBlank { "relevance" },
                    minResolution = j.optString("minResolution", "1920x1080").ifBlank { "1920x1080" },
                    aspectRatio = j.optString("aspectRatio", ""),
                    nsfwMode = j.optBoolean("nsfwMode", false),
                    searchSuffix = j.optString("searchSuffix", ""),
                    feedApiKey = j.optString("feedApiKey", ""),
                    malClientId = j.optString("malClientId", ""),
                    malClientSecret = j.optString("malClientSecret", ""),
                    redirectUri = j.optString("redirectUri", "")
                )
            }
        }.getOrElse { Log.e(TAG, "fetchSettings failed", it); ServerConfig() }
    }

    suspend fun saveSettings(config: ServerConfig): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val hdrs = authHeader()
            val body = JSONObject().apply {
                put("sorting", config.sorting)
                put("minResolution", config.minResolution)
                put("aspectRatio", config.aspectRatio)
                put("nsfwMode", config.nsfwMode)
                put("searchSuffix", config.searchSuffix)
                put("malClientId", config.malClientId)
                put("malClientSecret", config.malClientSecret)
                put("redirectUri", config.redirectUri)
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/settings")
                .post(body)
                .applyHeaders(hdrs)
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrElse { Log.e(TAG, "saveSettings failed", it); false }
    }

    suspend fun fetchSources(): List<SourceRow> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/sources").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = JSONArray(resp.body!!.string())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SourceRow(
                        name = o.optString("name"),
                        enabled = o.optBoolean("enabled", false),
                        apiKey = o.optString("apiKey", ""),
                        apiUser = o.optString("apiUser", "")
                    )
                }
            }
        }.getOrElse { Log.e(TAG, "fetchSources failed", it); emptyList() }
    }

    suspend fun saveSources(sources: List<SourceRow>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val hdrs = authHeader()
            val body = JSONArray().also { arr ->
                sources.forEach { s ->
                    arr.put(JSONObject().apply {
                        put("name", s.name)
                        put("enabled", s.enabled)
                        put("apiKey", s.apiKey)
                        put("apiUser", s.apiUser)
                    })
                }
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/sources")
                .post(body)
                .applyHeaders(hdrs)
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrElse { Log.e(TAG, "saveSources failed", it); false }
    }

    suspend fun generateApiKey(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/settings/generate-api-key")
                .post("".toRequestBody())
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val key = JSONObject(resp.body!!.string()).optString("apiKey", "").ifBlank { null }
                apiKey = key
                key
            }
        }.getOrElse { Log.e(TAG, "generateApiKey failed", it); null }
    }

    suspend fun fetchFeeds(): List<ServerFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/feeds").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = JSONArray(resp.body!!.string())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ServerFeed(
                        id = o.optInt("id"),
                        slug = o.optString("slug"),
                        name = o.optString("name")
                    )
                }
            }
        }.getOrElse { Log.e(TAG, "fetchFeeds failed", it); emptyList() }
    }

    suspend fun createFeed(slug: String, name: String): ServerFeed? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("slug", slug.trim())
                put("name", name.trim())
            }.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/feeds")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body!!.string())
                ServerFeed(id = o.optInt("id"), slug = o.optString("slug"), name = o.optString("name"))
            }
        }.getOrElse { Log.e(TAG, "createFeed failed", it); null }
    }

    suspend fun deleteFeed(id: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/feeds/$id")
                .delete()
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrElse { Log.e(TAG, "deleteFeed failed", it); false }
    }

    suspend fun testSource(name: String, apiKey: String, apiUser: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("source", name)
                    put("apiKey", apiKey)
                    put("apiUser", apiUser)
                }.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$baseUrl/api/admin/test-source")
                    .post(body)
                    .build()
                http.newCall(req).execute().use { resp ->
                    val j = JSONObject(resp.body!!.string())
                    Pair(j.optBoolean("ok", false), j.optString("error", "").ifBlank { null })
                }
            }.getOrElse { Pair(false, it.message) }
        }
}
