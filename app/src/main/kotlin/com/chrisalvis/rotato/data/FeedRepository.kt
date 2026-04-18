package com.chrisalvis.rotato.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

private const val TAG = "FeedRepository"

data class FeedSyncResult(val added: Int, val skipped: Int, val failed: Int)

class FeedRepository(private val imageDir: File) {

    private val httpClient = OkHttpClient()

    suspend fun fetchFeedName(feedUrl: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val json = getJson(feedUrl.appendQuery("page=1&limit=1"), headers) ?: return@withContext null
            json.optJSONObject("feed")?.optString("name")
        } catch (_: Exception) { null }
    }

    suspend fun fetchApiKey(baseUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = getJson("$baseUrl/api/settings", emptyMap()) ?: return@withContext null
            json.optString("feedApiKey", "").ifBlank { null }
        } catch (_: Exception) { null }
    }

    suspend fun syncFeed(feed: FeedConfig): FeedSyncResult = withContext(Dispatchers.IO) {
        var added = 0; var skipped = 0; var failed = 0
        var page = 1
        var pages = 1

        while (page <= pages) {
            val json = try {
                getJson(feed.url.appendQuery("page=$page&limit=100"), feed.headers) ?: break
            } catch (_: Exception) { break }

            pages = json.optInt("pages", 1).coerceAtLeast(1)
            val wallpapers = json.optJSONArray("wallpapers") ?: break

            for (i in 0 until wallpapers.length()) {
                val wp = wallpapers.getJSONObject(i)
                val sourceId = wp.optString("id").ifBlank { continue }
                val fullUrl = wp.optString("fullUrl").ifBlank { continue }

                val ext = fullUrl.substringAfterLast('.').substringBefore('?').take(5).ifBlank { "jpg" }
                val destFile = File(imageDir, "${sanitize(sourceId)}.$ext")

                if (destFile.exists()) { skipped++; continue }

                try {
                    val bytes = downloadBytes(fullUrl) ?: run {
                        Log.w(TAG, "download returned null (non-200): $fullUrl")
                        failed++
                        continue
                    }
                    imageDir.mkdirs()
                    destFile.writeBytes(bytes)
                    added++
                } catch (e: Exception) {
                    Log.e(TAG, "download exception for $fullUrl", e)
                    failed++
                }
            }
            page++
        }
        FeedSyncResult(added, skipped, failed)
    }

    private fun getJson(urlString: String, headers: Map<String, String>): JSONObject? {
        val req = Request.Builder()
            .url(urlString)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body!!.string())
        }
    }

    private fun downloadBytes(urlString: String): ByteArray? {
        val host = urlString.substringAfter("://").substringBefore("/")
        // Danbooru's CDN blocks browser UA on /original/ paths — send no custom headers
        // and let OkHttp use its default UA (okhttp/x.x.x), which passes through.
        val req = if (host == "cdn.donmai.us") {
            Request.Builder().url(urlString).build()
        } else {
            Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://$host/")
                .build()
        }
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "download HTTP ${resp.code} for $urlString")
                return null
            }
            return resp.body!!.bytes()
        }
    }

    suspend fun downloadWallpaper(sourceId: String, fullUrl: String): Boolean = withContext(Dispatchers.IO) {
        val ext = fullUrl.substringAfterLast('.').substringBefore('?').take(5).ifBlank { "jpg" }
        val destFile = File(imageDir, "${sanitize(sourceId)}.$ext")
        if (destFile.exists()) return@withContext true
        return@withContext try {
            val bytes = downloadBytes(fullUrl) ?: return@withContext false
            imageDir.mkdirs()
            destFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadWallpaper failed for $fullUrl", e)
            false
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}

private fun String.appendQuery(params: String): String =
    if (contains('?')) "$this&$params" else "$this?$params"
