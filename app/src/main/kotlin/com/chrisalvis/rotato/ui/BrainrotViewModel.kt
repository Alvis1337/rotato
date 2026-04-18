package com.chrisalvis.rotato.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.BrainrotRepository
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.RemoteList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

class BrainrotViewModel(
    app: Application,
    private val feed: FeedConfig
) : AndroidViewModel(app) {

    private val baseUrl = try {
        URL(feed.url).let { "${it.protocol}://${it.authority}" }
    } catch (_: Exception) { feed.url }

    private val brainrotRepo = BrainrotRepository(baseUrl, feed.headers)
    private val feedRepo = FeedRepository(
        File(app.filesDir, "rotato_images").also { it.mkdirs() }
    )

    private val _current = MutableStateFlow<BrainrotWallpaper?>(null)
    val current: StateFlow<BrainrotWallpaper?> = _current.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    private val _sessionSaved = MutableStateFlow(0)
    val sessionSaved: StateFlow<Int> = _sessionSaved.asStateFlow()

    private val _sessionSkipped = MutableStateFlow(0)
    val sessionSkipped: StateFlow<Int> = _sessionSkipped.asStateFlow()

    private val _lists = MutableStateFlow<List<RemoteList>>(emptyList())
    val lists: StateFlow<List<RemoteList>> = _lists.asStateFlow()

    private val _selectedListId = MutableStateFlow<Int?>(null)
    val selectedListId: StateFlow<Int?> = _selectedListId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val seenIds = mutableListOf<String>()
    private var nextCard: BrainrotWallpaper? = null
    private var prefetchJob: Job? = null

    init {
        loadLists()
        loadFirst()
    }

    private fun loadFirst() {
        viewModelScope.launch {
            _loading.update { true }
            _noResults.update { false }
            val wp = brainrotRepo.fetchWallpaper(seenIds)
            if (wp != null) seenIds.add(wp.id)
            _current.update { wp }
            _noResults.update { wp == null }
            _loading.update { false }
            if (wp != null) prefetchNext()
        }
    }

    private fun loadLists() {
        viewModelScope.launch {
            val result = brainrotRepo.fetchLists()
            _lists.update { result }
            if (_selectedListId.value == null && result.isNotEmpty()) {
                _selectedListId.update { result.first().id }
            }
        }
    }

    private fun prefetchNext() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val wp = brainrotRepo.fetchWallpaper(seenIds)
            if (wp != null) seenIds.add(wp.id)
            nextCard = wp
        }
    }

    private fun advanceCard() {
        viewModelScope.launch {
            val nxt = nextCard
            nextCard = null
            if (nxt != null) {
                _current.update { nxt }
                _noResults.update { false }
                _loading.update { false }
                prefetchNext()
            } else {
                _loading.update { true }
                _current.update { null }
                val wp = brainrotRepo.fetchWallpaper(seenIds)
                if (wp != null) seenIds.add(wp.id)
                _current.update { wp }
                _noResults.update { wp == null }
                _loading.update { false }
                if (wp != null) prefetchNext()
            }
        }
    }

    fun skip() {
        if (_busy.value) return
        _sessionSkipped.update { it + 1 }
        advanceCard()
    }

    fun saveToRotation() {
        if (_busy.value) return
        val wp = _current.value ?: return
        _busy.update { true }
        viewModelScope.launch {
            val ok = feedRepo.downloadWallpaper(wp.id, wp.fullUrl)
            val ctx = getApplication<Application>().applicationContext
            if (ok) {
                _sessionSaved.update { it + 1 }
                Toast.makeText(ctx, "Added to rotation", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
            }
            _busy.update { false }
            advanceCard()
        }
    }

    fun addToList(listId: Int) {
        if (_busy.value) return
        val wp = _current.value ?: return
        _busy.update { true }
        _selectedListId.update { listId }
        viewModelScope.launch {
            val ok = brainrotRepo.addToList(listId, wp)
            val ctx = getApplication<Application>().applicationContext
            if (ok) {
                val listName = _lists.value.find { it.id == listId }?.name ?: "list"
                _sessionSaved.update { it + 1 }
                Toast.makeText(ctx, "Saved to \"$listName\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Failed to save to list", Toast.LENGTH_SHORT).show()
            }
            _busy.update { false }
            advanceCard()
        }
    }

    fun setSelectedList(listId: Int) {
        _selectedListId.update { listId }
    }

    fun retry() {
        _noResults.update { false }
        seenIds.clear()
        nextCard = null
        loadFirst()
    }
}

class BrainrotViewModelFactory(
    private val app: Application,
    private val feed: FeedConfig
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BrainrotViewModel(app, feed) as T
    }
}
