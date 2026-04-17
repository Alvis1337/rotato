package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.BrowsePage
import com.chrisalvis.rotato.data.BrowseRepository
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.RemoteList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

class BrowseViewModel(application: Application, feed: FeedConfig) : AndroidViewModel(application) {

    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val baseUrl = runCatching { URL(feed.url).let { "${it.protocol}://${it.authority}" } }
        .getOrDefault(feed.url)
    private val browseRepo = BrowseRepository(baseUrl, feed.headers)
    private val feedRepo = FeedRepository(imageDir)

    private val _lists = MutableStateFlow<List<RemoteList>>(emptyList())
    val lists: StateFlow<List<RemoteList>> = _lists.asStateFlow()

    private val _listsLoading = MutableStateFlow(false)
    val listsLoading: StateFlow<Boolean> = _listsLoading.asStateFlow()

    private val _listsError = MutableStateFlow<String?>(null)
    val listsError: StateFlow<String?> = _listsError.asStateFlow()

    private val _selectedList = MutableStateFlow<RemoteList?>(null)
    val selectedList: StateFlow<RemoteList?> = _selectedList.asStateFlow()

    private val _wallpapers = MutableStateFlow<List<BrowseWallpaper>>(emptyList())
    val wallpapers: StateFlow<List<BrowseWallpaper>> = _wallpapers.asStateFlow()

    private val _wallpapersLoading = MutableStateFlow(false)
    val wallpapersLoading: StateFlow<Boolean> = _wallpapersLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // sourceIds of wallpapers currently in the local rotation pool
    private val _inRotation = MutableStateFlow<Set<String>>(emptySet())
    val inRotation: StateFlow<Set<String>> = _inRotation.asStateFlow()

    // sourceIds currently being downloaded
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private var currentPage = 0
    private var currentPages = 1
    private var loadingMore = false

    init {
        // Seed inRotation from existing files on disk
        _inRotation.update {
            imageDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        }
        loadLists()
    }

    fun loadLists() {
        viewModelScope.launch {
            _listsLoading.update { true }
            _listsError.update { null }
            try {
                _lists.update { browseRepo.fetchLists() }
            } catch (e: Exception) {
                _listsError.update { "Could not load lists: ${e.message}" }
            } finally {
                _listsLoading.update { false }
            }
        }
    }

    fun selectList(list: RemoteList) {
        _selectedList.update { list }
        _wallpapers.update { emptyList() }
        currentPage = 0
        currentPages = 1
        loadingMore = false
        loadNextPage(list.id)
    }

    fun clearSelection() {
        _selectedList.update { null }
        _wallpapers.update { emptyList() }
        currentPage = 0
    }

    fun loadMoreIfNeeded() {
        val listId = _selectedList.value?.id ?: return
        if (loadingMore || currentPage >= currentPages) return
        loadNextPage(listId)
    }

    private fun loadNextPage(listId: Int) {
        loadingMore = true
        viewModelScope.launch {
            _wallpapersLoading.update { true }
            try {
                val result: BrowsePage = browseRepo.fetchWallpapers(listId, currentPage + 1)
                _wallpapers.update { it + result.wallpapers }
                currentPage = result.page
                currentPages = result.pages
                _hasMore.update { result.page < result.pages }
            } catch (_: Exception) {
            } finally {
                _wallpapersLoading.update { false }
                loadingMore = false
            }
        }
    }

    fun toggleRotation(wallpaper: BrowseWallpaper) {
        val key = sanitize(wallpaper.sourceId)
        if (_inRotation.value.contains(key)) return  // already in — no remove for now
        if (_downloading.value.contains(wallpaper.sourceId)) return
        viewModelScope.launch {
            _downloading.update { it + wallpaper.sourceId }
            val ok = feedRepo.downloadWallpaper(wallpaper.sourceId, wallpaper.fullUrl)
            if (ok) _inRotation.update { it + key }
            _downloading.update { it - wallpaper.sourceId }
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}

class BrowseViewModelFactory(
    private val app: Application,
    private val feed: FeedConfig
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BrowseViewModel(app, feed) as T
    }
}
