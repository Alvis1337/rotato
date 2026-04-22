package com.chrisalvis.rotato.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val localLists = LocalListsPreferences(application)
    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val feedRepo = FeedRepository(imageDir)
    private lateinit var processLifecycleObserver: DefaultLifecycleObserver

    // All lists including locked ones (source of truth)
    private val _allLists: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // IDs of locked lists unlocked for this session (cleared when app is backgrounded)
    private val _unlockedListIds = MutableStateFlow<Set<String>>(emptySet())
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

    val listCovers: StateFlow<Map<String, String?>> = localLists.allWallpapers
        .map { all ->
            all.groupBy { it.listId }
                .mapValues { (_, entries) ->
                    entries.lastOrNull()?.let { entry ->
                        val rawUrl = entry.thumbUrl.ifBlank { entry.fullUrl }
                        if (rawUrl.startsWith("list_images/")) File(app.filesDir, rawUrl).toURI().toString() else rawUrl
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Store selected list by ID so it auto-clears when a list becomes hidden (locked)
    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedList: StateFlow<LocalList?> = combine(lists, _selectedListId) { visible, id ->
        visible.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val wallpapers: StateFlow<List<BrowseWallpaper>> =
        combine(localLists.allWallpapers, _selectedListId, lists) { all, id, visible ->
            if (id == null || visible.none { it.id == id }) return@combine emptyList()
            all.filter { it.listId == id }.map { it.toBrowseWallpaper(app.filesDir) }.reversed()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    init {
        _inRotation.update {
            imageDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        }
        // Re-lock all collections when the entire app is backgrounded
        processLifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = lockAll()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }

    fun selectList(list: LocalList) {
        _selectedListId.update { list.id }
        exitSelectionMode()
    }

    fun clearSelection() {
        _selectedListId.update { null }
        exitSelectionMode()
    }

    fun showCreateDialog() { _showCreateDialog.update { true } }
    fun dismissCreateDialog() { _showCreateDialog.update { false } }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = localLists.createList(name)
            _showCreateDialog.update { false }
            _selectedListId.update { list.id }
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
        }
    }

    /** Grant session-level access to all locked collections without removing their lock. */
    fun grantSessionAccess() {
        val locked = _allLists.value.filter { it.isLocked }.map { it.id }.toSet()
        _unlockedListIds.update { it + locked }
    }

    /** Re-lock all collections (clears session access). Called when app is backgrounded. */
    fun lockAll() {
        _unlockedListIds.update { emptySet() }
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
        _selected.update { setOf(wallpaper.sourceId) }
    }

    fun toggleSelection(wallpaper: BrowseWallpaper) {
        if (!_selectionMode.value) return
        _selected.update {
            if (it.contains(wallpaper.sourceId)) it - wallpaper.sourceId else it + wallpaper.sourceId
        }
    }

    fun exitSelectionMode() {
        _selectionMode.update { false }
        _selected.update { emptySet() }
    }

    fun downloadSelected() {
        val allSelected = wallpapers.value.filter { _selected.value.contains(it.sourceId) }
        val toDownload = allSelected.filter { it.source != "device" }
        val deviceCount = allSelected.size - toDownload.size
        exitSelectionMode()
        val ctx = app.applicationContext
        viewModelScope.launch {
            var saved = 0; var failed = 0
            toDownload.forEach { wp ->
                if (_downloading.value.contains(wp.sourceId)) return@forEach
                _downloading.update { it + wp.sourceId }
                val ok = feedRepo.saveToGallery(ctx, wp.sourceId, wp.fullUrl)
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

    fun saveWallpaper(wallpaper: BrowseWallpaper) {
        if (wallpaper.source == "device") {
            Toast.makeText(app.applicationContext, "Already on device", Toast.LENGTH_SHORT).show()
            return
        }
        val ctx = app.applicationContext
        viewModelScope.launch {
            if (_downloading.value.contains(wallpaper.sourceId)) return@launch
            _downloading.update { it + wallpaper.sourceId }
            val ok = feedRepo.saveToGallery(ctx, wallpaper.sourceId, wallpaper.fullUrl)
            _downloading.update { it - wallpaper.sourceId }
            val msg = if (ok) "Saved to Pictures/Rotato" else "Failed to save"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
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
                feedRepo.downloadWallpaper(wallpaper.sourceId, wallpaper.fullUrl)
            }
            if (ok) _inRotation.update { it + key }
            _downloading.update { it - wallpaper.sourceId }
        }
    }

    fun addAllToRotation() {
        val toAdd = wallpapers.value.filter { !isInRotation(it) }
        val ctx = app.applicationContext
        viewModelScope.launch {
            var added = 0
            toAdd.forEach { wp ->
                if (_downloading.value.contains(wp.sourceId)) return@forEach
                val key = sanitize(wp.sourceId)
                _downloading.update { it + wp.sourceId }
                val ok = if (wp.source == "device") {
                    copyLocalToRotation(wp.fullUrl, key)
                } else {
                    feedRepo.downloadWallpaper(wp.sourceId, wp.fullUrl)
                }
                if (ok) { _inRotation.update { it + key }; added++ }
                _downloading.update { it - wp.sourceId }
            }
            Toast.makeText(ctx, "Added $added wallpaper${if (added != 1) "s" else ""} to Library", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshInRotation() {
        _inRotation.update {
            imageDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
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
            val list = lists.value.find { it.id == listId } ?: return@launch
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
            val all = localLists.allWallpapers.first()
            all.filter { it.listId == currentListId && it.id in selectedIds }.forEach { entry ->
                localLists.removeWallpaper(entry.id)
                localLists.addWallpaperEntry(entry.copy(listId = targetListId))
            }
            exitSelectionMode()
        }
    }

    fun toggleCollectionRotation(list: LocalList) {
        viewModelScope.launch {
            localLists.setUseAsRotation(list.id, !list.useAsRotation)
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    }
}

private fun LocalWallpaperEntry.toBrowseWallpaper(filesDir: File) = BrowseWallpaper(
    sourceId = sourceId,
    entryId = id,
    fullUrl = resolveEntryUrl(fullUrl, filesDir),
    thumbUrl = resolveEntryUrl(thumbUrl.ifBlank { fullUrl }, filesDir),
    animeTitle = tags.take(3).joinToString(", "),
    source = source
)

/** Relative paths (list_images/…) are resolved to file:// URIs; remote URLs are passed through. */
private fun resolveEntryUrl(url: String, filesDir: File): String =
    if (url.startsWith("list_images/")) File(filesDir, url).toURI().toString() else url
