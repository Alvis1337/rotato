package com.chrisalvis.rotato.data.plugins

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chrisalvis.rotato.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages the full catalog of [PluginManifest] instances: bundled (from assets/) and
 * user-installed (from remote URLs, persisted in DataStore).
 *
 * Bundled manifests live in `assets/plugins/index.json` (array of filenames) and
 * individual JSON files like `assets/plugins/gelbooru.json`.
 *
 * Installed manifests are serialized to DataStore and override bundled ones with the same ID.
 */
class PluginRepository(private val context: Context) {

    companion object {
        private val INSTALLED_PLUGINS_KEY = stringPreferencesKey("installed_plugins_json")

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /** All available manifests: bundled + installed, deduplicated (installed overrides bundled). */
    val manifests: Flow<List<PluginManifest>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val bundled = loadBundledManifests()
            val installed = parseInstalledManifests(prefs[INSTALLED_PLUGINS_KEY] ?: "[]")
            val installedIds = installed.map { it.id }.toSet()
            bundled.filter { it.id !in installedIds } + installed
        }

    /** Returns the manifest with [id], checking installed first then bundled. Null if not found. */
    suspend fun getManifest(id: String): PluginManifest? = manifests.first().firstOrNull { it.id == id }

    /**
     * Fetches a manifest JSON from [url], validates it, and persists it.
     * Throws an exception if the fetch or parse fails.
     */
    suspend fun installFromUrl(url: String): PluginManifest = withContext(Dispatchers.IO) {
        val json = fetchRemoteJson(url) ?: throw IllegalStateException("Failed to fetch manifest from $url")
        val manifest = PluginManifest.fromJson(json, sourceUrl = url)
            ?: throw IllegalArgumentException("Invalid plugin manifest at $url")
        saveManifest(manifest)
        manifest
    }

    /** Removes an installed plugin by [id]. No-op if not installed. */
    suspend fun uninstall(id: String) {
        context.dataStore.edit { prefs ->
            val current = parseInstalledManifests(prefs[INSTALLED_PLUGINS_KEY] ?: "[]")
            val updated = current.filter { it.id != id }
            prefs[INSTALLED_PLUGINS_KEY] = serializeManifests(updated)
        }
    }

    /** Returns all installed (user-added) manifests. */
    val installedManifests: Flow<List<PluginManifest>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseInstalledManifests(prefs[INSTALLED_PLUGINS_KEY] ?: "[]") }

    // ---------------------------------------------------------------------------
    // Bundled manifest loading
    // ---------------------------------------------------------------------------

    private fun loadBundledManifests(): List<PluginManifest> = runCatching {
        val indexJson = context.assets.open("plugins/index.json")
            .bufferedReader().use { it.readText() }
        val index = JSONArray(indexJson)
        (0 until index.length()).mapNotNull { i ->
            val filename = index.optString(i).ifBlank { return@mapNotNull null }
            runCatching {
                val text = context.assets.open("plugins/$filename")
                    .bufferedReader().use { it.readText() }
                PluginManifest.fromJson(JSONObject(text))
            }.getOrNull()
        }
    }.getOrElse { emptyList() }

    // ---------------------------------------------------------------------------
    // Installed manifest persistence
    // ---------------------------------------------------------------------------

    /** Persists a manifest to the installed-plugins DataStore entry. */
    suspend fun saveManifest(manifest: PluginManifest) {
        context.dataStore.edit { prefs ->
            val current = parseInstalledManifests(prefs[INSTALLED_PLUGINS_KEY] ?: "[]").toMutableList()
            current.removeAll { it.id == manifest.id }
            current.add(manifest)
            prefs[INSTALLED_PLUGINS_KEY] = serializeManifests(current)
        }
    }

    private fun parseInstalledManifests(json: String): List<PluginManifest> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            PluginManifest.fromJson(arr.optJSONObject(i) ?: return@mapNotNull null)
        }
    }.getOrElse { emptyList() }

    private fun serializeManifests(manifests: List<PluginManifest>): String =
        JSONArray().also { arr -> manifests.forEach { arr.put(it.toJson()) } }.toString()

    // ---------------------------------------------------------------------------
    // Remote fetch
    // ---------------------------------------------------------------------------

    private fun fetchRemoteJson(url: String): JSONObject? = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Rotato/1.0 plugin-installer")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            JSONObject(resp.body?.string() ?: return@use null)
        }
    }.getOrNull()
}
