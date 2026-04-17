package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FeedPreferences(private val context: Context) {

    companion object {
        private val FEEDS_JSON = stringPreferencesKey("feeds_json")
    }

    val feeds: Flow<List<FeedConfig>> = context.dataStore.data.map { prefs ->
        parseFeedsJson(prefs[FEEDS_JSON] ?: "[]")
    }

    suspend fun addFeed(url: String, headers: Map<String, String>, name: String): FeedConfig {
        val feed = FeedConfig(id = UUID.randomUUID().toString(), url = url, headers = headers, name = name)
        context.dataStore.edit { prefs ->
            val current = parseFeedsJson(prefs[FEEDS_JSON] ?: "[]").toMutableList()
            current.add(feed)
            prefs[FEEDS_JSON] = serializeFeedsJson(current)
        }
        return feed
    }

    suspend fun removeFeed(id: String) {
        context.dataStore.edit { prefs ->
            val current = parseFeedsJson(prefs[FEEDS_JSON] ?: "[]").filter { it.id != id }
            prefs[FEEDS_JSON] = serializeFeedsJson(current)
        }
    }

    suspend fun updateHeaders(id: String, headers: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val current = parseFeedsJson(prefs[FEEDS_JSON] ?: "[]").map {
                if (it.id == id) it.copy(headers = headers) else it
            }
            prefs[FEEDS_JSON] = serializeFeedsJson(current)
        }
    }

    suspend fun updateLastSync(id: String, ms: Long) {
        context.dataStore.edit { prefs ->
            val current = parseFeedsJson(prefs[FEEDS_JSON] ?: "[]").map {
                if (it.id == id) it.copy(lastSyncMs = ms) else it
            }
            prefs[FEEDS_JSON] = serializeFeedsJson(current)
        }
    }

    private fun parseFeedsJson(json: String): List<FeedConfig> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            // Migrate legacy apiKey → Authorization Bearer header
            val headers: Map<String, String> = if (o.has("headers")) {
                val ho = o.getJSONObject("headers")
                ho.keys().asSequence().associateWith { ho.getString(it) }
            } else {
                val legacy = o.optString("apiKey", "")
                if (legacy.isNotBlank()) mapOf("Authorization" to "Bearer $legacy") else emptyMap()
            }
            FeedConfig(
                id = o.getString("id"),
                url = o.getString("url"),
                headers = headers,
                name = o.optString("name", "Feed"),
                lastSyncMs = o.optLong("lastSyncMs", 0L)
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun serializeFeedsJson(feeds: List<FeedConfig>): String =
        JSONArray().also { arr ->
            feeds.forEach { f ->
                arr.put(JSONObject().apply {
                    put("id", f.id)
                    put("url", f.url)
                    put("headers", JSONObject(f.headers))
                    put("name", f.name)
                    put("lastSyncMs", f.lastSyncMs)
                })
            }
        }.toString()
}
