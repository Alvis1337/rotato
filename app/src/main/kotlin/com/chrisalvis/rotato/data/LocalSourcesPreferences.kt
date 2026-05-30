package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class LocalSourcesPreferences(private val context: Context) {

    companion object {
        private val SOURCES_KEY = stringPreferencesKey("local_sources_json")
    }

    val sources: Flow<List<LocalSource>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parse(prefs[SOURCES_KEY] ?: "[]") }

    suspend fun update(
        pluginId: String,
        instanceId: String = "",
        enabled: Boolean? = null,
        apiKey: String? = null,
        apiUser: String? = null,
        tags: String? = null,
        wallhavenPurity: String? = null,
        baseUrl: String? = null,
    ) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").toMutableList()
            val idx = current.indexOfFirst { it.pluginId == pluginId && it.instanceId == instanceId }
            if (idx == -1) return@edit
            val existing = current[idx]
            current[idx] = existing.copy(
                enabled = enabled ?: existing.enabled,
                apiKey = apiKey ?: existing.apiKey,
                apiUser = apiUser ?: existing.apiUser,
                tags = tags ?: existing.tags,
                wallhavenPurity = wallhavenPurity ?: existing.wallhavenPurity,
                baseUrl = baseUrl ?: existing.baseUrl,
            )
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun addInstance(pluginId: String, instanceId: String) {
        if (instanceId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").toMutableList()
            if (current.any { it.pluginId == pluginId && it.instanceId == instanceId }) return@edit
            current.add(LocalSource(pluginId = pluginId, instanceId = instanceId, enabled = true))
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun removeInstance(pluginId: String, instanceId: String) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").toMutableList()
            current.removeAll { it.pluginId == pluginId && it.instanceId == instanceId }
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    /** Creates-or-replaces a source entry. Used by backup restore and plugin install. */
    suspend fun upsertSource(source: LocalSource) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").toMutableList()
            val idx = current.indexOfFirst { it.pluginId == source.pluginId && it.instanceId == source.instanceId }
            if (idx == -1) current.add(source) else current[idx] = source
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    suspend fun disableAll() {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]")
            prefs[SOURCES_KEY] = serialize(current.map { it.copy(enabled = false) })
        }
    }

    suspend fun updateSourceNsfw(pluginId: String, instanceId: String, nsfwEnabled: Boolean?) {
        context.dataStore.edit { prefs ->
            val current = parse(prefs[SOURCES_KEY] ?: "[]").toMutableList()
            val idx = current.indexOfFirst { it.pluginId == pluginId && it.instanceId == instanceId }
            if (idx == -1) return@edit
            current[idx] = current[idx].copy(nsfwEnabled = nsfwEnabled)
            prefs[SOURCES_KEY] = serialize(current)
        }
    }

    /** Exports current sources as a JSON string (same format as internal serialization). */
    suspend fun exportSources(): String {
        val prefs = context.dataStore.data.catch { emit(emptyPreferences()) }.first()
        val current = parse(prefs[SOURCES_KEY] ?: "[]")
        return serialize(current)
    }

    /** Imports sources from a JSON string, replacing current sources. */
    suspend fun importSources(json: String) {
        // Only reject truly unparseable JSON; an intentionally-empty backup ([]) is valid
        val arr = try { org.json.JSONArray(json) } catch (_: Exception) { return }
        val parsed = parse(json)
        context.dataStore.edit { prefs ->
            prefs[SOURCES_KEY] = if (arr.length() == 0) "[]" else serialize(parsed)
        }
    }

    private fun parse(json: String): List<LocalSource> = try {
        val arr = JSONArray(json)
        val result = mutableListOf<LocalSource>()
        (0 until arr.length()).forEach { i ->
            val o = arr.getJSONObject(i)
            // Auto-migration: old format stores "type" field, new stores "pluginId"
            val pluginId = o.optString("pluginId").ifBlank { o.optString("type") }
            if (pluginId.isBlank()) return@forEach
            result.add(LocalSource(
                pluginId = pluginId,
                instanceId = o.optString("instanceId", ""),
                enabled = o.optBoolean("enabled", false),
                apiKey = o.optString("apiKey", ""),
                apiUser = o.optString("apiUser", ""),
                tags = o.optString("tags", ""),
                wallhavenPurity = o.optString("wallhavenPurity", "110"),
                nsfwEnabled = o.optString("nsfwEnabled", "")
                    .takeUnless { it.isBlank() || it == "null" }
                    ?.toBooleanStrictOrNull(),
                baseUrl = o.optString("baseUrl", ""),
                extraConfig = o.optJSONObject("extraConfig")?.let { obj ->
                    buildMap { obj.keys().forEach { k -> put(k, obj.optString(k)) } }
                } ?: emptyMap(),
            ))
        }
        result
    } catch (_: Exception) { emptyList() }

    private fun serialize(sources: List<LocalSource>): String =
        JSONArray().also { arr ->
            sources.forEach { s ->
                arr.put(JSONObject().apply {
                    put("pluginId", s.pluginId)
                    put("instanceId", s.instanceId)
                    put("enabled", s.enabled)
                    put("apiKey", s.apiKey)
                    put("apiUser", s.apiUser)
                    put("tags", s.tags)
                    put("wallhavenPurity", s.wallhavenPurity)
                    put("nsfwEnabled", s.nsfwEnabled ?: JSONObject.NULL)
                    if (s.baseUrl.isNotBlank()) put("baseUrl", s.baseUrl)
                    if (s.extraConfig.isNotEmpty()) {
                        val extrasObj = JSONObject()
                        s.extraConfig.forEach { (k, v) -> extrasObj.put(k, v) }
                        put("extraConfig", extrasObj)
                    }
                })
            }
        }.toString()
}
