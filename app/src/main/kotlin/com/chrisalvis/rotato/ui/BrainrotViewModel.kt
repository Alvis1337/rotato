package com.chrisalvis.rotato.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.BrainrotRepository
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.DiscoverSettings
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedPreferences
import com.chrisalvis.rotato.data.RemoteList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URL

class BrainrotViewModel(app: Application) : AndroidViewModel(app) {

    private val feedPrefs = FeedPreferences(app)

    private val _activeFeed = MutableStateFlow<FeedConfig?>(null)
    val activeFeed: StateFlow<FeedConfig?> = _activeFeed.asStateFlow()

    private var brainrotRepo: BrainrotRepository? = null

    private val _current = MutableStateFlow<BrainrotWallpaper?>(null)
    val current: StateFlow<BrainrotWallpaper?> = _current.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    private val _noFeed = MutableStateFlow(false)
    val noFeed: StateFlow<Boolean> = _noFeed.asStateFlow()

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

    private val _discoverSettings = MutableStateFlow(DiscoverSettings())
    val discoverSettings: StateFlow<DiscoverSettings> = _discoverSettings.asStateFlow()

    private val _settingsSaving = MutableStateFlow(false)
    val settingsSaving: StateFlow<Boolean> = _settingsSaving.asStateFlow()

    private val _availableSources = MutableStateFlow<List<String>>(emptyList())
    val availableSources: StateFlow<List<String>> = _availableSources.asStateFlow()

    private val _selectedSources = MutableStateFlow<Set<String>>(emptySet())
    val selectedSources: StateFlow<Set<String>> = _selectedSources.asStateFlow()

    private val seenIds = mutableListOf<String>()

    /** Cards buffered and ready to display, pre-fetched from the server. */
    private val cardQueue = ArrayDeque<BrainrotWallpaper>()
    private val queueTargetSize = 8
    private val queueRefillThreshold = 3
    private var fillJob: Job? = null

    private val _nextWallpaper = MutableStateFlow<BrainrotWallpaper?>(null)
    val nextWallpaper: StateFlow<BrainrotWallpaper?> = _nextWallpaper.asStateFlow()

    init {
        viewModelScope.launch { initWithFirstFeed() }
        // Reactively start when a feed is added while noFeed is true (no restart needed)
        viewModelScope.launch {
            feedPrefs.feeds.collect { feeds ->
                if (_noFeed.value && feeds.isNotEmpty()) {
                    switchFeed(feeds.first())
                }
            }
        }
    }

    private suspend fun initWithFirstFeed() {
        val feeds = feedPrefs.feeds.first()
        if (feeds.isEmpty()) {
            _noFeed.update { true }
            _loading.update { false }
            return
        }
        val feed = feeds.first()
        switchFeed(feed)
    }

    fun switchFeed(feed: FeedConfig) {
        _activeFeed.update { feed }
        val baseUrl = try {
            URL(feed.url).let { "${it.protocol}://${it.authority}" }
        } catch (_: Exception) { feed.url }
        brainrotRepo = BrainrotRepository(baseUrl, feed.headers, feed.serverSlug)
        clearQueue()
        _noFeed.update { false }
        loadLists()
        loadSettings()
        loadSources()
        loadFirst()
    }

    private fun clearQueue() {
        fillJob?.cancel()
        fillJob = null
        cardQueue.clear()
        seenIds.clear()
        _nextWallpaper.update { null }
    }

    private fun loadFirst() {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            _loading.update { true }
            _noResults.update { false }
            val wp = repo.fetchWallpaper(seenIds, _selectedSources.value.toList())
            if (wp != null) seenIds.add(wp.id)
            _current.update { wp }
            _noResults.update { wp == null }
            _loading.update { false }
            if (wp != null) fillQueue()
        }
    }

    /** Fills the card queue up to [queueTargetSize] in the background, warming Coil's cache. */
    private fun fillQueue() {
        if (fillJob?.isActive == true) return
        fillJob = viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            val ctx = getApplication<Application>().applicationContext
            while (cardQueue.size < queueTargetSize) {
                val wp = repo.fetchWallpaper(seenIds, _selectedSources.value.toList())
                    ?: break // no more results
                seenIds.add(wp.id)
                val url = wp.fullUrl.ifBlank { wp.thumbUrl }
                if (url.isNotBlank()) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                    )
                }
                cardQueue.addLast(wp)
                // Expose next card for the background layer as soon as the first queued item arrives
                if (cardQueue.size == 1) _nextWallpaper.update { wp }
            }
        }
    }

    private fun loadLists() {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            val result = repo.fetchLists()
            _lists.update { result }
            if (_selectedListId.value == null && result.isNotEmpty()) {
                _selectedListId.update { result.first().id }
            }
        }
    }

    private fun advanceCard() {
        val nxt = cardQueue.removeFirstOrNull()
        _nextWallpaper.update { cardQueue.firstOrNull() }
        if (nxt != null) {
            _current.update { nxt }
            _noResults.update { false }
            _loading.update { false }
        } else {
            // Queue was empty — fetch directly and show loading
            viewModelScope.launch {
                val repo = brainrotRepo ?: return@launch
                _loading.update { true }
                _current.update { null }
                val wp = repo.fetchWallpaper(seenIds, _selectedSources.value.toList())
                if (wp != null) seenIds.add(wp.id)
                _current.update { wp }
                _noResults.update { wp == null }
                _loading.update { false }
            }
        }
        if (cardQueue.size < queueRefillThreshold) fillQueue()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            val s = repo.fetchSettings()
            _discoverSettings.update { s }
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            val sources = repo.fetchSources()
            _availableSources.update { sources }
            _selectedSources.update { emptySet() }
        }
    }

    fun toggleSource(name: String) {
        _selectedSources.update { prev ->
            if (prev.contains(name)) prev - name else prev + name
        }
        clearQueue()
        loadFirst()
    }

    fun clearSourceFilter() {
        _selectedSources.update { emptySet() }
        clearQueue()
        loadFirst()
    }

    fun saveSettings(settings: DiscoverSettings) {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            _settingsSaving.update { true }
            val ok = repo.updateSettings(settings)
            val ctx = getApplication<Application>().applicationContext
            if (ok) {
                _discoverSettings.update { settings }
                Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
                clearQueue()
                loadFirst()
            } else {
                Toast.makeText(ctx, "Failed to save settings", Toast.LENGTH_SHORT).show()
            }
            _settingsSaving.update { false }
        }
    }

    fun skip() {
        if (_busy.value) return
        _sessionSkipped.update { it + 1 }
        advanceCard()
    }

    fun addToList(listId: Int) {
        if (_busy.value) return
        val wp = _current.value ?: return
        val repo = brainrotRepo ?: return
        _busy.update { true }
        _selectedListId.update { listId }
        viewModelScope.launch {
            val ok = repo.addToList(listId, wp)
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
        clearQueue()
        loadFirst()
    }
}

