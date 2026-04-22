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
import com.chrisalvis.rotato.data.fetchPageFromSource
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.plugins.SourcePluginRegistry
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        .map { all -> all.filter { !it.isLocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val nsfwMode: StateFlow<Boolean> = prefs.nsfwMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val brainrotFilters: StateFlow<BrainrotFilters> = prefs.brainrotFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrainrotFilters())

    val globalBlacklist: StateFlow<Set<String>> = prefs.globalBlacklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val discoverBatchSize: StateFlow<Int> = prefs.discoverBatchSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20)

    val sourceHealth = SourceHealthTracker.health

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Composite "source:id" dedup — session-long to prevent repeat items */
    private val displayedKeys = mutableSetOf<String>()

    /** Page-level cache: keyed by "SOURCETYPE:query", populated in parallel at load time */
    private val pageCache = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<BrainrotWallpaper>>()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val undoStack = ArrayDeque<BrainrotWallpaper>(3)

    private val _skipEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipEvent: SharedFlow<Unit> = _skipEvent

    private var fetchJob: Job? = null

    init {
        android.util.Log.d("RotatoDebug", "BrainrotViewModel.init start")
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
        android.util.Log.d("RotatoDebug", "BrainrotViewModel.init done")
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
     * Loads the next batch of items into the grid.
     * [reset] = true clears the grid and caches (pull-to-refresh, filter change, etc.).
     *
     * Strategy:
     *  1. Compute queries once per source (stable — avoids MAL shuffle mismatch between steps).
     *  2. Pre-warm page caches for all enabled sources **in parallel** (one API call each).
     *  3. Drain from the in-memory caches — no network during drain.
     *  4. Batch-update the grid with all new items in a single state emission.
     */
    fun loadMore(reset: Boolean = false) {
        if (reset) {
            fetchJob?.cancel()
            fetchJob = null
            _gridItems.update { emptyList() }
            displayedKeys.clear()
            pageCache.clear()
            _endReached.update { false }
            _noResults.update { false }
        }
        if (_endReached.value) return
        if (fetchJob?.isActive == true) return

        fetchJob = viewModelScope.launch {
            val isInitial = _gridItems.value.isEmpty()
            if (isInitial) _loading.update { true } else _loadingMore.update { true }

            val ctx = getApplication<Application>().applicationContext
            val nsfw = prefs.nsfwMode.first()
            val filters = prefs.brainrotFilters.first()
            val blacklist = prefs.globalBlacklist.first()
            val explicitQuery = _searchQuery.value
            val malTitles: List<String> =
                if (explicitQuery.isBlank()) malPrefs.animeList.first() else emptyList()
            val localEnabled = localSources.sources.first().filter { it.enabled }
            val batchSize = prefs.discoverBatchSize.first()
            val target = if (isInitial) batchSize * 2 else batchSize

            // Compute queries once per source so pre-warm and drain use the same cache keys.
            // queriesFor() shuffles MAL titles — calling it twice would produce different keys.
            val sourcesWithQueries: List<Pair<LocalSource, List<String>>> = localEnabled.mapNotNull { source ->
                val plugin = SourcePluginRegistry.forType(source.type) ?: return@mapNotNull null
                if (plugin.requiresCredentials &&
                    (source.apiKey.isBlank() || (plugin.needsApiUser && source.apiUser.isBlank()))) return@mapNotNull null
                source to queriesFor(source, explicitQuery, malTitles)
            }

            // Step 1: fetch pages from ALL sources in parallel (network bound)
            val seenKeys = displayedKeys.toSet()
            sourcesWithQueries.mapNotNull { (source, queries) ->
                if (queries.all { q -> pageCache[cacheKey(source, q)]?.isNotEmpty() == true }) return@mapNotNull null
                val sourceKey = source.type.name.lowercase()
                async(Dispatchers.IO) {
                    for (q in queries) {
                        val ck = cacheKey(source, q)
                        if (pageCache[ck]?.isNotEmpty() == true) continue
                        val excludes = seenKeys
                            .filter { it.startsWith("$sourceKey:") }
                            .map { it.removePrefix("$sourceKey:") }
                        val page = try {
                            fetchPageFromSource(source, q, excludes, nsfw, filters)
                        } catch (e: Exception) { emptyList() }
                        if (page.isNotEmpty()) pageCache[ck] = ArrayDeque(page.shuffled())
                    }
                }
            }.awaitAll()

            // Step 2: drain from caches — purely in-memory, no network
            val newItems = mutableListOf<BrainrotWallpaper>()
            var totalSkipped = 0
            while (newItems.size < target) {
                val wp = drainOne(sourcesWithQueries) ?: break  // null = all caches empty
                val key = "${wp.source}:${wp.id}"
                if (key in displayedKeys) { if (++totalSkipped >= 200) break; continue }
                if (blacklist.isNotEmpty() && wp.tags.any { it.lowercase() in blacklist }) { totalSkipped++; continue }
                totalSkipped = 0
                displayedKeys.add(key)
                val url = wp.sampleUrl.ifBlank { wp.fullUrl }
                if (url.isNotBlank()) {
                    ctx.imageLoader.enqueue(
                        ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                    )
                }
                newItems += wp
            }

            if (newItems.isEmpty()) {
                if (_gridItems.value.isEmpty()) {
                    _noResults.update { true }
                    _endReached.update { true }
                } else {
                    // Grid has content but this page was exhausted — clear cache so next
                    // scroll fetch pulls a fresh page rather than declaring end of results.
                    pageCache.clear()
                    _endReached.update { false }
                }
            } else {
                _gridItems.update { it + newItems }  // single batch update → one recomposition
            }

            if (isInitial) _loading.update { false } else _loadingMore.update { false }
        }
    }

    private fun cacheKey(source: LocalSource, query: String) = "${source.type.name}:$query"

    private fun queriesFor(source: LocalSource, explicitQuery: String, malTitles: List<String>): List<String> = when {
        source.tags.isNotBlank() -> listOf(source.tags)
        explicitQuery.isNotBlank() -> listOf(explicitQuery)
        malTitles.isNotEmpty() -> malTitles.shuffled().take(3)
        else -> listOf("")
    }

    /** Pull one unseen item from the in-memory page caches (no network calls). */
    private fun drainOne(sourcesWithQueries: List<Pair<LocalSource, List<String>>>): BrainrotWallpaper? {
        for ((source, queries) in sourcesWithQueries.shuffled()) {
            for (q in queries) {
                val cached = pageCache[cacheKey(source, q)] ?: continue
                while (cached.isNotEmpty()) {
                    val wp = cached.removeFirst()
                    if ("${wp.source}:${wp.id}" !in displayedKeys) return wp
                }
            }
        }
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

    fun setGlobalBlacklist(tags: Set<String>) {
        viewModelScope.launch {
            prefs.setGlobalBlacklist(tags)
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

    fun saveToGallery(wp: BrainrotWallpaper) {
        val key = "gallery:${wp.id}"
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            val ctx = getApplication<Application>().applicationContext
            val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            val ok = feedRepo.saveToGallery(ctx, sourceId, wp.fullUrl)
            Toast.makeText(ctx, if (ok) "Saved to Pictures/Rotato" else "Save failed", Toast.LENGTH_SHORT).show()
            _downloadingIds.update { it - key }
        }
    }
}
