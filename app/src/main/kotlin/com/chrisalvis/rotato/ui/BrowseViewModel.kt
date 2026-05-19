package com.chrisalvis.rotato.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.SmartRule
import com.chrisalvis.rotato.data.ScheduleEntry
import com.chrisalvis.rotato.data.SchedulePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.chrisalvis.rotato.data.sanitizeFilename
import com.chrisalvis.rotato.worker.ScheduleReceiver
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class WallpaperSortOrder { DATE_ADDED, SOURCE, RESOLUTION }

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val localLists = LocalListsPreferences(application)
    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val feedRepo = FeedRepository(imageDir)
    private lateinit var processLifecycleObserver: DefaultLifecycleObserver

    // All lists including locked ones (source of truth)
    private val _allLists: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // IDs of locked lists unlocked for this session (shared app-level, cleared when app is backgrounded)
    private val _unlockedListIds = getApplication<com.chrisalvis.rotato.RotatoApp>().unlockedListIds
    val unlockedListIds: StateFlow<Set<String>> = _unlockedListIds.asStateFlow()

    // Lists visible to the user: unlocked + not locked
    val lists: StateFlow<List<LocalList>> = combine(_allLists, _unlockedListIds) { all, unlocked ->
        all.filter { !it.isLocked || unlocked.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Count of locked collections hidden from the grid
    val lockedHiddenCount: StateFlow<Int> = combine(_allLists, _unlockedListIds) { all, unlocked ->
        all.count { it.isLocked && !unlocked.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val listCounts: StateFlow<Map<String, Int>> = localLists.allWallpapers
        .map { all -> all.groupBy { it.listId }.mapValues { (_, v) -> v.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allKnownTags: StateFlow<List<String>> = localLists.allWallpapers
        .map { all -> all.flatMap { it.tags }.map { it.lowercase() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listCovers: StateFlow<Map<String, String?>> = combine(_allLists, localLists.allWallpapers) { lists, all ->
        val wallpapersByList = all.groupBy { it.listId }
        lists.associate { list ->
            val explicitCover = list.coverUrl
                .takeIf { it.isNotBlank() }
                ?.let { resolveEntryUrl(it, app.filesDir) }
                ?.takeIf { it.isNotBlank() }
            val fallbackCover = wallpapersByList[list.id]
                ?.maxByOrNull { it.addedAt }
                ?.let { entry ->
                    val rawUrl = entry.thumbUrl.ifBlank { entry.fullUrl }
                    resolveEntryUrl(rawUrl, app.filesDir, entry.sourceId)
                }
                ?.takeIf { it.isNotBlank() }
            list.id to (explicitCover ?: fallbackCover)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Store selected list by ID so it auto-clears when a list becomes hidden (locked)
    private val _selectedListId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _collectionSearch = MutableStateFlow("")
    val collectionSearch: StateFlow<String> = _collectionSearch.asStateFlow()
    private val _sortOrder = MutableStateFlow(WallpaperSortOrder.DATE_ADDED)
    val sortOrder: StateFlow<WallpaperSortOrder> = _sortOrder.asStateFlow()
    private val _duplicateWarning = MutableSharedFlow<String>()
    val duplicateWarning: SharedFlow<String> = _duplicateWarning.asSharedFlow()
    private val _exportCompletion = MutableSharedFlow<Int>()
    val exportCompletion: SharedFlow<Int> = _exportCompletion.asSharedFlow()
    private val _exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val exportProgress: StateFlow<Pair<Int, Int>?> = _exportProgress.asStateFlow()
    private val _restoreProgress = MutableStateFlow<String?>(null)
    val restoreProgress: StateFlow<String?> = _restoreProgress.asStateFlow()
    private val _downloadAllProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val downloadAllProgress: StateFlow<Pair<Int, Int>?> = _downloadAllProgress.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.update { q } }
    fun setCollectionSearch(q: String) { _collectionSearch.update { q } }
    fun setSortOrder(order: WallpaperSortOrder) { _sortOrder.update { order } }

    val selectedList: StateFlow<LocalList?> = combine(lists, _selectedListId) { visible, id ->
        visible.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val visibleWallpaperEntries: StateFlow<List<LocalWallpaperEntry>> =
        combine(localLists.allWallpapers, _selectedListId, lists, _collectionSearch, _sortOrder) { all, id, visible, query, sortOrder ->
            if (id == null || visible.none { it.id == id }) return@combine emptyList()
            val listEntries = all.filter { it.listId == id }
            val matched = if (query.isBlank()) listEntries else listEntries.filter { entry ->
                entry.tags.any { it.contains(query, ignoreCase = true) } ||
                entry.source.contains(query, ignoreCase = true)
            }
            matched.sortedFor(sortOrder)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallpapers: StateFlow<List<BrowseWallpaper>> = visibleWallpaperEntries
        .map { entries -> entries.map { it.toBrowseWallpaper(app.filesDir) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inRotation = MutableStateFlow<Set<String>>(emptySet())
    val inRotation: StateFlow<Set<String>> = _inRotation.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _brokenEntryIds = MutableStateFlow<Set<String>>(emptySet())
    val brokenEntryIds: StateFlow<Set<String>> = _brokenEntryIds.asStateFlow()

    private val _isCheckingLinks = MutableStateFlow(false)
    val isCheckingLinks: StateFlow<Boolean> = _isCheckingLinks.asStateFlow()

    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private var linkCheckJob: Job? = null

    init {
        _inRotation.update {
            imageDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        }
        // Session unlock persists for the process lifetime — re-locks only when the OS kills the app.
        // This allows background wallpaper rotation on locked collections without constant re-prompts.
        processLifecycleObserver = object : DefaultLifecycleObserver {}
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)

        // React to new wallpapers in manual collections and auto-populate smart collections.
        // We only watch non-smart entries so adding to smart collections doesn't re-trigger.
        // Use Eagerly so the observer stays hot even when no UI subscribers are active.
        viewModelScope.launch {
            combine(localLists.allWallpapers, localLists.lists) { allEntries, lists ->
                val smartIds = lists.filter { it.isSmartCollection }.map { it.id }.toSet()
                Triple(allEntries, lists.filter { it.isSmartCollection }, allEntries.filter { it.listId !in smartIds })
            }.collect { (allEntries, smartLists, manualEntries) ->
                if (smartLists.isEmpty()) return@collect
                Log.d("SmartCollection", "observer fired: smartLists=${smartLists.size} manualEntries=${manualEntries.size}")
                for (smartList in smartLists) {
                    val rule = smartList.smartRule ?: continue
                    val alreadyIn = allEntries.filter { it.listId == smartList.id }.map { it.sourceId }.toSet()
                    for (entry in manualEntries) {
                        if (entry.sourceId in alreadyIn) continue
                        if (rule.matches(entry)) {
                            Log.d("SmartCollection", "observer match: sourceId=${entry.sourceId} tags=${entry.tags} → adding to ${smartList.id}")
                            localLists.addWallpaperEntry(entry.copy(
                                id = UUID.randomUUID().toString(),
                                listId = smartList.id,
                            ))
                        }
                    }
                }
            }
        }
    }

    fun selectList(list: LocalList) {
        _selectedListId.update { list.id }
        _searchQuery.update { "" }
        _collectionSearch.update { "" }
        exitSelectionMode()
        _brokenEntryIds.update { emptySet() }
        checkLinksForCurrentList()
    }

    fun clearSelection() {
        _selectedListId.update { null }
        _collectionSearch.update { "" }
        exitSelectionMode()
    }

    fun showCreateDialog() { _showCreateDialog.update { true } }
    fun dismissCreateDialog() { _showCreateDialog.update { false } }

    fun createList(name: String, smartRule: SmartRule? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = localLists.createList(name)
            if (smartRule != null && !smartRule.isEmpty) {
                localLists.setSmartRule(list.id, smartRule)
                populateSmartCollection(list.id, smartRule)
            }
            _showCreateDialog.update { false }
            _selectedListId.update { list.id }
        }
    }

    private suspend fun populateSmartCollection(listId: String, rule: SmartRule, limit: Int = Int.MAX_VALUE): Int {
        val allEntries = localLists.allWallpapers.first()
        val smartIds = localLists.lists.first().filter { it.isSmartCollection }.map { it.id }.toSet()
        val alreadyInIds = allEntries.filter { it.listId == listId }.map { it.sourceId }.toSet()
        val seenSourceIds = alreadyInIds.toMutableSet()
        val sourceEntries = allEntries.filter { it.listId !in smartIds }
        var added = 0
        for (entry in sourceEntries) {
            if (added >= limit) break
            if (entry.sourceId in seenSourceIds) continue
            if (rule.matches(entry)) {
                localLists.addWallpaperEntry(entry.copy(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                ))
                seenSourceIds.add(entry.sourceId)
                added++
            }
        }
        Log.d("SmartCollection", "populate: allEntries=${allEntries.size} smartIds=$smartIds sourceEntries=${sourceEntries.size} alreadyIn=${alreadyInIds.size} added=$added rule=$rule")
        return added
    }

    fun editSmartRule(list: LocalList, rule: SmartRule?) {
        viewModelScope.launch {
            localLists.setSmartRule(list.id, rule)
            if (rule != null && !rule.isEmpty) {
                populateSmartCollection(list.id, rule)
            }
        }
    }

    fun autofillSmartCollection(list: LocalList, limit: Int = 25) {
        val rule = list.smartRule ?: return
        Log.d("SmartCollection", "autofill triggered: listId=${list.id} rule=$rule limit=$limit")
        viewModelScope.launch {
            val added = populateSmartCollection(list.id, rule, limit)
            val ctx = app.applicationContext
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, if (added > 0) "Autofill added $added image${if (added != 1) "s" else ""}" else "No new matches found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteList(list: LocalList) {
        viewModelScope.launch {
            if (_selectedListId.value == list.id) clearSelection()
            // Collect device-image entries before they're deleted from DataStore
            val deviceEntries = localLists.allWallpapers.first()
                .filter { it.listId == list.id && it.source == "device" }
            localLists.deleteList(list.id)
            // Remove all local image files for this collection
            File(app.filesDir, "list_images/${list.id}").deleteRecursively()
            // Also clean rotation-pool copies for device images from this list
            deviceEntries.forEach { entry ->
                val key = sanitize(entry.sourceId)
                imageDir.listFiles()?.find { it.nameWithoutExtension == key }?.delete()
                _inRotation.update { it - key }
            }
        }
    }

    fun renameList(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { localLists.renameList(id, name) }
    }

    /** Permanently lock a collection. No auth required — just hides it. */
    fun lockCollection(listId: String) {
        viewModelScope.launch {
            localLists.setLocked(listId, true)
            _unlockedListIds.update { it - listId }
            // Auto-clear selection if the locked list was selected
            if (_selectedListId.value == listId) clearSelection()
        }
    }

    /** Permanently remove the lock from a collection. Call only after biometric succeeds. */
    fun unlockCollection(listId: String) {
        viewModelScope.launch {
            localLists.setLocked(listId, false)
            _unlockedListIds.update { it + listId }
            applyPendingSchedules(setOf(listId))
        }
    }

    /** Re-hide a session-unlocked collection without removing its permanent lock. */
    fun relockForSession(listId: String) {
        _unlockedListIds.update { it - listId }
    }

    /** Grant session-level access to all locked collections without removing their lock. */
    fun grantSessionAccess() {
        val locked = _allLists.value.filter { it.isLocked }.map { it.id }.toSet()
        _unlockedListIds.update { it + locked }
        viewModelScope.launch { applyPendingSchedules(locked) }
    }

    /** Re-lock all collections (clears session access). Called when app is backgrounded. */
    fun lockAll() {
        _unlockedListIds.update { emptySet() }
    }

    /**
     * For each list in [listIds], immediately apply any schedule entry that is currently active.
     * An entry is considered active if it has a pending locked event (lastLockedMs > 0) OR if the
     * current time falls within its scheduled window (today is a scheduled day and the clock is at
     * or past the entry's start time). The latter handles the case where the retry window already
     * expired but the user is still within the intended schedule window (e.g., unlocking at 8pm
     * for a 6pm schedule that gave up retrying at 7pm).
     */
    private suspend fun applyPendingSchedules(listIds: Set<String>) {
        if (listIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            val schedPrefs = SchedulePreferences(app)
            val allEntries = schedPrefs.entries.first()
            val listPrefs = LocalListsPreferences(app)
            Log.d("BrowseViewModel", "applyPendingSchedules: listIds=$listIds allEntries=${allEntries.size}")
            listIds.forEach { listId ->
                val matching = allEntries.filter {
                    it.enabled && it.listId == listId && isInScheduleWindow(it)
                }
                Log.d("BrowseViewModel", "  listId=$listId matching=${matching.size}")
                matching.forEach { entry ->
                    Log.d("BrowseViewModel", "  applying entry ${entry.id}")
                    try {
                        val result = ScheduleReceiver.applyEntry(app, entry, allEntries, schedPrefs, listPrefs)
                        Log.d("BrowseViewModel", "  applied entry ${entry.id}: $result")
                    } catch (e: Exception) {
                        Log.e("BrowseViewModel", "applyEntry failed for ${entry.id}", e)
                        runCatching { schedPrefs.recordTrigger(entry.id, "error: ${e.javaClass.simpleName}") }
                    }
                }
            }
        }
    }

    /** Returns true if [entry] is currently within its scheduled window — today is one of its
     *  scheduled days and the current time is at or past its start time. */
    private fun isInScheduleWindow(entry: ScheduleEntry): Boolean {
        val now = java.util.Calendar.getInstance()
        if (now.get(java.util.Calendar.DAY_OF_WEEK) !in entry.days) return false
        val nowMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return nowMins >= entry.startHour * 60 + entry.startMinute
    }

    fun removeWallpaper(entryId: String) {
        val wp = wallpapers.value.find { it.entryId == entryId }
        viewModelScope.launch {
            localLists.removeWallpaper(entryId)
            if (wp?.source == "device") {
                // Delete the local image file
                val uri = Uri.parse(wp.fullUrl)
                uri.path?.let { File(it).delete() }
                // Also remove from rotation pool if present
                val key = sanitize(wp.sourceId)
                imageDir.listFiles()?.find { it.nameWithoutExtension == key }?.delete()
                _inRotation.update { it - key }
            }
        }
    }

    fun enterSelectionMode(wallpaper: BrowseWallpaper) {
        _selectionMode.update { true }
        _selected.update { setOf(wallpaper.entryId) }
    }

    fun toggleSelection(wallpaper: BrowseWallpaper) {
        if (!_selectionMode.value) return
        _selected.update {
            if (it.contains(wallpaper.entryId)) it - wallpaper.entryId else it + wallpaper.entryId
        }
    }

    fun exitSelectionMode() {
        _selectionMode.update { false }
        _selected.update { emptySet() }
    }

    fun downloadSelected() {
        val allSelected = wallpapers.value.filter { _selected.value.contains(it.entryId) }
        val toDownload = allSelected.filter { it.source != "device" }
        val deviceCount = allSelected.size - toDownload.size
        exitSelectionMode()
        val ctx = app.applicationContext
        viewModelScope.launch {
            var saved = 0; var failed = 0
            toDownload.forEach { wp ->
                if (_downloading.value.contains(wp.sourceId)) return@forEach
                _downloading.update { it + wp.sourceId }
                val ok = feedRepo.saveToGallery(ctx, wp.sourceId, wp.fullUrl, wp.sampleUrl.ifBlank { wp.thumbUrl })
                if (ok) saved++ else failed++
                _downloading.update { it - wp.sourceId }
            }
            val msg = when {
                failed == 0 && deviceCount == 0 -> "Saved $saved photo${if (saved != 1) "s" else ""} to Pictures/Rotato"
                failed == 0 && deviceCount > 0  -> "Saved $saved to Pictures/Rotato ($deviceCount device photo${if (deviceCount != 1) "s" else ""} already on device)"
                saved == 0  -> "Failed to save $failed photo${if (failed != 1) "s" else ""}"
                else        -> "Saved $saved, failed $failed"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun removeSelected() {
        val toRemove = wallpapers.value.filter { _selected.value.contains(it.entryId) }
        exitSelectionMode()
        viewModelScope.launch {
            toRemove.forEach { wp ->
                localLists.removeWallpaper(wp.entryId)
                if (wp.source == "device") {
                    val uri = android.net.Uri.parse(wp.fullUrl)
                    uri.path?.let { java.io.File(it).delete() }
                    val key = sanitize(wp.sourceId)
                    imageDir.listFiles()?.find { it.nameWithoutExtension == key }?.delete()
                    _inRotation.update { it - key }
                }
            }
        }
    }

    fun saveWallpaper(wallpaper: BrowseWallpaper) {
        if (wallpaper.source == "device") {
            Toast.makeText(app.applicationContext, "Already on device", Toast.LENGTH_SHORT).show()
            return
        }
        val ctx = app.applicationContext
        viewModelScope.launch {
            if (_downloading.value.contains(wallpaper.sourceId)) return@launch
            _downloading.update { it + wallpaper.sourceId }
            val ok = feedRepo.saveToGallery(ctx, wallpaper.sourceId, wallpaper.fullUrl, wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl })
            _downloading.update { it - wallpaper.sourceId }
            val msg = if (ok) "Saved to Pictures/Rotato" else "Failed to save"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun setCoverImage(wallpaper: LocalWallpaperEntry) {
        val currentListId = _selectedListId.value ?: return
        viewModelScope.launch {
            localLists.setCoverImage(currentListId, wallpaper.thumbUrl.ifBlank { wallpaper.fullUrl })
        }
    }

    fun wallpaperEntry(entryId: String): LocalWallpaperEntry? = visibleWallpaperEntries.value.find { it.id == entryId }

    fun shareWallpapers(context: Context, wallpapers: List<LocalWallpaperEntry>) {
        if (wallpapers.isEmpty()) return
        val text = wallpapers.joinToString("\n") { it.fullUrl }
        val intent = if (wallpapers.size == 1 && wallpapers.first().source == "device") {
            val entry = wallpapers.first()
            val shareFile = resolveShareFile(entry.fullUrl)
            val contentUri = shareFile
                ?.takeIf { it.exists() }
                ?.let {
                    runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                    }.getOrNull()
                }
            if (contentUri != null) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        val chooser = Intent.createChooser(intent, "Share wallpaper")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun restoreFromBackup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _restoreProgress.update { "Reading backup..." }
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw IllegalArgumentException("Unable to read backup")
                val root = JSONObject(json)
                val collections = root.optJSONArray("collections") ?: root.optJSONArray("lists") ?: JSONArray()
                val wallpapers = root.optJSONArray("collectionWallpapers") ?: root.optJSONArray("wallpapers") ?: JSONArray()

                _restoreProgress.update { "Restoring collections..." }
                val existingByName = localLists.lists.first()
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                val listIdMap = mutableMapOf<String, String>()
                var restoredCollections = 0

                for (i in 0 until collections.length()) {
                    val item = collections.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    val oldId = item.optString("id").ifBlank { name }
                    val existing = existingByName[name.lowercase()]
                    val target = existing ?: localLists.createList(name).also { created ->
                        restoredCollections++
                        existingByName[name.lowercase()] = created
                        if (item.optBoolean("isLocked", false)) {
                            localLists.setLocked(created.id, true)
                        }
                    }
                    listIdMap[oldId] = target.id
                }

                _restoreProgress.update { "Restoring images..." }
                val existingEntryIds = localLists.allWallpapers.first().mapTo(mutableSetOf()) { it.id }
                var restoredImages = 0
                for (i in 0 until wallpapers.length()) {
                    val item = wallpapers.optJSONObject(i) ?: continue
                    val mappedListId = listIdMap[item.optString("listId")] ?: continue
                    var entryId = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                    while (!existingEntryIds.add(entryId)) {
                        entryId = UUID.randomUUID().toString()
                    }
                    val tagsArray = item.optJSONArray("tags")
                    val tags = if (tagsArray != null) {
                        (0 until tagsArray.length()).mapNotNull { index -> tagsArray.optString(index).takeIf { it.isNotBlank() } }
                    } else {
                        emptyList()
                    }
                    localLists.addWallpaperEntry(
                        LocalWallpaperEntry(
                            id = entryId,
                            listId = mappedListId,
                            sourceId = item.optString("sourceId"),
                            source = item.optString("source"),
                            thumbUrl = item.optString("thumbUrl"),
                            sampleUrl = item.optString("sampleUrl", ""),
                            fullUrl = item.optString("fullUrl"),
                            resolution = item.optString("resolution"),
                            pageUrl = item.optString("pageUrl"),
                            tags = tags,
                            addedAt = item.optLong("addedAt", System.currentTimeMillis())
                        )
                    )
                    restoredImages++
                }

                val message = "Restored $restoredCollections collections, $restoredImages images"
                _restoreProgress.update { message }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BrowseViewModel", "Failed to restore backup", e)
                val message = "Restore failed"
                _restoreProgress.update { message }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } finally {
                delay(2000)
                _restoreProgress.update { null }
            }
        }
    }

    fun isInRotation(wallpaper: BrowseWallpaper) = _inRotation.value.contains(sanitize(wallpaper.sourceId))

    fun toggleRotation(wallpaper: BrowseWallpaper) {
        val key = sanitize(wallpaper.sourceId)
        if (_downloading.value.contains(wallpaper.sourceId)) return
        if (_inRotation.value.contains(key)) {
            imageDir.listFiles()?.find { it.nameWithoutExtension == key }?.delete()
            _inRotation.update { it - key }
            return
        }
        viewModelScope.launch {
            _downloading.update { it + wallpaper.sourceId }
            val ok = if (wallpaper.source == "device") {
                copyLocalToRotation(wallpaper.fullUrl, key)
            } else {
                feedRepo.downloadWallpaper(wallpaper.sourceId, wallpaper.fullUrl, wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl })
            }
            if (ok) _inRotation.update { it + key }
            _downloading.update { it - wallpaper.sourceId }
        }
    }

    fun downloadAllToRotation(listId: String) {
        if (_downloadAllProgress.value != null) return
        val ctx = app.applicationContext
        viewModelScope.launch {
            val pending = localLists.wallpapersForList(listId).first()
                .filterNot { _inRotation.value.contains(sanitize(it.sourceId)) }
            if (pending.isEmpty()) {
                Toast.makeText(ctx, "Downloaded 0 images to rotation", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var downloaded = 0
            _downloadAllProgress.update { 0 to pending.size }
            try {
                pending.forEachIndexed { index, entry ->
                    if (_downloading.value.contains(entry.sourceId)) {
                        _downloadAllProgress.update { (index + 1) to pending.size }
                        return@forEachIndexed
                    }
                    val key = sanitize(entry.sourceId)
                    val wallpaper = entry.toBrowseWallpaper(app.filesDir)
                    _downloading.update { it + entry.sourceId }
                    val ok = try {
                        if (entry.source == "device") {
                            copyLocalToRotation(wallpaper.fullUrl, key)
                        } else {
                            feedRepo.downloadWallpaper(
                                sourceId = entry.sourceId,
                                fullUrl = wallpaper.fullUrl.ifBlank { wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl } },
                                fallbackUrl = wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl },
                            )
                        }
                    } finally {
                        _downloading.update { it - entry.sourceId }
                    }
                    if (ok) {
                        _inRotation.update { it + key }
                        downloaded++
                    }
                    _downloadAllProgress.update { (index + 1) to pending.size }
                }
            } finally {
                _downloadAllProgress.update { null }
            }

            Toast.makeText(ctx, "Downloaded $downloaded images to rotation", Toast.LENGTH_SHORT).show()
        }
    }

    fun addAllToRotation() {
        val listId = _selectedListId.value ?: return
        downloadAllToRotation(listId)
    }

    fun refreshInRotation() {
        _inRotation.update {
            imageDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        }
    }

    fun checkLinksForCurrentList() {
        val listId = _selectedListId.value ?: return
        linkCheckJob?.cancel()
        linkCheckJob = viewModelScope.launch(Dispatchers.IO) {
            _isCheckingLinks.update { true }
            _brokenEntryIds.update { emptySet() }
            // Read entries directly from the repo for this listId — avoids race condition
            // where wallpapers StateFlow might still hold the previous list's entries
            val rawEntries = localLists.wallpapersForList(listId).first()
            if (rawEntries.isEmpty()) {
                _isCheckingLinks.update { false }
                return@launch
            }
            val broken = mutableSetOf<String>()
            val semaphore = Semaphore(4) // max 4 parallel HTTP requests to avoid rate limiting
            coroutineScope {
                rawEntries.map { entry ->
                    async {
                        // Check fullUrl first — that's the actual image. thumbUrl is often a CDN
                        // preview that can 404 even when the full image is alive (e.g. Gelbooru thumbnails).
                        // Fall back to thumbUrl only when fullUrl is blank.
                        val urlToCheck = resolveEntryUrl(
                            entry.fullUrl.ifBlank { entry.thumbUrl },
                            app.filesDir,
                            entry.sourceId
                        )
                        // Local files are always healthy — no HTTP check needed
                        if (urlToCheck.startsWith("file://")) return@async
                        val isBroken = semaphore.withPermit { isUrlBroken(urlToCheck) }
                        if (isBroken) synchronized(broken) { broken.add(entry.id) }
                    }
                }.awaitAll()
            }
            // Only update state if we're still viewing this same list (guard against race on list switch)
            if (_selectedListId.value == listId) {
                _brokenEntryIds.update { broken }
            }
            _isCheckingLinks.update { false }
        }
    }

    private fun isUrlBroken(url: String): Boolean {
        return try {
            val headResp = healthClient.newCall(Request.Builder().url(url).head().build()).execute()
            headResp.close()
            when {
                headResp.isSuccessful -> false
                headResp.code == 405 -> {
                    // Server doesn't support HEAD — try a minimal Range GET
                    val getResp = healthClient.newCall(
                        Request.Builder().url(url).header("Range", "bytes=0-0").build()
                    ).execute()
                    getResp.body?.close()
                    !getResp.isSuccessful && getResp.code != 416 // 416 = range invalid but image exists
                }
                headResp.code == 429 || headResp.code == 503 -> false // rate limited, not broken
                headResp.code == 401 || headResp.code == 403 -> false // auth required — resource exists, not gone
                else -> true
            }
        } catch (_: java.net.SocketTimeoutException) { false } // timeout = network issue, not a dead link
        catch (_: java.io.IOException) { false }           // connection error = assume alive
        catch (_: Exception) { false }                     // any other transport error = assume alive
    }

    fun removeBrokenEntries() {
        val ids = _brokenEntryIds.value.toSet()
        _brokenEntryIds.update { emptySet() }
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { entryId -> localLists.removeWallpaper(entryId) }
        }
    }

    fun importFromRotation(listName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = localLists.createList(listName)
            val files = imageDir.listFiles() ?: return@launch
            files.sortedBy { it.name }.forEach { file ->
                val relativePath = "rotato_images/${file.name}"
                localLists.addWallpaperEntry(
                    com.chrisalvis.rotato.data.LocalWallpaperEntry(
                        listId = list.id,
                        sourceId = file.nameWithoutExtension,
                        source = "device",
                        thumbUrl = file.toURI().toString(),
                        fullUrl = file.toURI().toString(),
                        resolution = "",
                        pageUrl = "",
                        tags = emptyList(),
                    )
                )
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Saved ${files.size} images to \"$listName\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addLocalImages(listId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val list = localLists.lists.first().find { it.id == listId } ?: return@launch
            val listImagesDir = File(app.filesDir, "list_images/$listId").also { it.mkdirs() }
            var added = 0
            uris.forEach { uri ->
                try {
                    val mimeType = app.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = when {
                        mimeType.contains("png")  -> "png"
                        mimeType.contains("webp") -> "webp"
                        mimeType.contains("gif")  -> "gif"
                        else -> "jpg"
                    }
                    val uuid = UUID.randomUUID().toString()
                    val destFile = File(listImagesDir, "$uuid.$ext")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@forEach
                    if (!destFile.exists() || destFile.length() == 0L) {
                        destFile.delete(); return@forEach
                    }
                    val relativePath = "list_images/$listId/$uuid.$ext"
                    localLists.addLocalImage(listId, relativePath)
                    if (list.useAsRotation) {
                        val key = sanitize(uuid)
                        val rotationDest = File(imageDir, "$key.$ext")
                        destFile.copyTo(rotationDest, overwrite = true)
                        _inRotation.update { it + key }
                    }
                    added++
                } catch (e: Exception) {
                    Log.e("BrowseViewModel", "Failed to import image $uri", e)
                }
            }
            val count = added
            withContext(Dispatchers.Main) {
                if (count > 0)
                    Toast.makeText(app, "Added $count image${if (count != 1) "s" else ""} to ${list.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveShareFile(fullUrl: String): File? {
        val uri = Uri.parse(fullUrl)
        return when {
            uri.scheme == "file" -> uri.path?.let(::File)
            fullUrl.startsWith("list_images/") || fullUrl.startsWith("rotato_images/") -> File(app.filesDir, fullUrl)
            File(fullUrl).exists() -> File(fullUrl)
            else -> uri.path?.let(::File)?.takeIf { it.exists() }
        }
    }

    /** Copies a device image (fullUrl = file:// URI string) to the rotation pool. */
    private fun copyLocalToRotation(fullUrl: String, key: String): Boolean {
        return try {
            val src = File(Uri.parse(fullUrl).path ?: return false)
            if (!src.exists()) return false
            val ext = src.extension.ifBlank { "jpg" }
            src.copyTo(File(imageDir, "$key.$ext"), overwrite = true)
            true
        } catch (e: Exception) {
            Log.e("BrowseViewModel", "copyLocalToRotation failed: $fullUrl", e)
            false
        }
    }

    fun moveSelectedToList(targetListId: String) {
        val currentListId = selectedList.value?.id ?: return
        if (targetListId == currentListId) { exitSelectionMode(); return }
        val selectedIds = _selected.value
        viewModelScope.launch {
            val targetUrls = localLists.wallpapersForList(targetListId).first()
                .map { it.fullUrl }
                .toMutableSet()
            val sourceEntries = localLists.allWallpapers.first()
                .filter { it.listId == currentListId && it.id in selectedIds }
            var duplicateFound = false
            sourceEntries.forEach { entry ->
                if (!targetUrls.add(entry.fullUrl)) {
                    duplicateFound = true
                    return@forEach
                }
                localLists.removeWallpaper(entry.id)
                localLists.addWallpaperEntry(entry.copy(listId = targetListId))
            }
            if (duplicateFound) _duplicateWarning.emit("Already in collection")
            exitSelectionMode()
        }
    }

    fun exportCollectionToGallery() {
        val listId = _selectedListId.value ?: return
        if (_exportProgress.value != null) return
        val ctx = app.applicationContext
        viewModelScope.launch {
            val items = localLists.wallpapersForList(listId).first()
            if (items.isEmpty()) {
                _exportCompletion.emit(0)
                return@launch
            }
            var exported = 0
            _exportProgress.update { 0 to items.size }
            try {
                items.forEachIndexed { index, entry ->
                    val wallpaper = entry.toBrowseWallpaper(app.filesDir)
                    val ok = if (wallpaper.fullUrl.startsWith("file://") || wallpaper.fullUrl.startsWith("/")) {
                        saveLocalFileToGallery(ctx, File(Uri.parse(wallpaper.fullUrl).path ?: wallpaper.fullUrl))
                    } else {
                        _downloading.update { it + wallpaper.sourceId }
                        val saved = feedRepo.saveToGallery(ctx, wallpaper.sourceId, wallpaper.fullUrl, wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl })
                        _downloading.update { it - wallpaper.sourceId }
                        saved
                    }
                    if (ok) exported++
                    _exportProgress.update { (index + 1) to items.size }
                }
            } finally {
                _exportProgress.update { null }
            }
            _exportCompletion.emit(exported)
        }
    }

    fun toggleCollectionRotation(list: LocalList) {
        viewModelScope.launch {
            localLists.setUseAsRotation(list.id, !list.useAsRotation)
        }
    }

    fun setRotationTarget(list: LocalList, target: com.chrisalvis.rotato.data.ScreenRotationTarget) {
        viewModelScope.launch {
            localLists.setRotationTarget(list.id, target)
        }
    }

    private fun saveLocalFileToGallery(ctx: android.content.Context, file: File): Boolean {
        if (!file.exists()) return false
        val ext = file.extension.lowercase()
        val mimeType = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        return try {
            val bytes = file.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rotato")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Rotato")
                dir.mkdirs()
                File(dir, file.name).writeBytes(bytes)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    }
}

private fun List<LocalWallpaperEntry>.sortedFor(sortOrder: WallpaperSortOrder): List<LocalWallpaperEntry> =
    when (sortOrder) {
        WallpaperSortOrder.DATE_ADDED -> sortedByDescending { it.addedAt }
        WallpaperSortOrder.SOURCE -> sortedWith(compareBy<LocalWallpaperEntry>({ it.source.lowercase() }, { -it.addedAt }))
        WallpaperSortOrder.RESOLUTION -> sortedWith(compareByDescending<LocalWallpaperEntry> { it.resolutionArea() }.thenByDescending { it.addedAt })
    }

private fun LocalWallpaperEntry.resolutionArea(): Long {
    val parts = resolution.lowercase().split("x")
    if (parts.size != 2) return -1
    val width = parts[0].toLongOrNull() ?: return -1
    val height = parts[1].toLongOrNull() ?: return -1
    return width * height
}

private fun LocalWallpaperEntry.toBrowseWallpaper(filesDir: File) = BrowseWallpaper(
    sourceId = sourceId,
    entryId = id,
    fullUrl = resolveEntryUrl(fullUrl, filesDir, sourceId),
    sampleUrl = sampleUrl,
    thumbUrl = resolveEntryUrl(thumbUrl.ifBlank { fullUrl }, filesDir, sourceId),
    animeTitle = tags.take(3).joinToString(", "),
    source = source
)

/**
 * Resolves a stored URL to the best available local-or-remote URI:
 * 1. `list_images/…` relative paths → absolute file:// URI
 * 2. Already a file:// URI → pass through
 * 3. If a local downloaded copy exists in rotato_images/ for this sourceId → use it
 * 4. Otherwise return the remote URL as-is (may be dead)
 */
private fun resolveEntryUrl(url: String, filesDir: File, sourceId: String = ""): String {
    if (url.startsWith("list_images/")) return File(filesDir, url).toURI().toString()
    if (url.startsWith("file://")) return url
    if (sourceId.isNotBlank()) {
        val sanitized = sanitizeFilename(sourceId)
        val localFile = File(filesDir, "rotato_images").listFiles()
            ?.find { it.nameWithoutExtension == sanitized }
        if (localFile?.exists() == true) return localFile.toURI().toString()
    }
    return url
}
