package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.PluginStoreEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep { WELCOME, DISCOVER, LIBRARY, COLLECTIONS, FINISH }
enum class SetupStoreState { IDLE, LOADING, LOADED, ERROR }
enum class SetupInstallState { IDLE, BUSY, SUCCESS, ERROR }

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val rotatoPrefs = RotatoPreferences(app)
    private val pluginRepository = PluginRepository(app)
    private val sourcesPrefs = LocalSourcesPreferences(app)

    private val _step = MutableStateFlow(SetupStep.WELCOME)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _storeState = MutableStateFlow(SetupStoreState.IDLE)
    val storeState: StateFlow<SetupStoreState> = _storeState.asStateFlow()

    private val _storeEntries = MutableStateFlow<List<PluginStoreEntry>>(emptyList())
    val storeEntries: StateFlow<List<PluginStoreEntry>> = _storeEntries.asStateFlow()

    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    val installingIds: StateFlow<Set<String>> = _installingIds.asStateFlow()

    private val _installedIds = MutableStateFlow<Set<String>>(emptySet())
    val installedIds: StateFlow<Set<String>> = _installedIds.asStateFlow()

    fun loadOfficialStore() {
        if (_storeState.value == SetupStoreState.LOADING) return
        viewModelScope.launch {
            _storeState.update { SetupStoreState.LOADING }
            try {
                val (_, entries) = pluginRepository.fetchFromIndexUrl(PluginRepository.STORE_INDEX_URL)
                _storeEntries.update { entries }
                _storeState.update { SetupStoreState.LOADED }
            } catch (_: Exception) {
                _storeState.update { SetupStoreState.ERROR }
            }
        }
    }

    fun installPlugin(entry: PluginStoreEntry) {
        if (entry.id in _installingIds.value) return
        viewModelScope.launch {
            _installingIds.update { it + entry.id }
            try {
                val manifest = pluginRepository.installFromUrl(entry.manifestUrl)
                val current = sourcesPrefs.sources.first()
                if (current.none { it.pluginId == manifest.id }) {
                    sourcesPrefs.upsertSource(LocalSource(pluginId = manifest.id, enabled = true))
                }
                _installedIds.update { it + entry.id }
            } catch (_: Exception) { /* silent — user can install later from store */ }
            _installingIds.update { it - entry.id }
        }
    }

    fun complete() {
        viewModelScope.launch {
            rotatoPrefs.setSetupDone(true)
            rotatoPrefs.dismissPluginSystemIntro()
        }
    }
}
