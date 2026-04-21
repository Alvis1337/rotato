package com.chrisalvis.rotato.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.MalPreferences
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.SourceHealthTracker
import com.chrisalvis.rotato.data.fetchFromSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class BrainrotViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RotatoPreferences(app)
    private val localLists = LocalListsPreferences(app)
    private val localSources = LocalSourcesPreferences(app)
    private val malPrefs = MalPreferences(app)
    private val feedRepo = FeedRepository(File(app.filesDir, "rotato_images").also { it.mkdirs() })

    private val _current = MutableStateFlow<BrainrotWallpaper?>(null)
    val current: StateFlow<BrainrotWallpaper?> = _current.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    private val _noSources = MutableStateFlow(false)
    val noSources: StateFlow<Boolean> = _noSources.asStateFlow()

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

    val nsfwMode: StateFlow<Boolean> = prefs.nsfwMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val brainrotFilters: StateFlow<BrainrotFilters> = prefs.brainrotFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrainrotFilters())

    val enabledLocalSources: StateFlow<List<LocalSource>> = localSources.sources
        .map { it.filter { s -> s.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sourceHealth = SourceHealthTracker.health

    // Names of sources tried during the last fetchNext() that returned no result
    private val _lastTriedSources = MutableStateFlow<List<String>>(emptyList())
    val lastTriedSources: StateFlow<List<String>> = _lastTriedSources.asStateFlow()


    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val seenIds = mutableListOf<String>()

    private val cardQueue = ArrayDeque<BrainrotWallpaper>()
    private val queueTargetSize = 10
    private val queueRefillThreshold = queueTargetSize // refill after every swipe
    private var fillJob: Job? = null

    private val _nextWallpaper = MutableStateFlow<BrainrotWallpaper?>(null)
    val nextWallpaper: StateFlow<BrainrotWallpaper?> = _nextWallpaper.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    init {
        viewModelScope.launch { init() }
        // React when user enables their first source while on the no-sources screen
        viewModelScope.launch {
            localSources.sources.collect { sources ->
                if (_noSources.value && sources.any { it.enabled }) {
                    _noSources.update { false }
                    loadLists()
                    loadFirst()
                }
            }
        }
    }

    private suspend fun init() {
        val localEnabled = localSources.sources.first().filter { it.enabled }
        if (localEnabled.isEmpty()) {
            _noSources.update { true }
            _loading.update { false }
            return
        }
        loadLists()
        loadFirst()
    }

    private fun clearQueue() {
        fillJob?.cancel()
        fillJob = null
        cardQueue.clear()
        seenIds.clear()
        _nextWallpaper.update { null }
    }

    private suspend fun enabledSources() =
        localSources.sources.first().filter { it.enabled }

    private suspend fun fetchNext(): BrainrotWallpaper? {
        val localEnabled = enabledSources()
        if (localEnabled.isEmpty()) return null
        val nsfw = prefs.nsfwMode.first()
        val filters = prefs.brainrotFilters.first()
        val explicitQuery = _searchQuery.value
        val malTitles = if (explicitQuery.isBlank()) malPrefs.animeList.first() else emptyList()
        val excludeSnapshot = seenIds.toList()
        val triedSources = mutableListOf<String>()
        for (source in localEnabled.shuffled()) {
            triedSources += source.type.displayName
            val queriesToTry: List<String> = when {
                source.tags.isNotBlank() -> listOf(source.tags)
                explicitQuery.isNotBlank() -> listOf(explicitQuery)
                malTitles.isNotEmpty() -> malTitles.shuffled().take(3) + listOf("")
                else -> listOf("")
            }
            for (query in queriesToTry) {
                val wp = fetchFromSource(source, query, excludeSnapshot, nsfw, filters)
                if (wp != null) {
                    _lastTriedSources.update { emptyList() }
                    return wp
                }
            }
        }
        _lastTriedSources.update { triedSources.distinct() }
        return null
    }

    private fun loadFirst() {
        viewModelScope.launch {
            if (enabledSources().isEmpty()) {
                _noSources.update { true }
                _loading.update { false }
                return@launch
            }
            _loading.update { true }
            _noResults.update { false }
            val wp = fetchNext()
            if (wp != null) addSeen(wp.id)
            _current.update { wp }
            _noResults.update { wp == null }
            _loading.update { false }
            if (wp != null) fillQueue()
        }
    }

    private fun addSeen(id: String) {
        seenIds.add(id)
        if (seenIds.size > 30) seenIds.removeAt(0)
    }

    private fun fillQueue() {
        if (fillJob?.isActive == true) return
        fillJob = viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            var nullStreak = 0
            while (cardQueue.size < queueTargetSize) {
                val wp = fetchNext()
                if (wp == null) {
                    nullStreak++
                    if (nullStreak >= 3) break // give up after 3 consecutive misses
                    kotlinx.coroutines.delay(500)
                    continue
                }
                nullStreak = 0
                addSeen(wp.id)
                val url = wp.fullUrl.ifBlank { wp.thumbUrl }
                if (url.isNotBlank()) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                    )
                }
                // Preload thumb into memory cache so the placeholder renders instantly
                val thumbUrl = wp.thumbUrl.ifBlank { "" }
                if (thumbUrl.isNotBlank() && thumbUrl != url) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(thumbUrl).memoryCacheKey(thumbUrl).diskCacheKey(thumbUrl).build()
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
            // Queue was empty — fetch inline; retry a few times before declaring no results
            viewModelScope.launch {
                _loading.update { true }
                var wp: BrainrotWallpaper? = null
                repeat(3) { attempt ->
                    if (wp != null) return@repeat
                    if (attempt > 0) kotlinx.coroutines.delay(800L * attempt)
                    wp = fetchNext()
                }
                if (wp != null) addSeen(wp!!.id)
                _current.update { wp }
                _noResults.update { wp == null }
                _loading.update { false }
            }
        }
        if (cardQueue.size < queueRefillThreshold) fillQueue()
    }

    fun setNsfwMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNsfwMode(enabled)
            clearQueue()
            loadFirst()
        }
    }

    fun setMinResolution(value: MinResolution) {
        viewModelScope.launch {
            prefs.setMinResolution(value)
            clearQueue()
            loadFirst()
        }
    }

    fun setAspectRatio(value: AspectRatio) {
        viewModelScope.launch {
            prefs.setAspectRatio(value)
            clearQueue()
            loadFirst()
        }
    }

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.update { query }
        clearQueue()
        loadFirst()
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

    fun downloadToRotation(wp: BrainrotWallpaper) {
        val key = wp.id
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            val ok = feedRepo.downloadWallpaper(sourceId, wp.fullUrl)
            val ctx = getApplication<Application>().applicationContext
            Toast.makeText(ctx, if (ok) "Added to rotation" else "Download failed", Toast.LENGTH_SHORT).show()
            _downloadingIds.update { it - key }
        }
    }
}
