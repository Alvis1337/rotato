package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class LocalSourcesPreferences(private val context: Context) {

    companion object {
        private val SOURCES_KEY = stringPreferencesKey("local_sources_json")

        private fun defaultSources() = SourceType.entries.map {
            // Enable safe sources that don't require a user account on first install
            LocalSource(type = it, enabled = it.safeContent && !it.needsApiUser)
        }
    }

    val sources: Flow<List<LocalSource>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() } }

    suspend fun update(type: SourceType, enabled: Boolean? = null, apiKey: String? = null, apiUser: String? = null, tags: String? = null) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }.toMutableList()
            val idx = current.indexOfFirst { it.type == type }
            if (idx == -1) return@edit
            val existing = current[idx]
            current[idx] = existing.copy(
                enabled = enabled ?: existing.enabled,
                apiKey = if (apiKey != null) apiKey else existing.apiKey,
                apiUser = if (apiUser != null) apiUser else existing.apiUser,
                tags = if (tags != null) tags else existing.tags
            )
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    private fun parse(json: String): List<LocalSource> = try {
        val arr = JSONArray(json)
        val result = mutableListOf<LocalSource>()
        (0 until arr.length()).forEach { i ->
            val o = arr.getJSONObject(i)
            val type = runCatching { SourceType.valueOf(o.getString("type")) }.getOrNull() ?: return@forEach
            result.add(LocalSource(
                type = type,
                enabled = o.optBoolean("enabled", false),
                apiKey = o.optString("apiKey", ""),
                apiUser = o.optString("apiUser", ""),
                tags = o.optString("tags", "")
            ))
        }
        // Always include all source types, inserting defaults for any not yet persisted
        val seen = result.map { it.type }.toSet()
        result + SourceType.entries.filter { it !in seen }.map {
            LocalSource(type = it, enabled = it.safeContent && !it.needsApiUser)
        }
    } catch (_: Exception) { emptyList() }

    private fun serialize(sources: List<LocalSource>): String =
        JSONArray().also { arr ->
            sources.forEach { s ->
                arr.put(JSONObject().apply {
                    put("type", s.type.name)
                    put("enabled", s.enabled)
                    put("apiKey", s.apiKey)
                    put("apiUser", s.apiUser)
                    put("tags", s.tags)
                })
            }
        }.toString()
}
