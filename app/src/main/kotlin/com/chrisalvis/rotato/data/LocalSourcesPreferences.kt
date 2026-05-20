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

        // Reddit is user-managed (multi-instance) — never auto-created by defaults.
        private fun defaultSources() = SourceType.entries
            .filter { it != SourceType.REDDIT }
            .map { LocalSource(type = it, enabled = it.safeContent && !it.needsApiUser) }
    }

    val sources: Flow<List<LocalSource>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() } }

    suspend fun update(
        type: SourceType,
        instanceId: String = "",
        enabled: Boolean? = null,
        apiKey: String? = null,
        apiUser: String? = null,
        tags: String? = null,
        wallhavenPurity: String? = null,
    ) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }.toMutableList()
            val idx = current.indexOfFirst { it.type == type && it.instanceId == instanceId }
            if (idx == -1) return@edit
            val existing = current[idx]
            current[idx] = existing.copy(
                enabled = enabled ?: existing.enabled,
                apiKey = apiKey ?: existing.apiKey,
                apiUser = apiUser ?: existing.apiUser,
                tags = tags ?: existing.tags,
                wallhavenPurity = wallhavenPurity ?: existing.wallhavenPurity,
            )
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun addInstance(type: SourceType, instanceId: String) {
        if (instanceId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }.toMutableList()
            if (current.any { it.type == type && it.instanceId == instanceId }) return@edit
            current.add(LocalSource(type = type, instanceId = instanceId, enabled = true))
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun removeInstance(type: SourceType, instanceId: String) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }.toMutableList()
            current.removeAll { it.type == type && it.instanceId == instanceId }
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun disableAll() {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }
            prefs[SOURCES_KEY] = serialize(current.map { it.copy(enabled = false) })
        }
    }

    suspend fun updateSourceNsfw(sourceId: String, instanceId: String, nsfwEnabled: Boolean?) {
        val type = runCatching { SourceType.valueOf(sourceId) }.getOrNull() ?: return
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").ifEmpty { defaultSources() }.toMutableList()
            val idx = current.indexOfFirst { it.type == type && it.instanceId == instanceId }
            if (idx == -1) return@edit
            current[idx] = current[idx].copy(nsfwEnabled = nsfwEnabled)
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
                instanceId = o.optString("instanceId", ""),
                enabled = o.optBoolean("enabled", false),
                apiKey = o.optString("apiKey", ""),
                apiUser = o.optString("apiUser", ""),
                tags = o.optString("tags", ""),
                wallhavenPurity = o.optString("wallhavenPurity", "110"),
                nsfwEnabled = o.optString("nsfwEnabled", "")
                    .takeUnless { it.isBlank() || it == "null" }
                    ?.toBooleanStrictOrNull(),
            ))
        }
        // Backfill any missing non-Reddit source types with defaults
        val seenNonReddit = result.filter { it.instanceId.isEmpty() }.map { it.type }.toSet()
        result + SourceType.entries
            .filter { it != SourceType.REDDIT && it !in seenNonReddit }
            .map { LocalSource(type = it, enabled = it.safeContent && !it.needsApiUser) }
    } catch (_: Exception) { emptyList() }

    private fun serialize(sources: List<LocalSource>): String =
        JSONArray().also { arr ->
            sources.forEach { s ->
                arr.put(JSONObject().apply {
                    put("type", s.type.name)
                    put("instanceId", s.instanceId)
                    put("enabled", s.enabled)
                    put("apiKey", s.apiKey)
                    put("apiUser", s.apiUser)
                    put("tags", s.tags)
                    put("wallhavenPurity", s.wallhavenPurity)
                    put("nsfwEnabled", s.nsfwEnabled ?: JSONObject.NULL)
                })
            }
        }.toString()
}
