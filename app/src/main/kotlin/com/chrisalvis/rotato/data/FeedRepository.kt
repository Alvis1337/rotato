package com.chrisalvis.rotato.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FeedSyncResult(val added: Int, val skipped: Int, val failed: Int)

class FeedRepository(private val imageDir: File) {

    suspend fun fetchFeedName(feedUrl: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = getJson("$feedUrl?page=1&limit=1", apiKey) ?: return@withContext null
            json.optJSONObject("feed")?.optString("name")
        } catch (_: Exception) { null }
    }

    suspend fun syncFeed(feed: FeedConfig): FeedSyncResult = withContext(Dispatchers.IO) {
        var added = 0; var skipped = 0; var failed = 0
        var page = 1
        var pages = 1

        while (page <= pages) {
            val json = try {
                getJson("${feed.url}?page=$page&limit=100", feed.apiKey) ?: break
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
                    val bytes = downloadBytes(fullUrl) ?: run { failed++; continue }
                    imageDir.mkdirs()
                    destFile.writeBytes(bytes)
                    added++
                } catch (_: Exception) { failed++ }
            }
            page++
        }
        FeedSyncResult(added, skipped, failed)
    }

    private fun getJson(urlString: String, apiKey: String): JSONObject? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            if (conn.responseCode != 200) return null
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadBytes(urlString: String): ByteArray? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode != 200) return null
            return conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}
