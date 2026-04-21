package com.chrisalvis.rotato.ui

import android.app.Application
import android.app.WallpaperManager
import android.graphics.BitmapFactory
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
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.MalPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.SourceType
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.MinResolution
import kotlinx.coroutines.flow.combine
import com.chrisalvis.rotato.worker.WallpaperWorker
import com.chrisalvis.rotato.worker.WallpaperWorker.Companion.CHAIN_WORK_NAME
import com.chrisalvis.rotato.worker.WallpaperWorker.Companion.KEY_INTERVAL_MINUTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ImageRepository(application)
    private val preferences = RotatoPreferences(application)
    private val workManager = WorkManager.getInstance(application)
    private val localLists = LocalListsPreferences(application)
    private val sourcesPrefs = LocalSourcesPreferences(application)
    private val malPrefs = MalPreferences(application)
    private val imageDir = File(application.filesDir, "rotato_images").also { it.mkdirs() }
    private val feedRepo = FeedRepository(imageDir)

    private val _images = MutableStateFlow<List<File>>(repository.getImages())
    val images: StateFlow<List<File>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _setNowState = MutableStateFlow(SetNowState.IDLE)
    val setNowState: StateFlow<SetNowState> = _setNowState.asStateFlow()


    val collections: StateFlow<List<LocalList>> = localLists.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<RotatoSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RotatoSettings())

    val lastRotationMs: StateFlow<Long> = preferences.lastRotationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        observeImageDir()
        recoverIfNeeded()
        observeRotationCollections()
    }

    /** Polls rotato_images/ every 2 s and pushes changes into [_images]. */
    private fun observeImageDir() {
        viewModelScope.launch {
            repository.imagesFlow().collect { files ->
                _images.update { files }
            }
        }
    }

    private fun refreshImages() {
        viewModelScope.launch {
            _images.update { repository.getImages() }
        }
    }

    fun refreshFromFeeds() {
        viewModelScope.launch {
            _images.update { repository.getImages() }
        }
    }

    /** Auto-download wallpapers from any collection marked useAsRotation into the Library pool. */
    private fun observeRotationCollections() {
        viewModelScope.launch {
            localLists.lists.combine(localLists.allWallpapers) { lists, wallpapers ->
                val rotationIds = lists.filter { it.useAsRotation }.map { it.id }.toSet()
                wallpapers.filter { it.listId in rotationIds }
            }.collect { toSync ->
                var changed = false
                toSync.forEach { entry ->
                    if (entry.fullUrl.isBlank()) return@forEach
                    val key = sanitizeFilename(entry.sourceId)
                    val onDisk = imageDir.listFiles()?.any { it.nameWithoutExtension == key } == true
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
                            feedRepo.downloadWallpaper(entry.sourceId, entry.fullUrl)
                            changed = true
                        }
                    }
                }
                if (changed) _images.update { repository.getImages() }
            }
        }
    }

    fun toggleCollectionAsRotation(list: LocalList) {
        viewModelScope.launch {
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

            val infos = workManager.getWorkInfosForUniqueWork(workName).get()
            val isAlive = infos.any { it.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) }

            if (!isAlive) {
                scheduleRotation(intervalMinutes)
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.update { true }
            uris.forEach { repository.addImage(it) }
            _images.update { repository.getImages() }
            _isLoading.update { false }
        }
    }

    fun removeImage(file: File) {
        viewModelScope.launch {
            repository.removeImage(file)
            _images.update { repository.getImages() }
            // Auto-disable rotation if the pool is now empty
            if (_images.value.isEmpty() && settings.value.isEnabled) {
                setRotationEnabled(false)
            }
        }
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

    fun clearAll() {
        viewModelScope.launch {
            cancelRotation()
            preferences.setEnabled(false)
            repository.clearAll()
            _images.update { emptyList() }
        }
    }

    fun deleteSelected(files: Set<File>) {
        viewModelScope.launch {
            files.forEach { repository.removeImage(it) }
            _images.update { repository.getImages() }
            if (_images.value.isEmpty() && settings.value.isEnabled) {
                setRotationEnabled(false)
            }
        }
    }

    fun setWallpaperNow() {
        if (_images.value.isEmpty()) return
        viewModelScope.launch {
            _setNowState.update { SetNowState.SETTING }
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
            workManager.enqueue(request).await()
            try {
                val terminalStates = setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)
                val info = workManager.getWorkInfoByIdFlow(request.id)
                    .first { it?.state in terminalStates }
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        _setNowState.update { SetNowState.DONE }
                        kotlinx.coroutines.delay(2_000)
                        _setNowState.update { SetNowState.IDLE }
                    }
                    else -> {
                        _setNowState.update { SetNowState.ERROR }
                        kotlinx.coroutines.delay(2_000)
                        _setNowState.update { SetNowState.IDLE }
                    }
                }
            } catch (e: Exception) {
                _setNowState.update { SetNowState.ERROR }
                kotlinx.coroutines.delay(2_000)
                _setNowState.update { SetNowState.IDLE }
            }
        }
    }

    fun setSpecificWallpaper(file: File) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _setNowState.update { SetNowState.SETTING }
            try {
                val app = getApplication<Application>()
                val bitmap = loadScaledBitmapFromFile(app, file.absolutePath)
                    ?: run {
                        _setNowState.update { SetNowState.ERROR }
                        kotlinx.coroutines.delay(2_000)
                        _setNowState.update { SetNowState.IDLE }
                        return@launch
                    }
                val settingsVal = preferences.settings.first()
                val wallpaperManager = WallpaperManager.getInstance(app)
                val flags = when (settingsVal.wallpaperTarget) {
                    com.chrisalvis.rotato.data.WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
                    com.chrisalvis.rotato.data.WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
                    com.chrisalvis.rotato.data.WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(bitmap, null, true, flags)
                bitmap.recycle()
                _setNowState.update { SetNowState.DONE }
                kotlinx.coroutines.delay(2_000)
                _setNowState.update { SetNowState.IDLE }
            } catch (e: Exception) {
                _setNowState.update { SetNowState.ERROR }
                kotlinx.coroutines.delay(2_000)
                _setNowState.update { SetNowState.IDLE }
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

    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun exportSettings(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupState.update { BackupState.BUSY }
            runCatching {
                val sources = sourcesPrefs.sources.first()
                val prefs = preferences.settings.first()
                val nsfwMode = preferences.nsfwMode.first()
                val minRes = preferences.brainrotFilters.first().minResolution
                val aspectRatio = preferences.brainrotFilters.first().aspectRatio
                val malUsername = malPrefs.username.first()
                val malStatuses = malPrefs.filterStatuses.first()
                val malMinScore = malPrefs.filterMinScore.first()

                val sourcesArr = JSONArray().also { arr ->
                    sources.forEach { s ->
                        arr.put(JSONObject().apply {
                            put("type", s.type.name)
                            put("enabled", s.enabled)
                            put("apiKey", s.apiKey)
                            put("apiUser", s.apiUser)
                            put("tags", s.tags)
                            put("wallhavenPurity", s.wallhavenPurity)
                        })
                    }
                }
                val json = JSONObject().apply {
                    put("version", 1)
                    put("sources", sourcesArr)
                    put("preferences", JSONObject().apply {
                        put("intervalMinutes", prefs.intervalMinutes)
                        put("shuffleMode", prefs.shuffleMode)
                        put("wallpaperTarget", prefs.wallpaperTarget.name)
                        put("nsfwMode", nsfwMode)
                        put("minResolution", minRes.name)
                        put("aspectRatio", aspectRatio.name)
                    })
                    put("mal", JSONObject().apply {
                        put("username", malUsername)
                        put("filterStatuses", JSONArray(malStatuses.toList()))
                        put("filterMinScore", malMinScore)
                    })
                }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toString(2).toByteArray(Charsets.UTF_8))
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
                        val type = runCatching { SourceType.valueOf(o.getString("type")) }.getOrNull() ?: continue
                        sourcesPrefs.update(
                            type = type,
                            enabled = o.optBoolean("enabled", false),
                            apiKey = o.optString("apiKey", ""),
                            apiUser = o.optString("apiUser", ""),
                            tags = o.optString("tags", ""),
                            wallhavenPurity = o.optString("wallhavenPurity", "110")
                        )
                    }
                }

                // Restore preferences
                val prefsObj = json.optJSONObject("preferences")
                if (prefsObj != null) {
                    preferences.setIntervalMinutes(prefsObj.optInt("intervalMinutes", 60))
                    preferences.setShuffleMode(prefsObj.optBoolean("shuffleMode", true))
                    runCatching { WallpaperTarget.valueOf(prefsObj.optString("wallpaperTarget", "BOTH")) }
                        .getOrNull()?.let { preferences.setWallpaperTarget(it) }
                    preferences.setNsfwMode(prefsObj.optBoolean("nsfwMode", false))
                    runCatching { MinResolution.valueOf(prefsObj.optString("minResolution", "ANY")) }
                        .getOrNull()?.let { preferences.setMinResolution(it) }
                    runCatching { AspectRatio.valueOf(prefsObj.optString("aspectRatio", "ANY")) }
                        .getOrNull()?.let { preferences.setAspectRatio(it) }
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
    }
}

private fun sanitizeFilename(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

private fun loadScaledBitmapFromFile(app: Application, path: String): android.graphics.Bitmap? {
    val metrics = app.resources.displayMetrics
    val targetWidth = metrics.widthPixels
    val targetHeight = metrics.heightPixels
    val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOnly)
    var sampleSize = 1
    var w = boundsOnly.outWidth
    var h = boundsOnly.outHeight
    while (w / 2 >= targetWidth && h / 2 >= targetHeight) { w /= 2; h /= 2; sampleSize *= 2 }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(path, opts)
}
