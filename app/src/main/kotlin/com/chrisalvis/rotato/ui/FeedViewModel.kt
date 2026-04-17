package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedPreferences
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.FeedSyncResult
import com.chrisalvis.rotato.data.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed class AddFeedState {
    object Idle : AddFeedState()
    object Validating : AddFeedState()
    data class Error(val message: String) : AddFeedState()
}

data class SyncStatus(
    val syncing: Boolean = false,
    val result: FeedSyncResult? = null,
    val error: String? = null
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val feedPreferences = FeedPreferences(application)
    private val imageRepository = ImageRepository(application)
    private val feedRepository = FeedRepository(
        File(application.filesDir, "rotato_images").also { it.mkdirs() }
    )

    val feeds: StateFlow<List<FeedConfig>> = feedPreferences.feeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addFeedState = MutableStateFlow<AddFeedState>(AddFeedState.Idle)
    val addFeedState: StateFlow<AddFeedState> = _addFeedState.asStateFlow()

    private val _syncStatus = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val syncStatus: StateFlow<Map<String, SyncStatus>> = _syncStatus.asStateFlow()

    private val _imagesRefreshTick = MutableStateFlow(0)
    val imagesRefreshTick: StateFlow<Int> = _imagesRefreshTick.asStateFlow()

    fun addFeed(url: String, headers: Map<String, String>) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _addFeedState.update { AddFeedState.Error("URL is required") }
            return
        }
        val trimmedHeaders = headers
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotBlank() }
        viewModelScope.launch {
            _addFeedState.update { AddFeedState.Validating }
            val name = feedRepository.fetchFeedName(trimmedUrl, trimmedHeaders)
            if (name == null) {
                _addFeedState.update { AddFeedState.Error("Could not reach feed — check URL and headers") }
                return@launch
            }
            feedPreferences.addFeed(trimmedUrl, trimmedHeaders, name)
            _addFeedState.update { AddFeedState.Idle }
        }
    }

    fun resetAddFeedState() {
        _addFeedState.update { AddFeedState.Idle }
    }

    fun removeFeed(id: String) {
        viewModelScope.launch { feedPreferences.removeFeed(id) }
    }

    fun syncFeed(feed: FeedConfig) {
        if (_syncStatus.value[feed.id]?.syncing == true) return
        viewModelScope.launch {
            _syncStatus.update { it + (feed.id to SyncStatus(syncing = true)) }
            try {
                val result = feedRepository.syncFeed(feed)
                feedPreferences.updateLastSync(feed.id, System.currentTimeMillis())
                _syncStatus.update { it + (feed.id to SyncStatus(result = result)) }
                if (result.added > 0) _imagesRefreshTick.update { it + 1 }
            } catch (e: Exception) {
                _syncStatus.update { it + (feed.id to SyncStatus(error = e.message ?: "Sync failed")) }
            }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            feeds.value.forEach { feed ->
                if (_syncStatus.value[feed.id]?.syncing != true) syncFeed(feed)
            }
        }
    }
}
