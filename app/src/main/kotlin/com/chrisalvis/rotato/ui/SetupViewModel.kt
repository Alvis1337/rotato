package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.FeedPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.ServerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SetupStep { WELCOME, CONNECT, DONE }

sealed class ConnectState {
    data object Idle : ConnectState()
    data object Connecting : ConnectState()
    data class Error(val message: String) : ConnectState()
}

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val rotatoPrefs = RotatoPreferences(app)
    private val feedPrefs = FeedPreferences(app)

    private val _step = MutableStateFlow(SetupStep.WELCOME)
    val step: StateFlow<SetupStep> = _step.asStateFlow()

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState> = _connectState.asStateFlow()

    private val _feedCount = MutableStateFlow(0)
    val feedCount: StateFlow<Int> = _feedCount.asStateFlow()

    fun advanceToConnect() {
        _step.value = SetupStep.CONNECT
    }

    fun connect(rawUrl: String, apiKey: String) {
        viewModelScope.launch {
            _connectState.value = ConnectState.Connecting
            val url = normalizeUrl(rawUrl)
            val key = apiKey.trim().ifBlank { null }
            val repo = ServerSettingsRepository(url, key)
            val result = runCatching { repo.fetchSettings() }
            if (result.isFailure) {
                _connectState.value = ConnectState.Error(
                    result.exceptionOrNull()?.message ?: "Could not reach server"
                )
                return@launch
            }
            val added = repo.syncFeeds(feedPrefs)
            _feedCount.value = added
            rotatoPrefs.setSetupDone(true)
            _connectState.value = ConnectState.Idle
            _step.value = SetupStep.DONE
        }
    }

    fun skip() {
        viewModelScope.launch {
            rotatoPrefs.setSetupDone(true)
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }
}
