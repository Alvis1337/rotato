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

data class PluginStoreEntry(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val versionCode: Int,
    val manifestUrl: String,
    val tags: List<String>,
    val isBundled: Boolean,
    val safeContent: Boolean,
    /** URL of the index this entry was fetched from. */
    val storeSource: String = "",
    /** Display name of the store this entry came from. */
    val storeName: String = "",
)

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
        private val CUSTOM_STORES_KEY = stringPreferencesKey("custom_store_index_urls")
        const val STORE_INDEX_URL = "https://raw.githubusercontent.com/Alvis1337/rotato/main/plugin-store/index.json"
        const val DEFAULT_STORE_NAME = "Rotato Official"
        private val BUNDLED_IDS = setOf("GELBOORU", "DANBOORU", "RULE34", "SAFEBOORU", "WALLHAVEN", "KONACHAN", "YANDERE", "ZEROCHAN", "REDDIT")

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /** All available manifests: user-installed only. Bundled plugins are no longer auto-loaded. */
    val manifests: Flow<List<PluginManifest>> = installedManifests

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

    /** Flow of user-added store index URLs. */
    val customStoreUrls: Flow<List<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseCustomStoreUrls(prefs[CUSTOM_STORES_KEY] ?: "[]") }

    /**
     * Fetches all store entries from the default index and every user-added index.
     * Returns a map of indexUrl → entries so the UI can group by store.
     */
    suspend fun fetchAllStoreEntries(): Map<String, List<PluginStoreEntry>> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, List<PluginStoreEntry>>()
        runCatching {
            val (_, entries) = fetchFromIndexUrl(STORE_INDEX_URL)
            result[STORE_INDEX_URL] = entries
        }
        for (url in customStoreUrls.first()) {
            runCatching {
                val (_, entries) = fetchFromIndexUrl(url)
                result[url] = entries
            }
        }
        result
    }

    /**
     * Fetches a single index URL. Returns store name + list of entries.
     * Throws on network failure.
     */
    suspend fun fetchFromIndexUrl(url: String): Pair<String, List<PluginStoreEntry>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Rotato/1.0 plugin-store")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} from $url")
            val body = resp.body?.string() ?: throw IllegalStateException("Empty response from $url")
            val json = JSONObject(body)
            val storeName = json.optString("name").ifBlank { extractDomainName(url) }
            val pluginsArr = json.optJSONArray("plugins") ?: return@use storeName to emptyList()
            val entries = (0 until pluginsArr.length()).mapNotNull { i ->
                val o = pluginsArr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val manifestUrl = o.optString("manifestUrl").ifBlank { return@mapNotNull null }
                PluginStoreEntry(
                    id = id,
                    name = o.optString("name", id),
                    description = o.optString("description", ""),
                    author = o.optString("author", "Unknown"),
                    version = o.optString("version", "1.0"),
                    versionCode = o.optInt("versionCode", 1),
                    manifestUrl = manifestUrl,
                    tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: emptyList(),
                    isBundled = o.optBoolean("isBundled", id in BUNDLED_IDS),
                    safeContent = o.optBoolean("safeContent", true),
                    storeSource = url,
                    storeName = storeName,
                )
            }
            storeName to entries
        }
    }

    /** Kept for backward compatibility; fetches only the default store. */
    suspend fun fetchStoreIndex(): List<PluginStoreEntry> = runCatching {
        fetchFromIndexUrl(STORE_INDEX_URL).second
    }.getOrElse { emptyList() }

    /** Returns plugin IDs where the store has a newer versionCode than the installed version. */
    suspend fun checkForUpdates(storeEntries: List<PluginStoreEntry>): Set<String> {
        val installed = installedManifests.first().associateBy { it.id }
        return storeEntries
            .filter { entry -> installed[entry.id]?.let { entry.versionCode > it.versionCode } == true }
            .map { it.id }
            .toSet()
    }

    /**
     * Validates [url] as a plugin index and adds it to the custom store list.
     * Returns the store's display name. Throws if the URL is invalid or unreachable.
     */
    suspend fun addCustomStore(url: String): String {
        val trimmed = url.trim()
        val (storeName, entries) = fetchFromIndexUrl(trimmed)
        if (entries.isEmpty()) throw IllegalStateException("No plugins found at this URL.")
        context.dataStore.edit { prefs ->
            val current = parseCustomStoreUrls(prefs[CUSTOM_STORES_KEY] ?: "[]").toMutableList()
            if (trimmed !in current) current.add(trimmed)
            prefs[CUSTOM_STORES_KEY] = serializeCustomStoreUrls(current)
        }
        return storeName
    }

    /** Removes a custom store URL. No-op if not present. */
    suspend fun removeCustomStore(url: String) {
        context.dataStore.edit { prefs ->
            val current = parseCustomStoreUrls(prefs[CUSTOM_STORES_KEY] ?: "[]").toMutableList()
            current.remove(url)
            prefs[CUSTOM_STORES_KEY] = serializeCustomStoreUrls(current)
        }
    }

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

    private fun parseCustomStoreUrls(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }
    }.getOrElse { emptyList() }

    private fun serializeCustomStoreUrls(urls: List<String>): String =
        JSONArray().also { arr -> urls.forEach { arr.put(it) } }.toString()

    private fun extractDomainName(url: String): String = runCatching {
        java.net.URI(url).host ?: url
    }.getOrElse { url }
}
