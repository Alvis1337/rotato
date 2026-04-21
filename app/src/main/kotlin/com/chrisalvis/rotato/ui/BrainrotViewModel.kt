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
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.fetchFromSource
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.plugins.SourcePluginRegistry
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    /** Grid feed — single source of truth for displayed wallpapers */
    private val _gridItems = MutableStateFlow<List<BrainrotWallpaper>>(emptyList())
    val gridItems: StateFlow<List<BrainrotWallpaper>> = _gridItems.asStateFlow()

    /** Item currently open in fullscreen detail modal (null = no modal) */
    private val _selectedItem = MutableStateFlow<BrainrotWallpaper?>(null)
    val selectedItem: StateFlow<BrainrotWallpaper?> = _selectedItem.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

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

    private val _lastTriedSources = MutableStateFlow<List<String>>(emptyList())
    val lastTriedSources: StateFlow<List<String>> = _lastTriedSources.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Composite "source:id" dedup — session-long to prevent repeat items */
    private val displayedKeys = mutableSetOf<String>()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val undoStack = ArrayDeque<BrainrotWallpaper>(3)

    private val _skipEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipEvent: SharedFlow<Unit> = _skipEvent

    private val _sourceFailureEvent = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val sourceFailureEvent: SharedFlow<String> = _sourceFailureEvent

    private var fetchJob: Job? = null

    init {
        viewModelScope.launch { init() }
        viewModelScope.launch {
            localSources.sources.collect { sources ->
                if (_noSources.value && sources.any { it.enabled }) {
                    _noSources.update { false }
                    loadLists()
                    loadMore(reset = true)
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
        loadMore(reset = true)
    }

    /**
     * Appends up to 12 new items to the grid.
     * [reset] = true clears the grid and restarts from scratch (used on filter/source change).
     */
    fun loadMore(reset: Boolean = false) {
        if (reset) {
            fetchJob?.cancel()
            fetchJob = null
            _gridItems.update { emptyList() }
            displayedKeys.clear()
            _endReached.update { false }
            _noResults.update { false }
        }
        if (_endReached.value) return
        if (fetchJob?.isActive == true) return

        fetchJob = viewModelScope.launch {
            val isInitial = _gridItems.value.isEmpty()
            if (isInitial) _loading.update { true } else _loadingMore.update { true }

            val ctx = getApplication<Application>().applicationContext
            var fetched = 0
            var nullStreak = 0
            val target = 12

            var dupStreak = 0
            while (fetched < target && nullStreak < 3) {
                val wp = fetchNext()
                if (wp == null) {
                    nullStreak++
                    dupStreak = 0
                    if (nullStreak < 3) kotlinx.coroutines.delay(300)
                    continue
                }
                val key = "${wp.source}:${wp.id}"
                if (key in displayedKeys) {
                    if (++dupStreak >= 15) break  // safety valve: too many consecutive dups
                    continue
                }
                dupStreak = 0
                displayedKeys.add(key)
                nullStreak = 0
                val url = wp.fullUrl.ifBlank { wp.thumbUrl }
                if (url.isNotBlank()) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                    )
                }
                val thumbUrl = wp.thumbUrl.ifBlank { "" }
                if (thumbUrl.isNotBlank() && thumbUrl != url) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(thumbUrl).memoryCacheKey(thumbUrl).diskCacheKey(thumbUrl).build()
                    )
                }
                _gridItems.update { it + wp }
                fetched++
            }

            if (fetched == 0) {
                if (_gridItems.value.isEmpty()) _noResults.update { true }
                _endReached.update { true }
            }

            if (isInitial) _loading.update { false } else _loadingMore.update { false }
        }
    }

    private suspend fun fetchNext(): BrainrotWallpaper? {
        val localEnabled = localSources.sources.first().filter { it.enabled }
        if (localEnabled.isEmpty()) return null
        val nsfw = prefs.nsfwMode.first()
        val filters = prefs.brainrotFilters.first()
        val explicitQuery = _searchQuery.value
        val malTitles = if (explicitQuery.isBlank()) malPrefs.animeList.first() else emptyList()
        val triedSources = mutableListOf<String>()
        for (source in localEnabled.shuffled()) {
            val plugin = SourcePluginRegistry.forType(source.type)
            if (plugin?.requiresCredentials == true &&
                (source.apiKey.isBlank() || (plugin.needsApiUser && source.apiUser.isBlank()))) {
                continue
            }
            triedSources += source.type.displayName
            // Strip source prefix from composite keys so plugins compare bare IDs
            val sourceKey = source.type.name.lowercase()
            val sourceExcludes = displayedKeys
                .filter { it.startsWith("$sourceKey:") }
                .map { it.removePrefix("$sourceKey:") }
            val queriesToTry: List<String> = when {
                source.tags.isNotBlank() -> listOf(source.tags)
                explicitQuery.isNotBlank() -> listOf(explicitQuery)
                malTitles.isNotEmpty() -> malTitles.shuffled().take(5)
                else -> listOf("")
            }
            for (query in queriesToTry) {
                val wp = fetchFromSource(source, query, sourceExcludes, nsfw, filters)
                if (wp != null) {
                    _lastTriedSources.update { emptyList() }
                    return wp
                }
            }
            if (localEnabled.size > 1) {
                _sourceFailureEvent.tryEmit(source.type.displayName)
            }
        }
        _lastTriedSources.update { triedSources.distinct() }
        return null
    }

    private fun loadLists() {
        viewModelScope.launch {
            val current = localLists.lists.first()
            if (_selectedListId.value == null && current.isNotEmpty()) {
                _selectedListId.update { current.first().id }
            }
        }
    }

    /** Opens the fullscreen detail modal for [wp], or closes it when null. */
    fun selectItem(wp: BrainrotWallpaper?) {
        _selectedItem.update { wp }
    }

    fun skip(wp: BrainrotWallpaper) {
        _sessionSkipped.update { it + 1 }
        if (undoStack.size >= 3) undoStack.removeFirst()
        undoStack.addLast(wp)
        _skipEvent.tryEmit(Unit)
        removeFromGrid(wp)
        _selectedItem.update { null }
    }

    fun undo() {
        val wp = undoStack.removeLastOrNull() ?: return
        _sessionSkipped.update { (it - 1).coerceAtLeast(0) }
        displayedKeys.remove("${wp.source}:${wp.id}")
        _gridItems.update { listOf(wp) + it }
    }

    fun addToList(listId: String, wp: BrainrotWallpaper) {
        _selectedListId.update { listId }
        _sessionSaved.update { it + 1 }
        removeFromGrid(wp)
        _selectedItem.update { null }
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

    private fun removeFromGrid(wp: BrainrotWallpaper) {
        _gridItems.update { items ->
            items.filter { it.source != wp.source || it.id != wp.id }
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
        loadMore(reset = true)
    }

    fun setNsfwMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNsfwMode(enabled)
            loadMore(reset = true)
        }
    }

    fun setMinResolution(value: MinResolution) {
        viewModelScope.launch {
            prefs.setMinResolution(value)
            loadMore(reset = true)
        }
    }

    fun setAspectRatio(value: AspectRatio) {
        viewModelScope.launch {
            prefs.setAspectRatio(value)
            loadMore(reset = true)
        }
    }

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.update { query }
        loadMore(reset = true)
    }

    fun downloadToRotation(wp: BrainrotWallpaper) {
        val key = wp.id
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            val ok = feedRepo.downloadWallpaper(sourceId, wp.fullUrl)
            val ctx = getApplication<Application>().applicationContext
            if (ok) {
                val history = historyFromJson(prefs.historyJson.first()).toMutableList()
                history.add(0, WallpaperHistoryItem(
                    thumbUrl = wp.thumbUrl,
                    fullUrl = wp.fullUrl,
                    source = wp.source,
                    timestamp = System.currentTimeMillis(),
                    tags = wp.tags,
                    pageUrl = wp.pageUrl
                ))
                prefs.setHistoryJson(history.take(50).toJson())
            }
            Toast.makeText(ctx, if (ok) "Added to rotation" else "Download failed", Toast.LENGTH_SHORT).show()
            _downloadingIds.update { it - key }
        }
    }
}
