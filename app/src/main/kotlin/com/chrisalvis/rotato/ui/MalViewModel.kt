package com.chrisalvis.rotato.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.MalPreferences
import com.chrisalvis.rotato.data.MalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MalViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = MalPreferences(app)
    private val repo = MalRepository(app)

    val isLoggedIn: StateFlow<Boolean> = prefs.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val username: StateFlow<String> = prefs.username
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val animeCount: StateFlow<Int> = prefs.animeList
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(context: Context) {
        viewModelScope.launch {
            _loading.update { true }
            _error.update { null }
            runCatching {
                val url = repo.buildAuthUrl()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.onFailure {
                _error.update { it.message }
            }
            _loading.update { false }
        }
    }

    fun handleCallback(code: String) {
        viewModelScope.launch {
            _loading.update { true }
            _error.update { null }
            repo.exchangeCode(code)
                .onSuccess { fetchAnimeList() }
                .onFailure { _error.update { "Login failed: ${it.message}" } }
            _loading.update { false }
        }
    }

    fun logout() {
        viewModelScope.launch { prefs.clearAuth() }
    }

    fun refresh() {
        fetchAnimeList()
    }

    private fun fetchAnimeList() {
        viewModelScope.launch {
            _loading.update { true }
            _error.update { null }
            repo.fetchAnimeList()
                .onSuccess { titles ->
                    prefs.setAnimeList(titles)
                    val ctx = getApplication<Application>().applicationContext
                    Toast.makeText(ctx, "MAL list updated: ${titles.size} anime", Toast.LENGTH_SHORT).show()
                }
                .onFailure { _error.update { "Failed to fetch anime list: ${it.message}" } }
            _loading.update { false }
        }
    }
}
