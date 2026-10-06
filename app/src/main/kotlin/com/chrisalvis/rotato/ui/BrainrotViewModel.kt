package com.chrisalvis.rotato.ui

import android.app.Application
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.chrisalvis.rotato.data.fitWallpaperBitmap
import com.chrisalvis.rotato.data.setWallpaperBitmap
import com.chrisalvis.rotato.data.wallpaperTargetSize
import com.chrisalvis.rotato.data.AppErrorLog
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
import com.chrisalvis.rotato.data.TagTier
import com.chrisalvis.rotato.data.TastePreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.normalizeTag
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginManifest
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.http
import com.chrisalvis.rotato.data.plugins.normalizeBooruQuery
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val tastePrefs = TastePreferences(app)
    private val learnedTaste = com.chrisalvis.rotato.data.LearnedTaste(app)
    private val foldCanvasForRanking: com.chrisalvis.rotato.data.WallpaperCanvas? =
        if (com.chrisalvis.rotato.data.isFoldable(app)) com.chrisalvis.rotato.data.wallpaperTargetSize(app) else null

    val forYouEnabled: StateFlow<Boolean> = learnedTaste.forYouEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setForYouEnabled(enabled: Boolean) {
        viewModelScope.launch {
            learnedTaste.setForYouEnabled(enabled)
            loadMore(reset = true)
        }
    }

    fun resetLearnedTaste() {
        viewModelScope.launch { learnedTaste.reset() }
    }

    private fun learn(wp: BrainrotWallpaper, signal: com.chrisalvis.rotato.data.LearnedTaste.Signal) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { learnedTaste.record(wp.tags, signal) } }
    }
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

    val lists: StateFlow<List<LocalList>> = combine(
        localLists.lists,
        (app as com.chrisalvis.rotato.RotatoApp).unlockedListIds
    ) { all, unlocked ->
        all.filter { !it.isLocked || it.id in unlocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Only collections the user can currently see count as "saved", so a locked collection's
    // contents don't show up as saved badges in Discover.
    val savedSourceIds: StateFlow<Set<String>> = combine(localLists.allWallpapers, lists) { entries, visible ->
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        entries.filter { it.listId in visibleIds }.mapTo(HashSet()) { "${it.source}:${it.sourceId}" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** True when this phone can show a different wallpaper on each Fold screen. */
    val canFoldPair: Boolean = com.chrisalvis.rotato.data.supportsFoldPairs(app)

    private val _foldPairOuter = MutableStateFlow<BrainrotWallpaper?>(null)
    /** Image picked for the outer screen while the user looks for an inner one. */
    val foldPairOuter: StateFlow<BrainrotWallpaper?> = _foldPairOuter.asStateFlow()
    private val _foldPairBusy = MutableStateFlow(false)
    val foldPairBusy: StateFlow<Boolean> = _foldPairBusy.asStateFlow()

    fun pickFoldOuter(wp: BrainrotWallpaper?) { _foldPairOuter.value = wp }

    /**
     * Downloads both images into the Library, saves them as a fold pair and sets it right away:
     * [outer] on the cover screen, [inner] on the big screen.
     */
    fun saveFoldPair(outer: BrainrotWallpaper, inner: BrainrotWallpaper) {
        if (_foldPairBusy.value) return
        viewModelScope.launch {
            _foldPairBusy.value = true
            val ctx = getApplication<Application>().applicationContext
            try {
                val dir = File(ctx.filesDir, "rotato_images")
                val files = listOf(outer, inner).map { wp ->
                    feedRepo.downloadWallpaper(wp.id, wp.fullUrl, wp.sampleUrl, source = wp.source)
                        ?.also { if (wp.isNsfw) prefs.setFileNsfw(it, true) }
                        ?.let { File(dir, it) }
                }
                val (outerFile, innerFile) = files
                if (outerFile == null || innerFile == null) {
                    Toast.makeText(ctx, "Couldn't download both images", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                prefs.saveFoldPair(com.chrisalvis.rotato.data.FoldPair(outerFile.absolutePath, innerFile.absolutePath))
                val err = withContext(Dispatchers.IO) { com.chrisalvis.rotato.data.applyWallpaperFile(ctx, outerFile) }
                _foldPairOuter.value = null
                Toast.makeText(ctx, err ?: "Fold pair set · saved to Library", Toast.LENGTH_SHORT).show()
            } finally {
                _foldPairBusy.value = false
            }
        }
    }

    /** "source:id" → the visible collections that already hold that image, for the list rail. */
    val savedListIds: StateFlow<Map<String, Set<String>>> = combine(localLists.allWallpapers, lists) { entries, visible ->
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        entries.asSequence()
            .filter { it.listId in visibleIds }
            .groupBy({ "${it.source}:${it.sourceId}" }, { it.listId })
            .mapValues { it.value.toSet() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Locked collections currently hidden from Discover's save menus. */
    val lockedHiddenCount: StateFlow<Int> = combine(
        localLists.lists,
        (app as com.chrisalvis.rotato.RotatoApp).unlockedListIds,
        app.nsfwHidden
    ) { all, unlocked, nsfwHidden ->
        // With the content filter on, locked collections aren't mentioned anywhere.
        if (nsfwHidden) 0 else all.count { it.isLocked && it.id !in unlocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Unlock every locked collection for this session (call after the user authenticates). */
    fun unlockLockedLists() {
        if (getApplication<com.chrisalvis.rotato.RotatoApp>().nsfwHidden.value) return
        viewModelScope.launch {
            val locked = localLists.lists.first().filter { it.isLocked }.mapTo(HashSet()) { it.id }
            getApplication<com.chrisalvis.rotato.RotatoApp>().unlockedListIds.update { it + locked }
        }
    }

    /**
     * Rail toggle: adds [wp] to [listId] without leaving the image (so it can go into several
     * collections), or takes it back out if it's already there.
     */
    fun toggleInList(listId: String, wp: BrainrotWallpaper) {
        val inList = listId in savedListIds.value["${wp.source}:${wp.id}"].orEmpty()
        if (!inList) {
            addToList(listId, wp, leaveGrid = false)
            return
        }
        viewModelScope.launch {
            val ids = localLists.allWallpapers.first()
                .filter { it.listId == listId && it.source == wp.source && it.sourceId == wp.id }
                .mapTo(HashSet()) { it.id }
            localLists.removeWallpapers(ids)
            val name = lists.value.find { it.id == listId }?.name ?: "list"
            Toast.makeText(getApplication<Application>(), "Removed from \"$name\"", Toast.LENGTH_SHORT).show()
        }
    }

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val nsfwMode: StateFlow<Boolean> = prefs.nsfwMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val videoPreviewMode: StateFlow<com.chrisalvis.rotato.data.VideoPreviewMode> = prefs.settings
        .map { it.videoPreviewMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.chrisalvis.rotato.data.VideoPreviewMode.AUTOPLAY)

    val nsfwBlurEnabled: StateFlow<Boolean> = prefs.nsfwBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    val interestAlignEnabled: StateFlow<Boolean> = prefs.interestAlignEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val interestProfiles: StateFlow<List<com.chrisalvis.rotato.data.InterestProfile>> = tastePrefs.interestProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setInterestAlignEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setInterestAlignEnabled(enabled)
            loadMore(reset = true)
        }
    }

    fun toggleDiscoverProfile(profileId: String) {
        viewModelScope.launch {
            tastePrefs.toggleProfile(profileId)
            if (prefs.interestAlignEnabled.first()) loadMore(reset = true)
        }
    }

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

    /**
     * Stable discover requests for the current session — computed once on first load or after reset
     * so MAL title shuffles and tier boost picks don't change between loadMore calls. Changing
     * queries between calls creates cache misses and causes the 5-second stall on every scroll.
     */
    private var currentDiscoverRequests: List<DiscoverRequest>? = null

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
        // Linking (or unlinking) MAL must rebuild the feed: Discover keeps its queries stable for
        // the session, so without this it kept serving the pre-link general feed.
        viewModelScope.launch {
            malPrefs.animeList
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (prefs.brainrotFilters.first().useMalFilter && !_noSources.value) loadMore(reset = true)
                }
        }
    }

    private suspend fun init() {
        persistentBlockedKeys.clear()
        persistentBlockedKeys.addAll(prefs.blockedImageKeys.first())
        displayedKeys.addAll(persistentBlockedKeys)
        displayedKeys.addAll(prefs.seenWallpaperKeys.first())
        migrateBlacklistToNeverTier()
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

    private suspend fun migrateBlacklistToNeverTier() {
        val blacklist = prefs.globalBlacklist.first()
        if (blacklist.isEmpty()) return
        blacklist.forEach { tag -> tastePrefs.setTagTier(tag.lowercase(), com.chrisalvis.rotato.data.TagTier.NEVER, isNsfw = false) }
        prefs.setGlobalBlacklist(emptySet())
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
            currentDiscoverRequests = null
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
            val filters = withMalAnimeOnly(prefs.brainrotFilters.first())
            val alignInterests = prefs.interestAlignEnabled.first()
            val sfwTierMap = if (alignInterests) tastePrefs.sfwTagTiers.first() else emptyMap()
            // NSFW tier tags only apply when NSFW mode is on
            val nsfwTierMap = if (alignInterests && nsfw) tastePrefs.nsfwTagTiers.first() else emptyMap()
            val combinedTierMap = sfwTierMap + nsfwTierMap
            val neverTagSet = combinedTierMap.filterValues { it == TagTier.NEVER }.keys.map(::normalizeTag).toSet()
            val activeProfiles = if (alignInterests) tastePrefs.interestProfiles.first().filter { it.isActive } else emptyList()
            val profileExcludes = activeProfiles.flatMap { it.excludeTags }.map(::normalizeTag).toSet()
            val profileIncludes = activeProfiles.flatMap { it.includeTags }.map(::normalizeTag).toSet()
            // When align is on but no profiles selected, fall back to tier-based boosting/deprioritizing
            val tierBoostTags = if (alignInterests && activeProfiles.isEmpty())
                combinedTierMap.filterValues { it == TagTier.LOVE || it == TagTier.LIKE }.keys.map(::normalizeTag).toSet()
            else emptySet()
            val tierDemoteTags = if (alignInterests && activeProfiles.isEmpty())
                combinedTierMap.filterValues { it == TagTier.DISLIKE }.keys.map(::normalizeTag).toSet()
            else emptySet()
            val blacklist = prefs.globalBlacklist.first().map(::normalizeTag).toSet() + neverTagSet + profileExcludes
            val blockedUrls = prefs.blockedUrls.first()
            val explicitQuery = _searchQuery.value
            val batchSize = prefs.discoverBatchSize.first()
            val target = if (isInitial) batchSize * 2 else batchSize

            // Reuse stable queries for the session — MAL title shuffles / tier tag picks must not
            // change between loadMore calls or every scroll triggers cache misses + fresh fetches.
            val sourcesWithQueries = currentDiscoverRequests
                ?: buildDiscoverRequests(nsfw, filters, explicitQuery, tierBoostTags).also { currentDiscoverRequests = it }

            // When a strict aspect-ratio filter is active most fetched items will be discarded
            // by the plugin-side matches() check. Fetch more candidates per call to compensate.
            val fetchLimit = if (filters.aspectRatio != AspectRatio.ANY || filters.minResolution != MinResolution.ANY) 200 else 100

            // Steps 1+2 repeat up to 3 rounds: if strict filters drain the caches before the
            // target is reached, clear and fetch again rather than stopping short.
            val newItems = mutableListOf<BrainrotWallpaper>()
            var totalSkipped = 0
            var round = 0
            while (newItems.size < target && round < 3) {
            // Step 1: fetch pages — every (source, query) pair runs in its own coroutine so
            // multiple MAL title queries for the same source don't block each other.
            val seenKeys = displayedKeys.toSet()
            sourcesWithQueries.flatMap { request ->
                val source = request.source
                val sourceKey = source.pluginId.lowercase()
                val excludes = seenKeys
                    .filter { it.startsWith("$sourceKey:") }
                    .map { it.removePrefix("$sourceKey:") }
                request.queries.mapNotNull { q ->
                    val ck = cacheKey(source, q)
                    if (pageCache[ck]?.isNotEmpty() == true) return@mapNotNull null
                    async(Dispatchers.IO) {
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
                if (blacklist.isNotEmpty() && wp.tags.any { normalizeTag(it) in blacklist }) { totalSkipped++; continue }
                if (blockedUrls.isNotEmpty() && wp.fullUrl in blockedUrls) { totalSkipped++; continue }
                if (!nsfw && wp.isNsfw && getApplication<com.chrisalvis.rotato.RotatoApp>().nsfwHidden.value) { totalSkipped++; continue }
                totalSkipped = 0
                displayedKeys.add(key)
                prefetchGridImage(ctx, wp)
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
                val boostTags = profileIncludes.ifEmpty { tierBoostTags }
                val orderedItems = when {
                    boostTags.isNotEmpty() && tierDemoteTags.isNotEmpty() -> {
                        val (boosted, remaining) = newItems.partition { wp -> wp.tags.any { normalizeTag(it) in boostTags } }
                        val (demoted, neutral) = remaining.partition { wp -> wp.tags.any { normalizeTag(it) in tierDemoteTags } }
                        boosted + neutral + demoted
                    }
                    boostTags.isNotEmpty() -> {
                        val (boosted, rest) = newItems.partition { wp -> wp.tags.any { normalizeTag(it) in boostTags } }
                        boosted + rest
                    }
                    tierDemoteTags.isNotEmpty() -> {
                        val (demoted, rest) = newItems.partition { wp -> wp.tags.any { normalizeTag(it) in tierDemoteTags } }
                        rest + demoted
                    }
                    else -> newItems
                }.let { ordered ->
                    // "For you": rank each batch by learned taste and how well it'll work as a
                    // wallpaper here, with a little jitter so the feed doesn't feel sorted.
                    if (!learnedTaste.forYouEnabled.first() || explicitQuery.isNotBlank()) ordered
                    else {
                        val weights = learnedTaste.weights.first()
                        val rank = ordered.withIndex().associate { (i, wp) -> wp to -i * 0.02f }
                        ordered.sortedByDescending { wp ->
                            com.chrisalvis.rotato.data.LearnedTaste.score(wp, weights, foldCanvasForRanking) +
                                (rank[wp] ?: 0f) + kotlin.random.Random.nextFloat() * 0.8f
                        }
                    }
                }
                _gridItems.update { it + orderedItems }  // single batch update → one recomposition
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

    /** MAL-driven feed (linked list, no manual search) → anime category only on general sources. */
    private suspend fun withMalAnimeOnly(filters: BrainrotFilters): BrainrotFilters {
        val malDriven = filters.useMalFilter &&
            _searchQuery.value.isBlank() &&
            malPrefs.animeList.first().isNotEmpty()
        return if (malDriven) filters.copy(animeOnly = true) else filters
    }

    private fun cacheKey(source: LocalSource, query: String) = "${source.pluginId}:${source.instanceId}:$query"

    private fun queriesFor(source: LocalSource, explicitQuery: String, malTitles: List<String>, tierBoostTags: Set<String> = emptySet()): List<String> = when {
        explicitQuery.isNotBlank() -> listOf(explicitQuery)
        source.tags.isNotBlank() -> listOf(source.tags)
        // Tier boost tags drive preferred queries; MAL titles (if available) or a general ""
        // query fill the rest so the feed isn't exclusively restricted to tier tags. The reorder
        // step surfaces tier-matched content to the top of each batch.
        tierBoostTags.isNotEmpty() -> {
            val filler = if (malTitles.isNotEmpty())
                malTitles.shuffled().take(2).map { normalizeBooruQuery(it) }
            else
                listOf("")
            (tierBoostTags.toList().shuffled().take(3) + filler).distinct()
        }
        // MAL titles drive preferred queries; "" fills the rest so the feed doesn't run dry
        // if the session's random MAL draw lands on niche titles with few booru images.
        malTitles.isNotEmpty() -> (malTitles.shuffled().take(5).map { normalizeBooruQuery(it) } + listOf("")).distinct()
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
        tierBoostTags: Set<String> = emptySet(),
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
                // The global toggle is a ceiling: a per-source override can force a source to stay
                // SFW even in NSFW mode, but can never re-enable NSFW for a source once the global
                // toggle is off — otherwise a stale per-source override outlives the user turning
                // NSFW off globally, with no visible way to notice or undo it.
                val effectiveNsfw = nsfw && source.nsfwEnabled != false
                if (!PluginExecutor.canServe(manifest, effectiveNsfw, source)) return@mapNotNull null
                val rawQueries = queriesFor(source, explicitQuery, malTitles, tierBoostTags)
                val queries = if (manifest.maxTagCount == Int.MAX_VALUE) rawQueries
                    else rawQueries.filter { q ->
                        q.trim().split(Regex("\\s+")).count { it.isNotBlank() } <= manifest.maxTagCount
                    }
                if (queries.isEmpty()) return@mapNotNull null
                DiscoverRequest(
                    source = source,
                    queries = queries,
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
        AppErrorLog.log(source.pluginId, message)
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
            if (e is CancellationException) throw e
            recordSourceError(source, e.message ?: "Unknown error")
            Log.e("DiscoverFetch", "${source.pluginId} q=$query exception: ${e.message}", e)
            AppErrorLog.log("DiscoverFetch", "${source.pluginId} q=$query", e)
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
            if (e is CancellationException) throw e
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

    /** [closeViewer] false keeps the viewer open, which then lands on the next image. */
    fun skip(wp: BrainrotWallpaper, closeViewer: Boolean = true) {
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.SKIPPED)
        if (undoStack.size >= 3) undoStack.removeFirst()
        undoStack.addLast(wp)
        undoIndex["${wp.source}:${wp.id}"] = _gridItems.value.indexOfFirst { it.source == wp.source && it.id == wp.id }
        _skipEvent.tryEmit(Unit)
        removeFromGrid(wp)
        if (closeViewer) _selectedItem.update { null }
    }

    /** Block a URL permanently and remove the wallpaper from the current grid. */
    fun blockAndRemove(wp: BrainrotWallpaper) {
        blockImage(wp)
        viewModelScope.launch { prefs.blockUrl(wp.fullUrl) }
        _selectedItem.update { null }
    }

    fun blockImage(wp: BrainrotWallpaper) {
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.BLOCKED)
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

    // Where each skipped image sat, so undo puts it back in place (the viewer stays on it).
    private val undoIndex = HashMap<String, Int>()

    /** Puts the last skipped image back where it was; returns it. */
    fun undo(): BrainrotWallpaper? {
        val wp = undoStack.removeLastOrNull() ?: return null
        val key = "${wp.source}:${wp.id}"
        displayedKeys.remove(key)
        val at = undoIndex.remove(key) ?: 0
        _gridItems.update { items -> items.toMutableList().apply { add(at.coerceIn(0, size), wp) } }
        return wp
    }

    fun setWallpaperDirectly(wp: BrainrotWallpaper) {
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.SET_WALLPAPER)
        if (wp.isVideo) {
            setVideoWallpaper(wp)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            // Toasts must be shown from the main thread; this coroutine runs on IO.
            suspend fun toast(msg: String) = withContext(Dispatchers.Main) {
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
            val target = wallpaperTargetSize(app)
            val request = ImageRequest.Builder(app)
                .data(wp.fullUrl.ifBlank { wp.thumbUrl })
                .allowHardware(false)
                // Decode near wallpaper resolution instead of the original, which can be huge.
                .size(target.width, target.height)
                .scale(Scale.FILL)
                .build()
            val result = app.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.let {
                (it as? BitmapDrawable)?.bitmap
            }
            if (bitmap == null) {
                toast("Failed to load image")
                return@launch
            }
            try {
                val settings = prefs.settings.first()
                val wm = WallpaperManager.getInstance(app)
                val effectiveTarget = if (wp.isNsfw && prefs.nsfwHomeOnly.first()) WallpaperTarget.HOME_ONLY else settings.wallpaperTarget
                val flags = when (effectiveTarget) {
                    WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                // The source bitmap belongs to Coil's memory cache, so only the copy is recycled.
                val screenBitmap = fitWallpaperBitmap(bitmap, settings.wallpaperFit, target)
                try {
                    setWallpaperBitmap(app, wm, screenBitmap, flags, settings.wallpaperFit, settings.wallpaperEffects)
                } finally {
                    screenBitmap.recycle()
                }
                prefs.recordWallpaperShown(
                    WallpaperHistoryItem(
                        thumbUrl = wp.thumbUrl, sampleUrl = wp.sampleUrl, fullUrl = wp.fullUrl,
                        source = wp.source, timestamp = System.currentTimeMillis(),
                        tags = wp.tags, pageUrl = wp.pageUrl,
                    )
                )
                toast("Wallpaper set!")
            } catch (e: Exception) {
                toast("Failed to set wallpaper")
            }
        }
    }

    /** Videos play through Rotato's live wallpaper, so they need it switched on first. */
    private fun setVideoWallpaper(wp: BrainrotWallpaper) {
        val app = getApplication<Application>()
        if (!com.chrisalvis.rotato.live.LiveWallpaper.isActive(app)) {
            Toast.makeText(app, "Turn on the Rotato live wallpaper (Settings › Rotation) to use videos", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(app, "Downloading video…", Toast.LENGTH_SHORT).show()
        viewModelScope.launch(Dispatchers.IO) {
            val url = wp.fullUrl.ifBlank { wp.sampleUrl }
            val ext = url.substringBefore('?').substringAfterLast('.', "mp4").lowercase().takeIf { it.length in 2..4 } ?: "mp4"
            val dir = File(app.filesDir, "live").apply { mkdirs() }
            val file = File(dir, "video_${System.currentTimeMillis()}.$ext")
            val ok = try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                    .apply { if (wp.pageUrl.isNotBlank()) header("Referer", wp.pageUrl) }
                    .build()
                com.chrisalvis.rotato.data.FeedRepository.httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body
                    if (!resp.isSuccessful || body == null) false
                    else { file.outputStream().use { body.byteStream().copyTo(it) }; file.length() > 0 }
                }
            } catch (e: Exception) {
                false
            }
            if (ok) {
                // Keep just this video; older ones are no longer on screen.
                dir.listFiles { f -> f.name.startsWith("video_") && f != file }?.forEach { it.delete() }
                com.chrisalvis.rotato.live.LiveWallpaper.showVideo(app, file)
                prefs.recordWallpaperShown(
                    WallpaperHistoryItem(
                        thumbUrl = wp.thumbUrl, sampleUrl = wp.sampleUrl, fullUrl = wp.fullUrl,
                        source = wp.source, timestamp = System.currentTimeMillis(),
                        tags = wp.tags, pageUrl = wp.pageUrl,
                    )
                )
            } else {
                file.delete()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, if (ok) "Video wallpaper set!" else "Couldn't download the video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToList(listId: String, wp: BrainrotWallpaper, leaveGrid: Boolean = true) {
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.SAVED)
        _selectedListId.update { listId }
        if (leaveGrid) removeFromGrid(wp)
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
                            // Same file name the collection entry resolves to (poolKey(source, sourceId)),
                            // so the pool, per-screen and stealth lookups find it and it isn't fetched twice.
                            val downloadedFile = feedRepo.downloadWallpaper(wp.id, wp.fullUrl, wp.sampleUrl, source = wp.source)
                            if (downloadedFile != null) {
                                if (wp.isNsfw) prefs.setFileNsfw(downloadedFile, true)
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
                val filters = withMalAnimeOnly(prefs.brainrotFilters.first())
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
                val filters = withMalAnimeOnly(prefs.brainrotFilters.first())
                val alignInterests = prefs.interestAlignEnabled.first()
                val sfwTierMap = if (alignInterests) tastePrefs.sfwTagTiers.first() else emptyMap()
                val nsfwTierMap = if (alignInterests && nsfw) tastePrefs.nsfwTagTiers.first() else emptyMap()
                val neverTagSet = (sfwTierMap + nsfwTierMap).filterValues { it == TagTier.NEVER }.keys.map(::normalizeTag).toSet()
                val activeProfiles = if (alignInterests) tastePrefs.interestProfiles.first().filter { it.isActive } else emptyList()
                val profileExcludes = activeProfiles.flatMap { it.excludeTags }.map(::normalizeTag).toSet()
                val blacklist = prefs.globalBlacklist.first().map(::normalizeTag).toSet() + neverTagSet + profileExcludes
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
                            (blacklist.isEmpty() || wp.tags.none { normalizeTag(it) in blacklist }) &&
                            (blockedUrls.isEmpty() || wp.fullUrl !in blockedUrls)
                    }
                    .distinctBy { "${it.source}:${it.id}" }

                if (freshItems.isEmpty()) return@launch

                freshItems.forEach { wp ->
                    displayedKeys.add("${wp.source}:${wp.id}")
                    prefetchGridImage(ctx, wp)
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

    val isFoldable: Boolean = com.chrisalvis.rotato.data.isFoldable(app)

    /**
     * Data saver: on a metered connection Discover tiles load the source's small preview
     * instead of the larger sample.
     */
    val dataSaverActive: StateFlow<Boolean> = combine(prefs.discoverDataSaver, meteredFlow(app)) { on, metered -> on && metered }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun meteredFlow(context: Context) = kotlinx.coroutines.flow.callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        trySend(cm.isActiveNetworkMetered)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                trySend(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** One switch for "My Phone" ratio and resolution: images that fill every screen sharply. */
    fun setFoldFriendly(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                applyPhoneShape()
                prefs.setMinResolution(MinResolution.MY_PHONE)
                prefs.setAspectRatio(AspectRatio.MY_PHONE)
            } else {
                prefs.setMinResolution(MinResolution.ANY)
                prefs.setAspectRatio(AspectRatio.ANY)
            }
            loadMore(reset = true)
        }
    }

    private suspend fun applyPhoneShape() {
        // Every screen the device has (both panels on a foldable), not just the current one.
        val app = getApplication<Application>()
        val screens = com.chrisalvis.rotato.data.knownDisplaySizes(app)
        val narrowest = screens.minBy { it.width.toFloat() / it.height }
        val widest = screens.maxBy { it.width.toFloat() / it.height }
        // Normalize to base-9 so Wallhaven gets a clean ratio (e.g. 9x20 for a Pixel)
        val normalizedH = (9.0 * narrowest.height / narrowest.width).roundToInt()
        prefs.setPhoneRatio(9, normalizedH)
        val widestAspect = widest.width.toFloat() / widest.height
        val narrowestAspect = narrowest.width.toFloat() / narrowest.height
        prefs.setPhoneMaxAspect(if (widestAspect > narrowestAspect * 1.05f) widestAspect else 0f)
        val canvas = com.chrisalvis.rotato.data.wallpaperTargetSize(app)
        prefs.setPhoneScreen(canvas.width, canvas.height)
    }

    fun setAspectRatio(value: AspectRatio) {
        viewModelScope.launch {
            if (value == AspectRatio.MY_PHONE) applyPhoneShape()
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

    fun addTagToSearch(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _searchQuery.value.trim()
        setSearchQuery(if (current.isBlank()) trimmed else "$current $trimmed")
    }

    fun addTagToTier(tag: String, tier: TagTier, isNsfw: Boolean) {
        viewModelScope.launch {
            tastePrefs.setTagTier(tag.trim().lowercase(), tier, isNsfw)
            if (tier == TagTier.NEVER) loadMore(reset = true)
        }
    }

    fun downloadToRotation(wp: BrainrotWallpaper) {
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.DOWNLOADED)
        if (wp.isVideo) {
            Toast.makeText(getApplication(), "Videos can't be set as a wallpaper", Toast.LENGTH_SHORT).show()
            return
        }
        val key = wp.id
        if (_downloadingIds.value.contains(key)) return
        viewModelScope.launch {
            _downloadingIds.update { it + key }
            try {
                val downloadedFile = feedRepo.downloadWallpaper(wp.id, wp.fullUrl, wp.sampleUrl, source = wp.source)
                val ctx = getApplication<Application>().applicationContext
                if (downloadedFile != null) {
                    if (wp.isNsfw) prefs.setFileNsfw(downloadedFile, true)
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
        learn(wp, com.chrisalvis.rotato.data.LearnedTaste.Signal.DOWNLOADED)
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

    /**
     * Warms the disk cache with the image the grid tile will show. This used to prefetch
     * sampleUrl (the 4K original for Wallhaven) at full decode size into the memory cache for
     * every item in a batch, which queued dozens of multi-megabyte downloads ahead of the tiles
     * actually on screen and evicted their bitmaps.
     */
    private fun prefetchGridImage(ctx: android.content.Context, wp: BrainrotWallpaper) {
        if (wp.isVideo) return
        val url = (if (dataSaverActive.value) wp.dataSaverUrl else wp.gridUrl).takeIf { it.isNotBlank() } ?: return
        ctx.imageLoader.enqueue(
            ImageRequest.Builder(ctx)
                .data(url)
                .diskCacheKey(url)
                .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                .size(512)
                .build()
        )
    }
}
