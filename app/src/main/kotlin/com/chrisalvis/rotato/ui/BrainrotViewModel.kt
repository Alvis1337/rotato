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
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.fetchFromSource
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URL

class BrainrotViewModel(app: Application) : AndroidViewModel(app) {

    private val feedPrefs = FeedPreferences(app)
    private val localLists = LocalListsPreferences(app)
    private val localSources = LocalSourcesPreferences(app)
    private val feedRepo = FeedRepository(File(app.filesDir, "rotato_images").also { it.mkdirs() })

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

    val lists: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val enabledLocalSources: StateFlow<List<LocalSource>> = localSources.sources
        .map { it.filter { s -> s.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        // Reactively start when a feed or local source is added while noFeed is true
        viewModelScope.launch {
            feedPrefs.feeds.collect { feeds ->
                if (_noFeed.value && feeds.isNotEmpty()) switchFeed(feeds.first())
            }
        }
        viewModelScope.launch {
            localSources.sources.collect { sources ->
                if (_noFeed.value && sources.any { it.enabled }) {
                    _noFeed.update { false }
                    loadLists()
                    loadSettings()
                    loadFirst()
                }
            }
        }
    }

    private suspend fun initWithFirstFeed() {
        val feeds = feedPrefs.feeds.first()
        val localEnabled = localSources.sources.first().filter { it.enabled }
        if (feeds.isEmpty() && localEnabled.isEmpty()) {
            _noFeed.update { true }
            _loading.update { false }
            return
        }
        if (feeds.isNotEmpty()) {
            switchFeed(feeds.first())
        } else {
            // No server feed — go straight to local sources
            _noFeed.update { false }
            loadLists()
            loadSettings()
            loadFirst()
        }
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

    private suspend fun fetchNext(): BrainrotWallpaper? {
        val repo = brainrotRepo
        val localEnabled = enabledLocalSources.value
        if (repo != null) {
            val wp = repo.fetchWallpaper(seenIds, _selectedSources.value.toList(), _searchQuery.value)
            if (wp != null) return wp
        }
        // No server result — try local sources
        if (localEnabled.isEmpty()) return null
        val shuffled = localEnabled.shuffled()
        for (source in shuffled) {
            val wp = fetchFromSource(source, _searchQuery.value.ifBlank { "anime" }, seenIds, _discoverSettings.value.nsfwMode)
            if (wp != null) return wp
        }
        return null
    }

    private fun loadFirst() {
        viewModelScope.launch {
            if (brainrotRepo == null && enabledLocalSources.value.isEmpty()) {
                _noFeed.update { true }
                _loading.update { false }
                return@launch
            }
            _loading.update { true }
            _noResults.update { false }
            val wp = fetchNext()
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
            val ctx = getApplication<Application>().applicationContext
            while (cardQueue.size < queueTargetSize) {
                val wp = fetchNext() ?: break
                seenIds.add(wp.id)
                val url = wp.fullUrl.ifBlank { wp.thumbUrl }
                if (url.isNotBlank()) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                    )
                }
                cardQueue.addLast(wp)
                if (cardQueue.size == 1) _nextWallpaper.update { wp }
            }
        }
    }

    private fun loadLists() {
        viewModelScope.launch {
            val current = localLists.lists.first()
            if (_selectedListId.value == null && current.isNotEmpty()) {
                _selectedListId.update { current.first().id }
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
            viewModelScope.launch {
                _loading.update { true }
                _current.update { null }
                val wp = fetchNext()
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

    fun reloadSettings() {
        viewModelScope.launch {
            val repo = brainrotRepo ?: return@launch
            val s = repo.fetchSettings()
            if (s != _discoverSettings.value) {
                _discoverSettings.update { s }
                clearQueue()
                loadFirst()
            }
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

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.update { query }
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

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    fun downloadToRotation(wp: BrainrotWallpaper) {
        val key = wp.id
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            val ok = feedRepo.downloadWallpaper(sourceId, wp.fullUrl)
            val ctx = getApplication<Application>().applicationContext
            if (ok) Toast.makeText(ctx, "Added to rotation", Toast.LENGTH_SHORT).show()
            else Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
            _downloadingIds.update { it - key }
        }
    }

    fun skip() {
        if (_busy.value) return
        _sessionSkipped.update { it + 1 }
        advanceCard()
    }

    fun addToList(listId: String) {
        if (_busy.value) return
        val wp = _current.value ?: return
        _selectedListId.update { listId }
        _sessionSaved.update { it + 1 }
        advanceCard()
        viewModelScope.launch {
            val ok = localLists.addWallpaper(listId, wp)
            val ctx = getApplication<Application>().applicationContext
            val listName = lists.value.find { it.id == listId }?.name ?: "list"
            if (ok) {
                Toast.makeText(ctx, "Saved to \"$listName\"", Toast.LENGTH_SHORT).show()
            } else {
                _sessionSaved.update { it - 1 }
                Toast.makeText(ctx, "Already in \"$listName\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setSelectedList(listId: String) {
        _selectedListId.update { listId }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val list = localLists.createList(name)
            _selectedListId.update { list.id }
        }
    }

    fun retry() {
        _noResults.update { false }
        clearQueue()
        loadFirst()
    }
}

