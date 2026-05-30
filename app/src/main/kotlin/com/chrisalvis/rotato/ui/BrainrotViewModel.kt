package com.chrisalvis.rotato.ui

import android.app.Application
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.MalPreferences
import com.chrisalvis.rotato.data.MalRepository
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginManifest
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.http
import com.chrisalvis.rotato.data.plugins.normalizeBooruQuery
import com.chrisalvis.rotato.data.historyFromJson
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class SourceHealth(
    val sourceId: String,
    val sourceName: String,
    val lastSuccess: Long = 0L,
    val lastError: String? = null,
    val totalFetches: Int = 0,
    val successCount: Int = 0,
    val isTesting: Boolean = false,
)

enum class NoResultsReason { WIFI_ONLY, SEARCH_EMPTY, EXHAUSTED }

class BrainrotViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RotatoPreferences(app)
    private val localLists = LocalListsPreferences(app)
    private val localSources = LocalSourcesPreferences(app)
    private val malPrefs = MalPreferences(app)
    private val malRepo = MalRepository(app)
    private val feedRepo = FeedRepository(File(app.filesDir, "rotato_images").also { it.mkdirs() })
    private val pluginRepository = PluginRepository(app)

    /** Grid feed — single source of truth for displayed wallpapers */
    private val _gridItems = MutableStateFlow<List<BrainrotWallpaper>>(emptyList())
    val gridItems: StateFlow<List<BrainrotWallpaper>> = _gridItems.asStateFlow()

    /** Item currently open in fullscreen detail modal (null = no modal) */
    private val _selectedItem = MutableStateFlow<BrainrotWallpaper?>(null)
    val selectedItem: StateFlow<BrainrotWallpaper?> = _selectedItem.asStateFlow()

    private val _gridMode = MutableStateFlow(false)
    val gridMode: StateFlow<Boolean> = _gridMode.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    private val _hasNewBatch = MutableStateFlow(false)
    val hasNewBatch: StateFlow<Boolean> = _hasNewBatch.asStateFlow()

    fun clearNewBatch() { _hasNewBatch.update { false } }

    private val _noResults = MutableStateFlow(false)
    val noResults: StateFlow<Boolean> = _noResults.asStateFlow()

    private val _noResultsReason = MutableStateFlow<NoResultsReason?>(null)
    val noResultsReason: StateFlow<NoResultsReason?> = _noResultsReason.asStateFlow()

    private val _noSources = MutableStateFlow(false)
    val noSources: StateFlow<Boolean> = _noSources.asStateFlow()

    private val _sessionSaved = MutableStateFlow(0)
    val sessionSaved: StateFlow<Int> = _sessionSaved.asStateFlow()

    val savedSourceIds: StateFlow<Set<String>> = localLists.allWallpapers
        .map { entries -> entries.map { "${it.source}:${it.sourceId}" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _sessionSkipped = MutableStateFlow(0)
    val sessionSkipped: StateFlow<Int> = _sessionSkipped.asStateFlow()

    val lists: StateFlow<List<LocalList>> = combine(
        localLists.lists,
        (app as com.chrisalvis.rotato.RotatoApp).unlockedListIds
    ) { all, unlocked ->
        all.filter { !it.isLocked || it.id in unlocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val nsfwMode: StateFlow<Boolean> = prefs.nsfwMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val brainrotFilters: StateFlow<BrainrotFilters> = prefs.brainrotFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrainrotFilters())

    val sources: StateFlow<List<LocalSource>> = localSources.sources
        .map { configured -> configured.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSources: StateFlow<List<LocalSource>> = localSources.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinnedSearches: StateFlow<List<String>> = prefs.pinnedSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val globalBlacklist: StateFlow<Set<String>> = prefs.globalBlacklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val discoverHintSeen: StateFlow<Boolean> = prefs.discoverHintSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun dismissDiscoverHint() {
        viewModelScope.launch { prefs.setDiscoverHintSeen() }
    }

    val discoverBatchSize: StateFlow<Int> = prefs.discoverBatchSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20)

    val handsFreeInterval: StateFlow<Int> = prefs.handsFreeInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)

    private val _sourceHealth = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val sourceHealth: StateFlow<Map<String, SourceHealth>> = _sourceHealth.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _batchSelected = MutableStateFlow<Set<String>>(emptySet())
    val batchSelected: StateFlow<Set<String>> = _batchSelected.asStateFlow()
    val batchMode: StateFlow<Boolean> = _batchSelected
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _tagSuggestions = MutableStateFlow<List<String>>(emptyList())
    val tagSuggestions: StateFlow<List<String>> = _tagSuggestions.asStateFlow()

    private val _resetVersion = MutableStateFlow(0)
    val resetVersion: StateFlow<Int> = _resetVersion.asStateFlow()

    val danbooruEnabled: StateFlow<Boolean> = localSources.sources
        .map { sources -> sources.any { it.enabled && it.pluginId == "DANBOORU" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Composite "source:id" dedup — session-long to prevent repeat items */
    private val displayedKeys = mutableSetOf<String>()
    private val persistentBlockedKeys = mutableSetOf<String>()

    /** Tracks how many consecutive fetches returned 0 new items while the grid has content. */
    private var consecutiveEmptyFetches = 0

    /** Page-level cache: keyed by "SOURCETYPE:query", populated in parallel at load time */
    private val pageCache = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<BrainrotWallpaper>>()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val undoStack = ArrayDeque<BrainrotWallpaper>(3)

    private val _skipEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skipEvent: SharedFlow<Unit> = _skipEvent

    private val _blockEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val blockEvent: SharedFlow<Unit> = _blockEvent

    private var fetchJob: Job? = null
    private var tagSuggestionsJob: Job? = null

    init {
        viewModelScope.launch { init() }
        viewModelScope.launch {
            sources.collect { activeSources ->
                val activeIds = activeSources.map(::sourceKey).toSet()
                _sourceHealth.update { health -> health.filterKeys { it in activeIds } }
                if (_noSources.value && activeSources.isNotEmpty()) {
                    _noSources.update { false }
                    loadLists()
                    loadMore(reset = true)
                }
            }
        }
    }

    private suspend fun init() {
        persistentBlockedKeys.clear()
        persistentBlockedKeys.addAll(prefs.blockedImageKeys.first())
        displayedKeys.addAll(persistentBlockedKeys)
        displayedKeys.addAll(prefs.seenWallpaperKeys.first())
        val localEnabled = sources.first()
        if (localEnabled.isEmpty()) {
            _noSources.update { true }
            _loading.update { false }
            return
        }
        loadLists()
        refreshMalCacheIfEnabled() // silently refresh MAL list; buildDiscoverRequests uses it internally
        loadMore(reset = false)
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
            displayedKeys.addAll(persistentBlockedKeys)
            clearBatchSelection()
            viewModelScope.launch(Dispatchers.IO) { prefs.clearSeenWallpaperKeys() }
            pageCache.clear()
            _endReached.update { false }
            _noResults.update { false }
            _noResultsReason.update { null }
            consecutiveEmptyFetches = 0
            _resetVersion.update { it + 1 }
        }
        if (_endReached.value) return
        if (fetchJob?.isActive == true) return

        fetchJob = viewModelScope.launch {
            // Respect "Wi-Fi only for Discover" setting
            val wifiOnly = prefs.wifiOnlyDiscover.first()
            if (wifiOnly) {
                val cm = getApplication<Application>().applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!onWifi) {
                    _noResultsReason.update { NoResultsReason.WIFI_ONLY }
                    _noResults.update { true }
                    return@launch
                }
            }

            val isInitial = _gridItems.value.isEmpty()
            if (isInitial) _loading.update { true } else _loadingMore.update { true }
            try {
            val ctx = getApplication<Application>().applicationContext
            val nsfw = prefs.nsfwMode.first()
            val filters = prefs.brainrotFilters.first()
            val blacklist = prefs.globalBlacklist.first()
            val blockedUrls = prefs.blockedUrls.first()
            val explicitQuery = _searchQuery.value
            val batchSize = prefs.discoverBatchSize.first()
            val target = if (isInitial) batchSize * 2 else batchSize

            // Compute queries once per source so pre-warm and drain use the same cache keys.
            // queriesFor() shuffles MAL titles — calling it twice would produce different keys.
            val sourcesWithQueries = buildDiscoverRequests(nsfw, filters, explicitQuery)

            // When a strict aspect-ratio filter is active most fetched items will be discarded
            // by the plugin-side matches() check. Fetch more candidates per call to compensate.
            val fetchLimit = if (filters.aspectRatio != AspectRatio.ANY || filters.minResolution != MinResolution.ANY) 200 else 100

            // Steps 1+2 repeat up to 3 rounds: if strict filters drain the caches before the
            // target is reached, clear and fetch again rather than stopping short.
            val newItems = mutableListOf<BrainrotWallpaper>()
            var totalSkipped = 0
            var round = 0
            while (newItems.size < target && round < 3) {
            // Step 1: fetch pages from ALL sources in parallel (network bound)
            val seenKeys = displayedKeys.toSet()
            sourcesWithQueries.mapNotNull { request ->
                val source = request.source
                val queries = request.queries
                if (queries.all { q -> pageCache[cacheKey(source, q)]?.isNotEmpty() == true }) return@mapNotNull null
                val sourceKey = source.pluginId.lowercase()
                async(Dispatchers.IO) {
                    for (q in queries) {
                        val ck = cacheKey(source, q)
                        if (pageCache[ck]?.isNotEmpty() == true) continue
                        val excludes = seenKeys
                            .filter { it.startsWith("$sourceKey:") }
                            .map { it.removePrefix("$sourceKey:") }
                        val page = fetchPageForSource(source, q, excludes, request.effectiveNsfw, filters, fetchLimit)
                        Log.d("DiscoverFetch", "${source.pluginId} q=$q → ${page.size} items after filter (r${round+1})")
                        if (page.isNotEmpty()) pageCache[ck] = ArrayDeque(page.shuffled())
                    }
                }
            }.awaitAll()

            // Step 2: drain from caches — purely in-memory, no network
            while (newItems.size < target) {
                val wp = drainOne(sourcesWithQueries) ?: break  // null = all caches empty
                val key = "${wp.source}:${wp.id}"
                if (key in displayedKeys) { if (++totalSkipped >= 200) break; continue }
                if (blacklist.isNotEmpty() && wp.tags.any { it.lowercase() in blacklist }) { totalSkipped++; continue }
                if (blockedUrls.isNotEmpty() && wp.fullUrl in blockedUrls) { totalSkipped++; continue }
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
            // If still under target, clear caches so next round fetches fresh pages
            round++
            if (round < 3 && newItems.size < target) pageCache.clear()
            } // end round loop

            Log.d("DiscoverFetch", "drain complete — ${newItems.size} new items, ${displayedKeys.size} total seen")
            if (newItems.isEmpty()) {
                if (_gridItems.value.isEmpty()) {
                    _noResultsReason.update {
                        if (_searchQuery.value.isNotBlank()) NoResultsReason.SEARCH_EMPTY
                        else NoResultsReason.EXHAUSTED
                    }
                    _noResults.update { true }
                    _endReached.update { true }
                    consecutiveEmptyFetches = 0
                } else {
                    // Grid has content but this page was exhausted. Track consecutive empty
                    // pages — after 2 in a row, declare end of results so the spinner doesn't
                    // loop indefinitely when filters are too restrictive to yield new content.
                    consecutiveEmptyFetches++
                    if (consecutiveEmptyFetches >= 2) {
                        _endReached.update { true }
                        consecutiveEmptyFetches = 0
                    } else {
                        pageCache.clear()
                        _endReached.update { false }
                    }
                }
            } else {
                consecutiveEmptyFetches = 0
                _gridItems.update { it + newItems }  // single batch update → one recomposition
                _hasNewBatch.update { true }
                viewModelScope.launch(Dispatchers.IO) {
                    prefs.addSeenWallpaperKeys(newItems.map { "${it.source}:${it.id}" }.toSet())
                }
            }
            } finally {
                if (isInitial) _loading.update { false } else _loadingMore.update { false }
            }
        }
    }

    private fun cacheKey(source: LocalSource, query: String) = "${source.pluginId}:${source.instanceId}:$query"

    private fun queriesFor(source: LocalSource, explicitQuery: String, malTitles: List<String>): List<String> = when {
        explicitQuery.isNotBlank() -> listOf(explicitQuery)
        source.tags.isNotBlank() -> listOf(source.tags)
        // Pre-normalise MAL titles into compound booru tags (spaces → underscores) so
        // the plugins can apply per-token normalisation without breaking title lookups.
        malTitles.isNotEmpty() -> malTitles.shuffled().take(3).map { normalizeBooruQuery(it) }
        else -> listOf("")
    }

    private data class DiscoverRequest(
        val source: LocalSource,
        val queries: List<String>,
        val effectiveNsfw: Boolean,
    )

    private suspend fun buildDiscoverRequests(
        nsfw: Boolean,
        filters: BrainrotFilters,
        explicitQuery: String,
    ): List<DiscoverRequest> {
        val malTitles = if (explicitQuery.isBlank() && filters.useMalFilter) {
            malPrefs.animeList.first()
        } else {
            emptyList()
        }
        return sources.first()
            .mapNotNull { source ->
                val manifest: PluginManifest = pluginRepository.getManifest(source.pluginId) ?: return@mapNotNull null
                if (manifest.requiresCredentials &&
                    (source.apiKey.isBlank() || (manifest.needsApiUser && source.apiUser.isBlank()))
                ) {
                    return@mapNotNull null
                }
                val effectiveNsfw = source.nsfwEnabled ?: nsfw
                if (!PluginExecutor.canServe(manifest, effectiveNsfw, source)) return@mapNotNull null
                DiscoverRequest(
                    source = source,
                    queries = queriesFor(source, explicitQuery, malTitles),
                    effectiveNsfw = effectiveNsfw,
                )
            }
    }

    private fun sourceKey(source: LocalSource): String =
        if (source.instanceId.isBlank()) source.pluginId else "${source.pluginId}:${source.instanceId}"

    private fun sourceLabel(source: LocalSource): String =
        if (source.pluginId == "REDDIT" && source.instanceId.isNotBlank()) {
            "r/${source.instanceId}"
        } else {
            source.pluginId.lowercase().replaceFirstChar { it.uppercase() }
        }

    private fun updateSourceHealth(source: LocalSource, transform: (SourceHealth) -> SourceHealth) {
        val sourceId = sourceKey(source)
        val sourceName = sourceLabel(source)
        _sourceHealth.update { health ->
            val current = health[sourceId] ?: SourceHealth(sourceId = sourceId, sourceName = sourceName)
            health + (sourceId to transform(current.copy(sourceName = sourceName)))
        }
    }

    private fun markSourceTesting(source: LocalSource, isTesting: Boolean) {
        updateSourceHealth(source) { it.copy(isTesting = isTesting) }
    }

    private fun recordSourceSuccess(source: LocalSource) {
        val now = System.currentTimeMillis()
        updateSourceHealth(source) {
            it.copy(
                lastSuccess = now,
                totalFetches = it.totalFetches + 1,
                successCount = it.successCount + 1,
                isTesting = false,
            )
        }
    }

    private fun recordSourceError(source: LocalSource, message: String) {
        updateSourceHealth(source) {
            it.copy(
                lastError = message,
                totalFetches = it.totalFetches + 1,
                isTesting = false,
            )
        }
    }

    private suspend fun fetchPageForSource(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int,
    ): List<BrainrotWallpaper> {
        val manifest = pluginRepository.getManifest(source.pluginId)
        if (manifest == null) {
            recordSourceError(source, "No manifest registered")
            return emptyList()
        }
        if (!PluginEntitlement.isUnlocked(manifest)) return emptyList()
        return try {
            PluginExecutor.fetchPage(manifest, source, query, exclude, nsfw, filters, limit).also {
                recordSourceSuccess(source)
            }
        } catch (e: Exception) {
            recordSourceError(source, e.message ?: "Unknown error")
            Log.e("DiscoverFetch", "${source.pluginId} q=$query exception: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchSingleForSource(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? {
        val manifest = pluginRepository.getManifest(source.pluginId)
        if (manifest == null) {
            recordSourceError(source, "No plugin registered")
            return null
        }
        if (!PluginEntitlement.isUnlocked(manifest)) return null
        return try {
            PluginExecutor.fetch(manifest, source, query, exclude, nsfw, filters).also {
                recordSourceSuccess(source)
            }
        } catch (e: Exception) {
            recordSourceError(source, e.message ?: "Unknown error")
            null
        }
    }

    /** Pull one unseen item from the in-memory page caches (no network calls). */
    private fun drainOne(sourcesWithQueries: List<DiscoverRequest>): BrainrotWallpaper? {
        for (request in sourcesWithQueries.shuffled()) {
            for (q in request.queries) {
                val cached = pageCache[cacheKey(request.source, q)] ?: continue
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

    fun selectNext() {
        val current = _selectedItem.value ?: return
        val items = _gridItems.value
        val idx = items.indexOfFirst { it.id == current.id && it.source == current.source }
        if (idx + 1 < items.size) _selectedItem.update { items[idx + 1] }
    }

    fun selectPrev() {
        val current = _selectedItem.value ?: return
        val items = _gridItems.value
        val idx = items.indexOfFirst { it.id == current.id && it.source == current.source }
        if (idx > 0) _selectedItem.update { items[idx - 1] }
    }

    fun skip(wp: BrainrotWallpaper) {
        _sessionSkipped.update { it + 1 }
        if (undoStack.size >= 3) undoStack.removeFirst()
        undoStack.addLast(wp)
        _skipEvent.tryEmit(Unit)
        removeFromGrid(wp)
        _selectedItem.update { null }
    }

    /** Block a URL permanently and remove the wallpaper from the current grid. */
    fun blockAndRemove(wp: BrainrotWallpaper) {
        blockImage(wp)
        viewModelScope.launch { prefs.blockUrl(wp.fullUrl) }
        _selectedItem.update { null }
    }

    fun blockImage(wp: BrainrotWallpaper) {
        val key = "${wp.source}:${wp.id}"
        persistentBlockedKeys.add(key)
        displayedKeys.add(key)
        // If this is the currently selected item, advance to the next one instead of closing overlay
        if (_selectedItem.value?.id == wp.id && _selectedItem.value?.source == wp.source) {
            val items = _gridItems.value
            val idx = items.indexOfFirst { it.id == wp.id && it.source == wp.source }
            val next = items.getOrNull(idx + 1) ?: items.getOrNull(idx - 1)
            removeFromGrid(wp)
            _selectedItem.update { next }
        } else {
            removeFromGrid(wp)
        }
        _blockEvent.tryEmit(Unit)
        viewModelScope.launch { prefs.addBlockedImageKey(key) }
    }

    fun undo() {
        val wp = undoStack.removeLastOrNull() ?: return
        _sessionSkipped.update { (it - 1).coerceAtLeast(0) }
        displayedKeys.remove("${wp.source}:${wp.id}")
        _gridItems.update { listOf(wp) + it }
    }

    fun setWallpaperDirectly(wp: BrainrotWallpaper) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val request = ImageRequest.Builder(app)
                .data(wp.fullUrl.ifBlank { wp.thumbUrl })
                .allowHardware(false)
                .build()
            val result = app.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.let {
                (it as? BitmapDrawable)?.bitmap
            }
            if (bitmap == null) {
                Toast.makeText(app, "Failed to load image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val settings = prefs.settings.first()
                val wm = WallpaperManager.getInstance(app)
                val flags = when (settings.wallpaperTarget) {
                    WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                val metrics = app.resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
                val scaledW = (bitmap.width * scale).roundToInt()
                val scaledH = (bitmap.height * scale).roundToInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                val srcX = ((scaledW - screenW) / 2).coerceAtLeast(0)
                val srcY = ((scaledH - screenH) / 2).coerceAtLeast(0)
                val cropped = Bitmap.createBitmap(scaled, srcX, srcY, screenW, screenH)
                wm.setBitmap(cropped, null, true, flags)
                if (cropped != scaled) cropped.recycle()
                if (scaled != bitmap) scaled.recycle()
                Toast.makeText(app, "Wallpaper set!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(app, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToList(listId: String, wp: BrainrotWallpaper) {
        _selectedListId.update { listId }
        _sessionSaved.update { it + 1 }
        removeFromGrid(wp)
        viewModelScope.launch {
            val ok = localLists.addWallpaper(listId, wp)
            val ctx = getApplication<Application>().applicationContext
            val list = lists.value.find { it.id == listId }
            val listName = list?.name ?: "list"
            if (ok) {
                // If this list drives rotation, download the file now so it appears immediately.
                if (list?.useAsRotation == true) {
                    val key = wp.id
                    if (!_downloadingIds.value.contains(key)) {
                        _downloadingIds.update { it + key }
                        try {
                            val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
                            val downloaded = feedRepo.downloadWallpaper(sourceId, wp.fullUrl, wp.sampleUrl)
                            if (downloaded) {
                                val history = historyFromJson(prefs.historyJson.first()).toMutableList()
                                history.add(0, WallpaperHistoryItem(
                                    thumbUrl = wp.thumbUrl, sampleUrl = wp.sampleUrl,
                                    fullUrl = wp.fullUrl, source = wp.source,
                                    timestamp = System.currentTimeMillis(),
                                    tags = wp.tags, pageUrl = wp.pageUrl
                                ))
                                prefs.setHistoryJson(history.take(50).toJson())
                                Toast.makeText(ctx, "Saved to \"$listName\" · added to rotation", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Saved to \"$listName\" (download failed)", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            _downloadingIds.update { it - key }
                        }
                    } else {
                        Toast.makeText(ctx, "Saved to \"$listName\"", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, "Saved to \"$listName\"", Toast.LENGTH_SHORT).show()
                }
            } else {
                _sessionSaved.update { it - 1 }
                Toast.makeText(ctx, "Already in \"$listName\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeFromGrid(wp: BrainrotWallpaper) {
        _batchSelected.update { it - wp.id }
        _gridItems.update { items ->
            items.filter { it.source != wp.source || it.id != wp.id }
        }
    }

    fun setSelectedList(listId: String) {
        _selectedListId.update { listId }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val list = localLists.createList(name) ?: return@launch
            _selectedListId.update { list.id }
        }
    }

    fun toggleBatchSelect(id: String) {
        _batchSelected.update { selected ->
            if (id in selected) selected - id else selected + id
        }
    }

    fun clearBatchSelection() {
        _batchSelected.update { emptySet() }
    }

    fun saveBatchToList(listId: String) {
        val selectedIds = _batchSelected.value
        if (selectedIds.isEmpty()) return
        _gridItems.value
            .filter { it.id in selectedIds }
            .forEach { addToList(listId, it) }
        clearBatchSelection()
    }

    fun retry() {
        loadMore(reset = true)
    }

    fun testSource(sourceId: String) {
        val source = sources.value.firstOrNull { sourceKey(it) == sourceId } ?: return
        viewModelScope.launch {
            markSourceTesting(source, true)
            try {
                val nsfw = prefs.nsfwMode.first()
                val filters = prefs.brainrotFilters.first()
                val explicitQuery = _searchQuery.value
                val request = buildDiscoverRequests(nsfw, filters, explicitQuery)
                    .firstOrNull { sourceKey(it.source) == sourceId }
                if (request == null) {
                    recordSourceError(source, "Source is unavailable with the current settings")
                    return@launch
                }
                fetchSingleForSource(
                    source = request.source,
                    query = request.queries.firstOrNull().orEmpty(),
                    exclude = emptyList(),
                    nsfw = nsfw,
                    filters = filters,
                )
            } finally {
                markSourceTesting(source, false)
            }
        }
    }

    fun toggleGridMode() {
        _gridMode.update { !it }
    }

    fun surpriseMe() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.update { true }
            try {
                val nsfw = prefs.nsfwMode.first()
                val filters = prefs.brainrotFilters.first()
                val blacklist = prefs.globalBlacklist.first()
                val blockedUrls = prefs.blockedUrls.first()
                val explicitQuery = _searchQuery.value
                val requests = buildDiscoverRequests(nsfw, filters, explicitQuery)
                if (requests.isEmpty()) return@launch

                val seenKeys = displayedKeys.toSet()
                val ctx = getApplication<Application>().applicationContext
                val freshItems = requests.map { request ->
                    async(Dispatchers.IO) {
                        val sourceKey = request.source.pluginId.lowercase()
                        val excludes = seenKeys
                            .filter { it.startsWith("$sourceKey:") }
                            .map { it.removePrefix("$sourceKey:") }
                        request.queries.firstNotNullOfOrNull { query ->
                            fetchSingleForSource(request.source, query, excludes, request.effectiveNsfw, filters)
                        }
                    }
                }.awaitAll()
                    .filterNotNull()
                    .filter { wp ->
                        val key = "${wp.source}:${wp.id}"
                        key !in seenKeys &&
                            (blacklist.isEmpty() || wp.tags.none { it.lowercase() in blacklist }) &&
                            (blockedUrls.isEmpty() || wp.fullUrl !in blockedUrls)
                    }
                    .distinctBy { "${it.source}:${it.id}" }

                if (freshItems.isEmpty()) return@launch

                freshItems.forEach { wp ->
                    displayedKeys.add("${wp.source}:${wp.id}")
                    val url = wp.sampleUrl.ifBlank { wp.fullUrl }
                    if (url.isNotBlank()) {
                        ctx.imageLoader.enqueue(
                            ImageRequest.Builder(ctx).data(url).memoryCacheKey(url).diskCacheKey(url).build()
                        )
                    }
                }
                _gridItems.update { freshItems + it }
                _noResults.update { false }
                _noResultsReason.update { null }
                _endReached.update { false }
                pageCache.clear()
                prefs.addSeenWallpaperKeys(freshItems.map { "${it.source}:${it.id}" }.toSet())
            } finally {
                _busy.update { false }
            }
        }
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
            if (value == AspectRatio.MY_PHONE) {
                val metrics = getApplication<Application>().resources.displayMetrics
                val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
                val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
                // Normalize to base-9 so Wallhaven gets a clean ratio (e.g. 9x20 for a Pixel)
                val normalizedH = (9.0 * longSide / shortSide).roundToInt()
                prefs.setPhoneRatio(9, normalizedH)
                prefs.setPhoneScreen(shortSide, longSide)
            }
            prefs.setAspectRatio(value)
            loadMore(reset = true)
        }
    }

    fun setHandsFreeInterval(secs: Int) {
        viewModelScope.launch { prefs.setHandsFreeInterval(secs) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWifiOnlyDiscover(enabled)
            loadMore(reset = true)
        }
    }

    fun setUseMalFilter(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setUseMalFilter(enabled)
            loadMore(reset = true)
        }
    }

    /**
     * Silently fetches and caches the user's MAL list so buildDiscoverRequests can use
     * it for seeding content. Does NOT set the search query — the search bar stays blank.
     */
    private suspend fun refreshMalCacheIfEnabled() {
        if (!prefs.brainrotFilters.first().useMalFilter) return
        if (malPrefs.accessToken.first().isBlank()) return
        val entries = malRepo.fetchAnimeList().getOrNull() ?: return
        if (entries.isNotEmpty()) malPrefs.setAnimeEntries(entries)
    }

    fun setGlobalBlacklist(tags: Set<String>) {
        viewModelScope.launch {
            prefs.setGlobalBlacklist(tags)
            loadMore(reset = true)
        }
    }

    fun toggleSource(source: LocalSource) {
        viewModelScope.launch {
            localSources.update(source.pluginId, source.instanceId, enabled = !source.enabled)
            loadMore(reset = true)
        }
    }

    fun setSourceNsfw(sourceId: String, instanceId: String, nsfwEnabled: Boolean?) {
        viewModelScope.launch {
            localSources.updateSourceNsfw(sourceId, instanceId, nsfwEnabled)
            loadMore(reset = true)
        }
    }

    fun pinCurrentSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch { prefs.addPinnedSearch(q) }
    }

    fun unpinSearch(query: String) {
        viewModelScope.launch { prefs.removePinnedSearch(query) }
    }

    fun forceLoadMore() {
        pageCache.clear()
        consecutiveEmptyFetches = 0
        _endReached.update { false }
        loadMore(reset = false)
    }

    fun fetchTagSuggestions(query: String) {
        val token = query.trimEnd().substringAfterLast(' ').trim()
        if (token.length < 2) {
            clearTagSuggestions()
            return
        }
        tagSuggestionsJob?.cancel()
        tagSuggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
                val url = "https://danbooru.donmai.us/tags.json?search[name_matches]=${encodedToken}*&search[order]=count&limit=8"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Rotato/1.0")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        _tagSuggestions.update { emptyList() }
                        return@use
                    }
                    val arr = JSONArray(resp.body?.string().orEmpty())
                    val tags = (0 until arr.length()).mapNotNull { index ->
                        arr.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                    }
                    _tagSuggestions.update { tags }
                }
            } catch (_: Exception) {
                _tagSuggestions.update { emptyList() }
            }
        }
    }

    fun clearTagSuggestions() {
        tagSuggestionsJob?.cancel()
        _tagSuggestions.update { emptyList() }
    }

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.update { query }
        clearTagSuggestions()
        loadMore(reset = true)
    }

    fun searchByTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _searchQuery.value
        setSearchQuery(trimmed)
        if (current == trimmed) {
            loadMore(reset = true)
        }
    }

    fun downloadToRotation(wp: BrainrotWallpaper) {
        val key = wp.id
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            try {
                val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
                val ok = feedRepo.downloadWallpaper(sourceId, wp.fullUrl, wp.sampleUrl)
                val ctx = getApplication<Application>().applicationContext
                if (ok) {
                    val history = historyFromJson(prefs.historyJson.first()).toMutableList()
                    history.add(0, WallpaperHistoryItem(
                        thumbUrl = wp.thumbUrl,
                        sampleUrl = wp.sampleUrl,
                        fullUrl = wp.fullUrl,
                        source = wp.source,
                        timestamp = System.currentTimeMillis(),
                        tags = wp.tags,
                        pageUrl = wp.pageUrl
                    ))
                    prefs.setHistoryJson(history.take(50).toJson())
                    val rotationList = localLists.lists.first().firstOrNull { it.useAsRotation }
                    if (rotationList != null) localLists.addWallpaper(rotationList.id, wp)
                    val msg = if (rotationList != null) "Added to rotation · saved to \"${rotationList.name}\"" else "Added to rotation"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _downloadingIds.update { it - key }
            }
        }
    }

    fun saveToGallery(wp: BrainrotWallpaper) {
        val key = "gallery:${wp.id}"
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            try {
                val ctx = getApplication<Application>().applicationContext
                val sourceId = wp.fullUrl.substringAfterLast('/').substringBeforeLast('.')
                val ok = feedRepo.saveToGallery(ctx, sourceId, wp.fullUrl, wp.sampleUrl)
                Toast.makeText(ctx, if (ok) "Saved to Pictures/Rotato" else "Save failed", Toast.LENGTH_SHORT).show()
            } finally {
                _downloadingIds.update { it - key }
            }
        }
    }
}
