package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.FeedPreferences
import com.chrisalvis.rotato.data.ServerConfig
import com.chrisalvis.rotato.data.ServerFeed
import com.chrisalvis.rotato.data.ServerSettingsRepository
import com.chrisalvis.rotato.data.SourceRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    init {
        load()
    }

    fun load() {
        if (_state.value is ServerSettingsState.Loading) return
        viewModelScope.launch {
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
            _saving.value = false
            if (settingsOk && sourcesOk) {
                _snackMessage.value = "Settings saved"
                val current = _state.value as? ServerSettingsState.Loaded ?: return@launch
                _state.value = current.copy(config = config, sources = sources)
            } else {
                _snackMessage.value = "Failed to save settings"
            }
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

    fun clearSnack() {
        _snackMessage.value = null
    }
}
