package com.chrisalvis.rotato.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedPreferences
import com.chrisalvis.rotato.data.ServerConfig
import com.chrisalvis.rotato.data.ServerFeed
import com.chrisalvis.rotato.data.ServerSettingsRepository
import com.chrisalvis.rotato.data.SourceRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed class ServerSettingsState {
    data object Loading : ServerSettingsState()
    data object NoFeed : ServerSettingsState()
    data class Loaded(
        val serverUrl: String,
        val config: ServerConfig,
        val sources: List<SourceRow>,
        val feeds: List<ServerFeed>
    ) : ServerSettingsState()
    data class Error(val message: String) : ServerSettingsState()
}

class ServerSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val feedPrefs = FeedPreferences(app)

    private val _state = MutableStateFlow<ServerSettingsState>(ServerSettingsState.Loading)
    val state: StateFlow<ServerSettingsState> = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    private val _testResults = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val testResults: StateFlow<Map<String, Boolean?>> = _testResults.asStateFlow()

    private var repo: ServerSettingsRepository? = null
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.value = ServerSettingsState.Loading
            val feeds = feedPrefs.feeds.first()
            if (feeds.isEmpty()) {
                _state.value = ServerSettingsState.NoFeed
                return@launch
            }
            val baseUrl = feeds.first().url
                .substringBefore("?")
                .trimEnd('/')
                .let { if (it.endsWith("/api/brainrot", ignoreCase = true)) it.substringBeforeLast("/api/brainrot") else it }
                .let { if (it.contains("/api/")) it.substringBefore("/api/") else it }
            repo = ServerSettingsRepository(baseUrl)
            val r = repo!!
            try {
                val config = r.fetchSettings()
                val sources = r.fetchSources()
                val serverFeeds = r.fetchFeeds()
                _state.value = ServerSettingsState.Loaded(
                    serverUrl = baseUrl,
                    config = config,
                    sources = sources,
                    feeds = serverFeeds
                )
            } catch (e: Exception) {
                _state.value = ServerSettingsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun saveAll(config: ServerConfig, sources: List<SourceRow>) {
        val r = repo ?: return
        viewModelScope.launch {
            _saving.value = true
            val settingsOk = r.saveSettings(config)
            val sourcesOk = r.saveSources(sources)
            if (settingsOk && sourcesOk) {
                val current = _state.value as? ServerSettingsState.Loaded ?: run {
                    _saving.value = false
                    _snackMessage.value = "Failed to save settings"
                    return@launch
                }
                _state.value = current.copy(config = config, sources = sources)
                _saving.value = false
                _snackMessage.value = "Settings saved"
            } else {
                _saving.value = false
                _snackMessage.value = "Failed to save settings"
            }
        }
    }

    fun syncFeeds() {
        val r = repo ?: return
        viewModelScope.launch {
            _saving.value = true
            val added = r.syncFeeds(feedPrefs)
            _saving.value = false
            _snackMessage.value = if (added > 0) "Synced $added feed(s) from server" else "Feeds already up to date"
        }
    }

    fun generateApiKey() {
        val r = repo ?: return
        viewModelScope.launch {
            val key = r.generateApiKey()
            if (key != null) {
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(config = current.config.copy(feedApiKey = key))
                _snackMessage.value = "New API key generated"
            } else {
                _snackMessage.value = "Failed to generate API key"
            }
        }
    }

    fun createFeed(slug: String, name: String) {
        val r = repo ?: return
        viewModelScope.launch {
            val feed = r.createFeed(slug, name)
            if (feed != null) {
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(feeds = listOf(feed) + current.feeds)
                _snackMessage.value = "Feed created"
            } else {
                _snackMessage.value = "Failed to create feed (slug may already exist)"
            }
        }
    }

    fun deleteFeed(id: Int) {
        val r = repo ?: return
        viewModelScope.launch {
            val ok = r.deleteFeed(id)
            if (ok) {
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(feeds = current.feeds.filter { it.id != id })
            } else {
                _snackMessage.value = "Failed to delete feed"
            }
        }
    }

    fun testSource(name: String, apiKey: String, apiUser: String) {
        val r = repo ?: return
        viewModelScope.launch {
            _testResults.value = _testResults.value + (name to null)
            val (ok, error) = r.testSource(name, apiKey, apiUser)
            _testResults.value = _testResults.value + (name to ok)
            if (!ok && error != null) _snackMessage.value = "$name: $error"
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val loaded = _state.value as? ServerSettingsState.Loaded
                val appFeeds = feedPrefs.feeds.first()
                val root = JSONObject().apply {
                    put("version", 1)
                    put("app_feeds", JSONArray(appFeeds.map { f ->
                        JSONObject().apply {
                            put("id", f.id)
                            put("url", f.url)
                            put("name", f.name)
                            put("headers", JSONObject(f.headers))
                        }
                    }))
                    if (loaded != null) {
                        put("server_sources", JSONArray(loaded.sources.map { s ->
                            JSONObject().apply {
                                put("name", s.name)
                                put("enabled", s.enabled)
                                put("apiKey", s.apiKey)
                                put("apiUser", s.apiUser)
                            }
                        }))
                        put("server_feeds", JSONArray(loaded.feeds.map { f ->
                            JSONObject().apply {
                                put("id", f.id)
                                put("slug", f.slug)
                                put("name", f.name)
                            }
                        }))
                        put("server_config", JSONObject().apply {
                            put("feedApiKey", loaded.config.feedApiKey)
                            put("sorting", loaded.config.sorting)
                            put("minResolution", loaded.config.minResolution)
                            put("aspectRatio", loaded.config.aspectRatio)
                            put("searchSuffix", loaded.config.searchSuffix)
                            put("nsfwMode", loaded.config.nsfwMode)
                            put("malClientId", loaded.config.malClientId)
                            put("malClientSecret", loaded.config.malClientSecret)
                            put("redirectUri", loaded.config.redirectUri)
                        })
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(root.toString(2).toByteArray())
                }
                _snackMessage.value = "Backup exported"
            } catch (e: Exception) {
                _snackMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        val r = repo
        viewModelScope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: throw Exception("Could not read file")
                val root = JSONObject(json)

                // Restore app feeds
                val appFeedsArr = root.optJSONArray("app_feeds")
                if (appFeedsArr != null) {
                    val existing = feedPrefs.feeds.first()
                    val existingUrls = existing.map { it.url }.toSet()
                    for (i in 0 until appFeedsArr.length()) {
                        val fo = appFeedsArr.getJSONObject(i)
                        val url = fo.getString("url")
                        if (url !in existingUrls) {
                            val headers = fo.optJSONObject("headers")?.let { ho ->
                                ho.keys().asSequence().associateWith { ho.getString(it) }
                            } ?: emptyMap()
                            feedPrefs.addFeed(url, headers, fo.optString("name", "Feed"))
                        }
                    }
                }

                // Restore server sources/config
                if (r != null) {
                    val sourcesArr = root.optJSONArray("server_sources")
                    if (sourcesArr != null) {
                        val sources = (0 until sourcesArr.length()).map { i ->
                            val so = sourcesArr.getJSONObject(i)
                            SourceRow(
                                name = so.getString("name"),
                                enabled = so.getBoolean("enabled"),
                                apiKey = so.optString("apiKey", ""),
                                apiUser = so.optString("apiUser", "")
                            )
                        }
                        r.saveSources(sources)
                    }
                    val configObj = root.optJSONObject("server_config")
                    if (configObj != null) {
                        val config = ServerConfig(
                            feedApiKey = configObj.optString("feedApiKey", ""),
                            sorting = configObj.optString("sorting", "date_added"),
                            minResolution = configObj.optString("minResolution", ""),
                            aspectRatio = configObj.optString("aspectRatio", ""),
                            searchSuffix = configObj.optString("searchSuffix", ""),
                            nsfwMode = configObj.optBoolean("nsfwMode", false),
                            malClientId = configObj.optString("malClientId", ""),
                            malClientSecret = configObj.optString("malClientSecret", ""),
                            redirectUri = configObj.optString("redirectUri", "")
                        )
                        r.saveSettings(config)
                    }
                }
                _snackMessage.value = "Backup restored — reloading…"
                load()
            } catch (e: Exception) {
                _snackMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearSnack() {
        _snackMessage.value = null
    }
}
