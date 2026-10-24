package com.chrisalvis.rotato.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.AutoPauseSettings
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.sanitizeFilename
import com.chrisalvis.rotato.data.poolKey
import com.chrisalvis.rotato.data.poolKeys
import com.chrisalvis.rotato.data.findPoolFile
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.MalPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.RotationError
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.withBackupSettings
import com.chrisalvis.rotato.data.plugins.PluginRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.chrisalvis.rotato.worker.RotatoWidgetProvider
import com.chrisalvis.rotato.worker.WallpaperWorker
import com.chrisalvis.rotato.worker.WallpaperWorker.Companion.CHAIN_WORK_NAME
import com.chrisalvis.rotato.worker.WallpaperWorker.Companion.KEY_INTERVAL_MINUTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.chrisalvis.rotato.data.pairedPaths
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class SetNowState { IDLE, SETTING, DONE, ERROR }
enum class BackupState { IDLE, BUSY, SUCCESS, ERROR }

data class RotationStats(
    val totalRotations: Long = 0L,
    val recentCount: Int = 0,
    val topSources: List<Pair<String, Int>> = emptyList(),
    val topTags: List<Pair<String, Int>> = emptyList(),
    val recap: WeeklyRecap = WeeklyRecap(),
)

/** The last seven days at a glance on the Stats tab. */
data class WeeklyRecap(
    val shownThisWeek: Int = 0,
    /** Image URL (or local path) and how many times it was set this week. */
    val mostShown: List<Pair<String, Int>> = emptyList(),
    val topRated: List<Pair<File, Int>> = emptyList(),
    val newest: List<File> = emptyList(),
) {
    val isEmpty: Boolean get() = shownThisWeek == 0 && topRated.isEmpty() && newest.isEmpty()
}

sealed interface DuplicateScan {
    data object Idle : DuplicateScan
    data object Scanning : DuplicateScan
    data class Done(val groups: List<com.chrisalvis.rotato.data.DuplicateGroup>) : DuplicateScan
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ImageRepository(application)
    private val preferences = RotatoPreferences(application)
    private val workManager = WorkManager.getInstance(application)
    private val localLists = LocalListsPreferences(application)
    private val sourcesPrefs = LocalSourcesPreferences(application)
    private val malPrefs = MalPreferences(application)
    private val pluginRepository = PluginRepository(getApplication())
    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val feedRepo = FeedRepository(imageDir)

    private val _images = MutableStateFlow<List<File>>(emptyList())
    val images: StateFlow<List<File>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _setNowState = MutableStateFlow(SetNowState.IDLE)
    val setNowState: StateFlow<SetNowState> = _setNowState.asStateFlow()

    private val _setNowErrorMessage = MutableStateFlow<String?>(null)
    val setNowErrorMessage: StateFlow<String?> = _setNowErrorMessage.asStateFlow()

    val collections: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<RotatoSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RotatoSettings())

    val lastRotationMs: StateFlow<Long> = preferences.lastRotationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** The wallpaper on screen now (the newest history entry). */
    val nowShowing: StateFlow<com.chrisalvis.rotato.data.WallpaperHistoryItem?> = preferences.historyJson
        .map { historyFromJson(it).maxByOrNull { h -> h.timestamp } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Wallpapers lined up to show next, oldest first; missing files drop out. */
    val upNext: StateFlow<List<File>> = preferences.upNext
        .map { paths -> paths.map(::File).filter { it.exists() } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lines [file] up to show next ([first]) or after everything already queued. */
    fun queueUpNext(file: File, first: Boolean = false) {
        viewModelScope.launch {
            preferences.queueUpNext(file.absolutePath, first)
            android.widget.Toast.makeText(getApplication(), if (first) "Playing next" else "Added to Up next", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Queues [files] after everything already in Up next, keeping their order. */
    fun queueAllUpNext(files: List<File>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            files.forEach { preferences.queueUpNext(it.absolutePath, false) }
            android.widget.Toast.makeText(getApplication(), "Added ${files.size} to Up next", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun setRatingFor(files: Collection<File>, rating: Int) {
        viewModelScope.launch { files.forEach { preferences.setWallpaperRating(it.name, rating) } }
    }

    fun removeFromUpNext(file: File) {
        viewModelScope.launch { preferences.setUpNext(upNext.value.filter { it != file }.map { it.absolutePath }) }
    }

    fun moveUpNext(from: Int, to: Int) {
        val list = upNext.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { preferences.setUpNext(list.map { it.absolutePath }) }
    }

    fun clearUpNext() {
        viewModelScope.launch { preferences.setUpNext(emptyList()) }
    }

    /** Runs a remote-style action (next, previous, save) right away. */
    fun automation(action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.chrisalvis.rotato.worker.AutomationReceiver.handle(getApplication(), android.content.Intent(action))
        }
    }

    /** Never show the current wallpaper again and move on. */
    fun dislikeCurrent() {
        val app = getApplication<Application>()
        app.sendBroadcast(android.content.Intent(app, com.chrisalvis.rotato.worker.DislikeWallpaperReceiver::class.java))
        viewModelScope.launch {
            // Let the receiver block and remove it first, so the next pick can't be the same one.
            kotlinx.coroutines.delay(1_500)
            automation(com.chrisalvis.rotato.worker.AutomationReceiver.ACTION_NEXT)
        }
    }

    val lastSkipReason: StateFlow<String?> = preferences.lastSkipReason
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val wallpaperRatings: StateFlow<Map<String, Int>> = preferences.wallpaperRatings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val nsfwFileNames: StateFlow<Set<String>> = preferences.nsfwFileNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val nsfwBlurEnabled: StateFlow<Boolean> = preferences.nsfwBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val nsfwHidden: StateFlow<Boolean> = preferences.nsfwHidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setNsfwHidden(hidden: Boolean) {
        viewModelScope.launch { preferences.setNsfwHidden(hidden) }
    }

    val nsfwHomeOnly: StateFlow<Boolean> = preferences.nsfwHomeOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val stealthCollectionId: StateFlow<String> = preferences.stealthCollectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val autoPauseSettings: StateFlow<AutoPauseSettings> = preferences.autoPauseSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoPauseSettings())

    val chargingTriggerEnabled: StateFlow<Boolean> = preferences.chargingTriggerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoFavoriteEnabled: StateFlow<Boolean> = preferences.autoFavoriteEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoFavoriteMinutes: StateFlow<Int> = preferences.autoFavoriteMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 120)

    val autoRefillEnabled: StateFlow<Boolean> = preferences.autoRefillEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoRefillMinCount: StateFlow<Int> = preferences.autoRefillMinCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val widgetCollectionId: StateFlow<String> = preferences.widgetCollectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val stats: StateFlow<RotationStats> = combine(
        preferences.totalRotations,
        preferences.historyJson,
        _images,
        preferences.wallpaperRatings,
    ) { total, histJson, images, ratings ->
        val history = historyFromJson(histJson)
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val thisWeek = history.filter { it.timestamp >= weekAgo }
        val recap = WeeklyRecap(
            shownThisWeek = thisWeek.size,
            mostShown = thisWeek
                .groupBy { it.fullUrl.ifBlank { it.thumbUrl } }
                .entries
                .filter { it.value.size > 1 }
                .sortedByDescending { it.value.size }
                .take(5)
                .map { (_, items) -> items.first().thumbUrl.ifBlank { items.first().fullUrl } to items.size },
            topRated = images
                .mapNotNull { file -> ratings[file.name]?.takeIf { it > 0 }?.let { file to it } }
                .sortedByDescending { it.second }
                .take(5),
            newest = images
                .filter { it.lastModified() >= weekAgo }
                .sortedByDescending { it.lastModified() }
                .take(5),
        )
        val topSources = history
            .groupingBy { it.source.ifBlank { "local" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
        val topTags = history
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key to it.value }
        RotationStats(total, history.size, topSources, topTags, recap)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RotationStats())

    val rotationErrors: StateFlow<List<RotationError>> = preferences.rotationErrors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearRotationErrors() {
        viewModelScope.launch { preferences.clearRotationErrors() }
    }

    private suspend fun resetSetNowUi() {
        kotlinx.coroutines.delay(2_000)
        _setNowState.update { SetNowState.IDLE }
        _setNowErrorMessage.update { null }
    }

    init {
        observeImageDir()
        recoverIfNeeded()
        observeRotationCollections()
    }

    /** Pushes rotato_images/ changes into [_images] as files come and go. */
    private fun observeImageDir() {
        viewModelScope.launch {
            repository.mediaFlow().collect { files ->
                _images.update { files }
            }
        }
    }

    fun refreshImages() {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                _images.update { repository.getMedia() }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun refreshFromFeeds() {
        viewModelScope.launch(Dispatchers.IO) {
            _images.update { repository.getMedia() }
        }
    }

    /** Auto-download wallpapers from any collection marked useAsRotation into the Library pool. */
    private fun observeRotationCollections() {
        // Directory scans, file copies and downloads below must stay off the main thread;
        // large rotation collections froze the UI here.
        viewModelScope.launch(Dispatchers.IO) {
            // Also re-runs when an image is un-blocked, so saving it again brings it back.
            kotlinx.coroutines.flow.combine(
                localLists.lists, localLists.allWallpapers, preferences.libraryRemovedKeys.distinctUntilChanged(),
            ) { lists, wallpapers, removed ->
                val rotationIds = lists.filter { it.useAsRotation }.map { it.id }.toSet()
                wallpapers.filter { it.listId in rotationIds } to removed
            }.collect { (toSync, removedKeys) ->
                var changed = false
                val stemsOnDisk = imageDir.listFiles()?.mapTo(HashSet()) { it.nameWithoutExtension } ?: hashSetOf()
                // Images the user deleted from the Library stay deleted (removedKeys).
                toSync.forEach { entry ->
                    if (entry.fullUrl.isBlank()) return@forEach
                    val key = poolKey(entry.source, entry.sourceId)
                    val keys = poolKeys(entry.source, entry.sourceId)
                    if (keys.any { it in removedKeys }) return@forEach
                    val onDisk = keys.any { it in stemsOnDisk }
                    if (!onDisk) {
                        if (entry.source == "device" && entry.fullUrl.startsWith("list_images/")) {
                            // Local image — copy from list_images/ to rotation pool
                            val src = File(getApplication<android.app.Application>().filesDir, entry.fullUrl)
                            if (src.exists()) {
                                try {
                                    val ext = src.extension.ifBlank { "jpg" }
                                    src.copyTo(File(imageDir, "$key.$ext"), overwrite = true)
                                    changed = true
                                } catch (e: java.io.IOException) {
                                    android.util.Log.e("HomeViewModel", "Failed to copy device image to rotation pool: ${entry.fullUrl}", e)
                                }
                            }
                        } else {
                            try {
                                val fileName = feedRepo.downloadWallpaper(entry.sourceId, entry.fullUrl, entry.sampleUrl.ifBlank { entry.thumbUrl }, source = entry.source)
                                if (fileName != null && entry.isNsfw) preferences.setFileNsfw(fileName, true)
                                changed = true
                            } catch (e: Exception) {
                                android.util.Log.e("HomeViewModel", "Failed to download rotation wallpaper: ${entry.fullUrl}", e)
                            }
                        }
                    }
                }
                if (changed) _images.update { repository.getMedia() }
            }
        }
    }

    fun toggleCollectionAsRotation(list: LocalList) {
        viewModelScope.launch {
            // Linking a collection to the Library is an explicit add of everything in it.
            if (!list.useAsRotation) {
                preferences.clearLibraryRemovedKeys(
                    localLists.wallpapersForList(list.id).first().flatMap { poolKeys(it.source, it.sourceId) }
                )
            }
            localLists.setUseAsRotation(list.id, !list.useAsRotation)
        }
    }

    // If rotation was enabled but the work is gone (force stop, reboot killed the chain, etc.)
    // re-enqueue it so the user doesn't have to notice and manually toggle it off and on.
    private fun recoverIfNeeded() {
        viewModelScope.launch {
            val saved = preferences.settings.first()
            if (!saved.isEnabled || repository.getImages().isEmpty()) return@launch

            val intervalMinutes = saved.intervalMinutes.toLong()
            val workName = if (intervalMinutes < 15) CHAIN_WORK_NAME else WORK_NAME

            val isAlive = withContext(Dispatchers.IO) {
                val infos = workManager.getWorkInfosForUniqueWork(workName).get()
                infos.any { it.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) }
            }

            if (!isAlive) {
                scheduleRotation(intervalMinutes)
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                uris.forEach { repository.addImage(it) }
                _images.update { repository.getMedia() }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun removeImage(file: File) = deleteSelected(setOf(file))

    /**
     * Deletes [files] from the Library. Their names are remembered so collections linked to the
     * Library don't download them again, and collection entries that pointed straight at these
     * files ("Save library as collection") go too instead of being left as broken tiles.
     */
    private suspend fun deleteFromLibrary(files: Set<File>) = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext
        preferences.addLibraryRemovedKeys(files.map { it.nameWithoutExtension })
        val uris = files.mapTo(HashSet()) { it.toURI().toString() }
        val stale = localLists.allWallpapers.first()
            .filter { it.source == "device" && it.fullUrl in uris }
            .mapTo(HashSet()) { it.id }
        localLists.removeWallpapers(stale)
        files.forEach { repository.removeImage(it) }
        _images.update { repository.getMedia() }
    }

    fun setRating(file: File, rating: Int) {
        viewModelScope.launch { preferences.setWallpaperRating(file.name, rating) }
    }

    fun setRotationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(enabled)
            if (enabled && _images.value.isNotEmpty()) {
                scheduleRotation(settings.value.intervalMinutes.toLong())
            } else {
                cancelRotation()
            }
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setIntervalMinutes(minutes)
            if (settings.value.isEnabled) {
                scheduleRotation(minutes.toLong())
            }
        }
    }

    fun setShuffleMode(shuffle: Boolean) {
        viewModelScope.launch {
            preferences.setShuffleMode(shuffle)
        }
    }

    fun setWallpaperTarget(target: com.chrisalvis.rotato.data.WallpaperTarget) {
        viewModelScope.launch {
            preferences.setWallpaperTarget(target)
        }
    }

    fun setWallpaperFit(fit: com.chrisalvis.rotato.data.WallpaperFit) {
        viewModelScope.launch { preferences.setWallpaperFit(fit) }
    }

    fun setWallpaperEffects(effects: com.chrisalvis.rotato.data.WallpaperEffects) {
        viewModelScope.launch { preferences.setWallpaperEffects(effects) }
    }

    val hasPreviousWallpaper: StateFlow<Boolean> = preferences.hasPreviousWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Goes back to the wallpaper that was showing before the current one. */
    fun setPreviousWallpaper() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val path = preferences.popPreviousWallpaper { File(it).exists() }
            if (path == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "No earlier wallpaper to go back to", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            setSpecificWallpaper(File(path), recordAsCurrent = false)
        }
    }

    fun setVideoPreviewMode(mode: com.chrisalvis.rotato.data.VideoPreviewMode) {
        viewModelScope.launch { preferences.setVideoPreviewMode(mode) }
    }

    fun setNsfwBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNsfwBlurEnabled(enabled) }
    }

    fun setNsfwHomeOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setNsfwHomeOnly(enabled) }
    }

    fun setStealthCollectionId(id: String) {
        viewModelScope.launch { preferences.setStealthCollectionId(id) }
    }

    fun setWidgetCollectionId(listId: String) {
        viewModelScope.launch {
            preferences.setWidgetCollectionId(listId)
            RotatoWidgetProvider.refreshAll(getApplication())
        }
    }

    fun setAutoPauseNight(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoPauseNight(enabled) }
    }

    fun setAutoPauseNightHours(start: Int, end: Int) {
        viewModelScope.launch { preferences.setAutoPauseNightHours(start, end) }
    }

    fun setAutoPauseCharging(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoPauseCharging(enabled) }
    }

    fun setRotateScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferences.setRotateScreenOn(enabled) }
    }

    fun setChargingTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setChargingTriggerEnabled(enabled) }
    }

    fun setAutoFavoriteEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoFavoriteEnabled(enabled) }
    }

    fun setAutoFavoriteMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setAutoFavoriteMinutes(minutes) }
    }

    fun setAutoRefillEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoRefillEnabled(enabled) }
    }

    fun setAutoRefillMinCount(count: Int) {
        viewModelScope.launch { preferences.setAutoRefillMinCount(count) }
    }

    val discoverBatchSize: StateFlow<Int> = preferences.discoverBatchSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20)

    fun setDiscoverBatchSize(size: Int) {
        viewModelScope.launch { preferences.setDiscoverBatchSize(size) }
    }

    val wifiOnlyDiscover: StateFlow<Boolean> = preferences.wifiOnlyDiscover
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWifiOnlyDiscover(enabled: Boolean) {
        viewModelScope.launch { preferences.setWifiOnlyDiscover(enabled) }
    }

    val discoverDataSaver: StateFlow<Boolean> = preferences.discoverDataSaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDiscoverDataSaver(enabled: Boolean) {
        viewModelScope.launch { preferences.setDiscoverDataSaver(enabled) }
    }

    val isFoldable: Boolean = com.chrisalvis.rotato.data.isFoldable(application)

    val matchTimeOfDay: StateFlow<Boolean> = preferences.matchTimeOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMatchTimeOfDay(enabled: Boolean) {
        viewModelScope.launch { preferences.setMatchTimeOfDay(enabled) }
    }

    val enhanceLowRes: StateFlow<Boolean> = preferences.enhanceLowRes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setEnhanceLowRes(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnhanceLowRes(enabled) }
    }

    val rotateOnUnfold: StateFlow<Boolean> = preferences.rotateOnUnfold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRotateOnUnfold(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setRotateOnUnfold(enabled)
            com.chrisalvis.rotato.worker.UnfoldWatcherService.sync(getApplication(), enabled)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            cancelRotation()
            preferences.setEnabled(false)
            repository.clearAll()
            _images.update { emptyList() }
        }
    }

    private val _duplicateScan = MutableStateFlow<DuplicateScan>(DuplicateScan.Idle)
    val duplicateScan: StateFlow<DuplicateScan> = _duplicateScan.asStateFlow()

    fun findDuplicates() {
        if (_duplicateScan.value == DuplicateScan.Scanning) return
        viewModelScope.launch {
            _duplicateScan.update { DuplicateScan.Scanning }
            val groups = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.chrisalvis.rotato.data.findDuplicateImages(_images.value.filterNot { com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(it) })
            }
            _duplicateScan.update { DuplicateScan.Done(groups) }
        }
    }

    fun dismissDuplicates() {
        _duplicateScan.update { DuplicateScan.Idle }
    }

    fun removeDuplicates(groups: List<com.chrisalvis.rotato.data.DuplicateGroup>) {
        deleteSelected(groups.flatMap { it.duplicates }.toSet())
        _duplicateScan.update { DuplicateScan.Idle }
    }

    val foldPairsSupported: Boolean = com.chrisalvis.rotato.data.supportsFoldPairs(application)

    val pairedPaths: StateFlow<Set<String>> = preferences.foldPairs
        .map { it.pairedPaths() }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Pairs [outer] (cover screen) with [inner] (unfolded screen) and shows them right away. */
    fun saveFoldPair(outer: File, inner: File) {
        viewModelScope.launch {
            preferences.saveFoldPair(com.chrisalvis.rotato.data.FoldPair(outer.absolutePath, inner.absolutePath))
            setSpecificWallpaper(outer)
        }
    }

    fun unpair(files: Set<File>) {
        viewModelScope.launch { preferences.removeFoldPairsContaining(files.map { it.absolutePath }.toSet()) }
    }

    fun deleteSelected(files: Set<File>) {
        viewModelScope.launch {
            deleteFromLibrary(files)
            // Auto-disable rotation if the pool is now empty
            if (_images.value.isEmpty() && settings.value.isEnabled) {
                setRotationEnabled(false)
            }
        }
    }

    private val _saveToListInProgress = MutableStateFlow(false)
    val saveToListInProgress: StateFlow<Boolean> = _saveToListInProgress.asStateFlow()

    /** Adds the whole Library to a collection, or just [only] when given (the selection). */
    fun saveRotationToList(listId: String, only: Collection<File>? = null) {
        if (_saveToListInProgress.value) return
        viewModelScope.launch {
            _saveToListInProgress.update { true }
            try {
                val files = only?.sortedBy { it.name } ?: imageDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                val existing = localLists.allWallpapers.first()
                    .filter { it.listId == listId }
                    .map { it.sourceId }
                    .toSet()
                val nsfwNames = preferences.nsfwFileNames.first()
                var added = 0
                files.forEach { file ->
                    val sourceId = file.nameWithoutExtension
                    if (sourceId in existing) return@forEach
                    val fileUri = file.toURI().toString()
                    localLists.addWallpaperEntry(
                        LocalWallpaperEntry(
                            listId = listId,
                            sourceId = sourceId,
                            source = "device",
                            thumbUrl = fileUri,
                            fullUrl = fileUri,
                            resolution = "",
                            pageUrl = "",
                            tags = emptyList(),
                            isVideo = com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file),
                            isNsfw = file.name in nsfwNames,
                        )
                    )
                    added++
                }
                val ctx = getApplication<Application>().applicationContext
                val total = files.size
                val msg = when {
                    total == 0 -> "No images in Library"
                    added == 0 -> "All ${total} image${if (total != 1) "s" else ""} already in collection"
                    else -> "Added $added image${if (added != 1) "s" else ""} to collection"
                }
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _saveToListInProgress.update { false }
            }
        }
    }

    fun setWallpaperNow() {
        if (_images.value.isEmpty()) return
        viewModelScope.launch {
            _setNowState.update { SetNowState.SETTING }
            _setNowErrorMessage.update { null }
            try {
                val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
                    .build()
                workManager.enqueueUniqueWork(SET_NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request).await()
                val terminalStates = setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)
                val info = workManager.getWorkInfoByIdFlow(request.id)
                    .first { it?.state in terminalStates }
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _setNowState.update { SetNowState.DONE }
                        resetSetNowUi()
                    }
                    else -> {
                        _setNowErrorMessage.update { preferences.rotationErrors.first().firstOrNull()?.message ?: "Rotation failed" }
                        _setNowState.update { SetNowState.ERROR }
                        resetSetNowUi()
                    }
                }
            } catch (e: Exception) {
                _setNowErrorMessage.update { e.message?.take(60) ?: "Failed" }
                _setNowState.update { SetNowState.ERROR }
                resetSetNowUi()
            }
        }
    }

    fun saveFileToGallery(file: File) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = feedRepo.saveFileToGallery(app, file)
            android.widget.Toast.makeText(
                app,
                when {
                    !ok -> "Save failed"
                    com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file) -> "Saved to Movies/Rotato"
                    else -> "Saved to Pictures/Rotato"
                },
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setSpecificWallpaper(file: File, recordAsCurrent: Boolean = true) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _setNowState.update { SetNowState.SETTING }
            _setNowErrorMessage.update { null }
            try {
                val error = com.chrisalvis.rotato.data.applyWallpaperFile(getApplication(), file, recordAsCurrent)
                if (error != null) {
                    _setNowErrorMessage.update { error }
                    _setNowState.update { SetNowState.ERROR }
                } else {
                    _setNowState.update { SetNowState.DONE }
                    com.chrisalvis.rotato.worker.RotatoWidgetProvider.refreshAll(getApplication())
                }
                resetSetNowUi()
            } catch (e: Exception) {
                _setNowErrorMessage.update { e.message?.take(60) ?: "Unknown error" }
                _setNowState.update { SetNowState.ERROR }
                resetSetNowUi()
            }
        }
    }

    private fun scheduleRotation(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        if (intervalMinutes < 15) {
            // Sub-15-min intervals can't use PeriodicWorkRequest (OS enforces 15-min floor).
            // Use a self-chaining OneTimeWorkRequest instead. Constraints still apply.
            // Cancel any running periodic work so both don't fire simultaneously.
            workManager.cancelUniqueWork(WORK_NAME)
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_INTERVAL_MINUTES to intervalMinutes))
                .build()
            workManager.enqueueUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        } else {
            // Flex period: give the OS a window at the end of each interval to batch our work
            // with other jobs. More likely to execute on time than demanding strict scheduling.
            // Floor at 5 min (WorkManager minimum), cap at 15 min.
            val flexMinutes = (intervalMinutes / 4)
                .coerceAtLeast(PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS / 60_000)
                .coerceAtMost(15L)

            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                flexMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_INTERVAL_MINUTES to intervalMinutes))
                .build()

            // Cancel any running chain work so both don't fire simultaneously.
            workManager.cancelUniqueWork(CHAIN_WORK_NAME)
            // UPDATE (not REPLACE): keeps existing work running if already scheduled,
            // only re-enqueues if the interval or constraints actually changed.
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    private fun cancelRotation() {
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(CHAIN_WORK_NAME)
    }

    // ── Backup / Restore ────────────────────────────────────────────────────────

    val googleDriveBackupEnabled: StateFlow<Boolean> = preferences.googleDriveBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setGoogleDriveBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setGoogleDriveBackupEnabled(enabled) }
    }

    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun exportSettings(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupState.update { BackupState.BUSY }
            runCatching {
                val json = com.chrisalvis.rotato.data.buildBackupJson(getApplication())
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                _backupState.update { BackupState.SUCCESS }
            }.onFailure {
                android.util.Log.e("HomeViewModel", "Export failed", it)
                _backupState.update { BackupState.ERROR }
            }
            kotlinx.coroutines.delay(2_500)
            _backupState.update { BackupState.IDLE }
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupState.update { BackupState.BUSY }
            runCatching {
                val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Could not read file")
                val json = JSONObject(raw)

                // Restore sources
                val sourcesArr = json.optJSONArray("sources")
                if (sourcesArr != null) {
                    for (i in 0 until sourcesArr.length()) {
                        val o = sourcesArr.getJSONObject(i)
                        val pluginId = o.optString("pluginId").ifBlank { o.optString("type") }
                        if (pluginId.isBlank()) continue
                        sourcesPrefs.upsertSource(
                            com.chrisalvis.rotato.data.LocalSource(
                                pluginId = pluginId,
                                instanceId = o.optString("instanceId", ""),
                                baseUrl = o.optString("baseUrl", ""),
                                enabled = o.optBoolean("enabled", false),
                                apiKey = o.optString("apiKey", ""),
                                apiUser = o.optString("apiUser", ""),
                                tags = o.optString("tags", ""),
                                wallhavenPurity = o.optString("wallhavenPurity", "110"),
                                extraConfig = o.optJSONObject("extraConfig")?.let { extra ->
                                    buildMap { extra.keys().forEach { k -> put(k, extra.optString(k)) } }
                                } ?: emptyMap(),
                            )
                        )
                    }
                }

                // Restore preferences
                var importedWidgetCollectionId: String? = null
                val prefsObj = json.optJSONObject("preferences")
                if (prefsObj != null) {
                    preferences.setIntervalMinutes(prefsObj.optInt("intervalMinutes", 60))
                    preferences.setShuffleMode(prefsObj.optBoolean("shuffleMode", true))
                    runCatching { WallpaperTarget.valueOf(prefsObj.optString("wallpaperTarget", "BOTH")) }
                        .getOrNull()?.let { preferences.setWallpaperTarget(it) }
                    preferences.setNsfwMode(prefsObj.optBoolean("nsfwMode", false))
                    if (prefsObj.has("nsfwHidden")) preferences.setNsfwHidden(prefsObj.optBoolean("nsfwHidden", false))
                    runCatching { MinResolution.valueOf(prefsObj.optString("minResolution", "ANY")) }
                        .getOrNull()?.let { preferences.setMinResolution(it) }
                    runCatching { AspectRatio.valueOf(prefsObj.optString("aspectRatio", "ANY")) }
                        .getOrNull()?.let { preferences.setAspectRatio(it) }
                    preferences.setChargingTriggerEnabled(prefsObj.optBoolean("chargingTriggerEnabled", false))
                    preferences.setAutoFavoriteEnabled(prefsObj.optBoolean("autoFavoriteEnabled", false))
                    preferences.setAutoFavoriteMinutes(prefsObj.optInt("autoFavoriteMinutes", 120))
                    importedWidgetCollectionId = prefsObj.optString("widgetCollectionId", "")
                }

                // Restore MAL filter preferences (not auth tokens — those expire)
                val malObj = json.optJSONObject("mal")
                if (malObj != null) {
                    val statusArr = malObj.optJSONArray("filterStatuses")
                    if (statusArr != null) {
                        val statuses = (0 until statusArr.length()).map { statusArr.getString(it) }.toSet()
                        malPrefs.setFilterStatuses(statuses)
                    }
                    malPrefs.setFilterMinScore(malObj.optInt("filterMinScore", 0))
                }

                // Restore installed plugin manifests
                val pluginsArr2 = json.optJSONArray("installedPlugins")
                if (pluginsArr2 != null) {
                    for (i in 0 until pluginsArr2.length()) {
                        val obj = pluginsArr2.optJSONObject(i) ?: continue
                        val manifest = com.chrisalvis.rotato.data.plugins.PluginManifest.fromJson(obj) ?: continue
                        pluginRepository.saveManifest(manifest)
                    }
                }

                // Restore collections (v2+)
                val collectionsArr = json.optJSONArray("collections")
                // Backup collection id -> id here, for one that already exists under the same name.
                val listIdMap = mutableMapOf<String, String>()
                if (collectionsArr != null) {
                    val existingLists = localLists.lists.first()
                    val existingIds = existingLists.map { it.id }.toSet()
                    val idsByName = existingLists.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
                    for (i in 0 until collectionsArr.length()) {
                        val o = collectionsArr.getJSONObject(i)
                        val id = o.optString("id").ifBlank { null } ?: continue
                        val sameName = idsByName[o.optString("name", "Imported").trim().lowercase()]
                        if (id !in existingIds && sameName != null) {
                            listIdMap[id] = sameName
                        } else if (id !in existingIds) {
                            val malConfigObj = o.optJSONObject("malConfig")
                            val list = LocalList(
                                id = id,
                                name = o.optString("name", "Imported"),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                useAsRotation = o.optBoolean("useAsRotation", false),
                                isLocked = o.optBoolean("isLocked", false),
                                malConfig = malConfigObj?.let { obj ->
                                    com.chrisalvis.rotato.data.MalCollectionConfig(
                                        animeTitle = obj.optString("animeTitle", ""),
                                        animeQuery = obj.optString("animeQuery", ""),
                                        characterTags = obj.optJSONArray("characterTags")
                                            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } }
                                            ?: emptyList(),
                                        sourcePluginId = obj.optString("sourcePluginId").ifBlank { null },
                                        sourceInstanceId = obj.optString("sourceInstanceId", ""),
                                        fillCount = obj.optInt("fillCount", 25),
                                        matchAny = obj.optBoolean("matchAny", false),
                                        autoAddToLibrary = obj.optBoolean("autoAddToLibrary", false),
                                        nsfwOverride = obj.takeIf { it.has("nsfwOverride") }?.optBoolean("nsfwOverride"),
                                        minResolution = runCatching {
                                            MinResolution.valueOf(obj.optString("minResolution", MinResolution.ANY.name))
                                        }.getOrDefault(MinResolution.ANY),
                                        aspectRatio = runCatching {
                                            AspectRatio.valueOf(obj.optString("aspectRatio", AspectRatio.ANY.name))
                                        }.getOrDefault(AspectRatio.ANY),
                                        useMalFilter = obj.optBoolean("useMalFilter", false),
                                    ).takeIf { it.animeTitle.isNotBlank() }
                                },
                            ).withBackupSettings(o)
                            localLists.createListWithId(list)
                            idsByName[list.name.trim().lowercase()] = list.id
                        }
                    }
                }
                val wallpapersArr = json.optJSONArray("collectionWallpapers")
                if (wallpapersArr != null) {
                    val existing = localLists.allWallpapers.first().map { it.id }.toSet()
                    for (i in 0 until wallpapersArr.length()) {
                        val o = wallpapersArr.getJSONObject(i)
                        val id = o.optString("id").ifBlank { null } ?: continue
                        if (id in existing) continue
                        val tagsArr = o.optJSONArray("tags")
                        val backupListId = o.optString("listId", "")
                        val entry = LocalWallpaperEntry(
                            id = id,
                            listId = listIdMap[backupListId] ?: backupListId,
                            sourceId = o.optString("sourceId", ""),
                            source = o.optString("source", ""),
                            thumbUrl = o.optString("thumbUrl", ""),
                            sampleUrl = o.optString("sampleUrl", ""),
                            fullUrl = o.optString("fullUrl", ""),
                            resolution = o.optString("resolution", ""),
                            pageUrl = o.optString("pageUrl", ""),
                            tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList(),
                            addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                            isVideo = o.optBoolean("isVideo", false),
                            isNsfw = o.optBoolean("isNsfw", false),
                        )
                        if (entry.listId.isNotBlank() && entry.sourceId.isNotBlank()) {
                            localLists.addWallpaperEntry(entry)
                        }
                    }
                }

                importedWidgetCollectionId?.let { preferences.setWidgetCollectionId(it) }
                RotatoWidgetProvider.refreshAll(getApplication())
                _backupState.update { BackupState.SUCCESS }
            }.onFailure {
                android.util.Log.e("HomeViewModel", "Import failed", it)
                _backupState.update { BackupState.ERROR }
            }
            kotlinx.coroutines.delay(2_500)
            _backupState.update { BackupState.IDLE }
        }
    }

    companion object {
        const val WORK_NAME = "rotato_wallpaper_rotation"
        const val SET_NOW_WORK_NAME = "rotato_set_wallpaper_now"
    }
}
