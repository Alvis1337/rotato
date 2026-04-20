package com.chrisalvis.rotato.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val localLists = LocalListsPreferences(application)
    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val feedRepo = FeedRepository(imageDir)

    val lists: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listCounts: StateFlow<Map<String, Int>> = localLists.allWallpapers
        .map { all -> all.groupBy { it.listId }.mapValues { (_, v) -> v.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedList = MutableStateFlow<LocalList?>(null)
    val selectedList: StateFlow<LocalList?> = _selectedList.asStateFlow()

    val wallpapers: StateFlow<List<BrowseWallpaper>> =
        combine(localLists.allWallpapers, _selectedList) { all, selected ->
            val listId = selected?.id ?: return@combine emptyList()
            all.filter { it.listId == listId }.map { it.toBrowseWallpaper() }.reversed()
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
    }

    fun selectList(list: LocalList) {
        _selectedList.update { list }
        exitSelectionMode()
    }

    fun clearSelection() {
        _selectedList.update { null }
        exitSelectionMode()
    }

    fun showCreateDialog() { _showCreateDialog.update { true } }
    fun dismissCreateDialog() { _showCreateDialog.update { false } }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val list = localLists.createList(name)
            _showCreateDialog.update { false }
            _selectedList.update { list }
        }
    }

    fun deleteList(list: LocalList) {
        viewModelScope.launch {
            if (_selectedList.value?.id == list.id) clearSelection()
            localLists.deleteList(list.id)
        }
    }

    fun renameList(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { localLists.renameList(id, name) }
    }

    fun removeWallpaper(entryId: String) {
        viewModelScope.launch { localLists.removeWallpaper(entryId) }
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
        val toDownload = wallpapers.value.filter { _selected.value.contains(it.sourceId) }
        exitSelectionMode()
        val ctx = getApplication<Application>().applicationContext
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
                failed == 0 -> "Saved $saved photo${if (saved != 1) "s" else ""} to Pictures/Rotato"
                saved == 0  -> "Failed to save $failed photo${if (failed != 1) "s" else ""}"
                else        -> "Saved $saved, failed $failed"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun isInRotation(wallpaper: BrowseWallpaper) = _inRotation.value.contains(sanitize(wallpaper.sourceId))

    fun toggleRotation(wallpaper: BrowseWallpaper) {
        val key = sanitize(wallpaper.sourceId)
        if (_downloading.value.contains(wallpaper.sourceId)) return
        if (_inRotation.value.contains(key)) {
            // Remove from rotation pool
            imageDir.listFiles()?.find { it.nameWithoutExtension == key }?.delete()
            _inRotation.update { it - key }
            return
        }
        viewModelScope.launch {
            _downloading.update { it + wallpaper.sourceId }
            val ok = feedRepo.downloadWallpaper(wallpaper.sourceId, wallpaper.fullUrl)
            if (ok) _inRotation.update { it + key }
            _downloading.update { it - wallpaper.sourceId }
        }
    }

    fun addAllToRotation() {
        val toAdd = wallpapers.value.filter { !isInRotation(it) }
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            var added = 0
            toAdd.forEach { wp ->
                if (_downloading.value.contains(wp.sourceId)) return@forEach
                val key = sanitize(wp.sourceId)
                _downloading.update { it + wp.sourceId }
                val ok = feedRepo.downloadWallpaper(wp.sourceId, wp.fullUrl)
                if (ok) { _inRotation.update { it + key }; added++ }
                _downloading.update { it - wp.sourceId }
            }
            Toast.makeText(ctx, "Added $added wallpaper${if (added != 1) "s" else ""} to Library", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}

private fun LocalWallpaperEntry.toBrowseWallpaper() = BrowseWallpaper(
    sourceId = sourceId,
    entryId = id,
    fullUrl = fullUrl,
    thumbUrl = thumbUrl,
    animeTitle = tags.take(3).joinToString(", ")
)
