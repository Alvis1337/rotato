package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.CollectionDoctor
import com.chrisalvis.rotato.data.DoctorReport
import com.chrisalvis.rotato.data.HdMatch
import com.chrisalvis.rotato.data.HdUpgrade
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.OfflinePack
import com.chrisalvis.rotato.data.PhotoTagger
import com.chrisalvis.rotato.data.PlaceRule
import com.chrisalvis.rotato.data.PlaceRules
import com.chrisalvis.rotato.data.StorageItem
import com.chrisalvis.rotato.data.StorageKind
import com.chrisalvis.rotato.data.StorageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A long-running tool's progress: [done] of [total], or a finished [message]. */
data class ToolProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false, val message: String? = null)

/** Backs the Tools screen: storage, collection doctor, HD upgrade, photo tags, offline pack, place rules. */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val listPrefs = LocalListsPreferences(app)

    val collections: StateFlow<List<LocalList>> = listPrefs.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Storage cleaner ---
    private val _storage = MutableStateFlow<List<StorageItem>>(emptyList())
    val storage: StateFlow<List<StorageItem>> = _storage.asStateFlow()
    private val _storageBusy = MutableStateFlow(false)
    val storageBusy: StateFlow<Boolean> = _storageBusy.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun toastShown() = _toast.update { null }

    fun scanStorage() {
        viewModelScope.launch {
            _storageBusy.update { true }
            _storage.update { runCatching { StorageTools.scan(app) }.getOrDefault(emptyList()) }
            _storageBusy.update { false }
        }
    }

    fun clear(kind: StorageKind) {
        viewModelScope.launch {
            _storageBusy.update { true }
            val freed = runCatching { StorageTools.clear(app, kind) }.getOrDefault(0L)
            _toast.update { "Freed ${StorageTools.format(freed)} from ${kind.label.lowercase()}" }
            _storage.update { runCatching { StorageTools.scan(app) }.getOrDefault(emptyList()) }
            _storageBusy.update { false }
        }
    }

    // --- Collection doctor ---
    private val _doctor = MutableStateFlow(ToolProgress())
    val doctor: StateFlow<ToolProgress> = _doctor.asStateFlow()
    private val _doctorReport = MutableStateFlow<DoctorReport?>(null)
    val doctorReport: StateFlow<DoctorReport?> = _doctorReport.asStateFlow()

    fun checkCollections() {
        if (_doctor.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            _doctorReport.update { null }
            _doctor.update { ToolProgress(running = true) }
            val report = CollectionDoctor.check(app) { done, total -> _doctor.update { it.copy(done = done, total = total) } }
            _doctorReport.update { report }
            _doctor.update {
                ToolProgress(message = when {
                    report.dead.isEmpty() && report.unchecked == 0 -> "All ${report.checked} saved wallpapers are fine"
                    report.dead.isEmpty() -> "Nothing broken found. ${report.unchecked} couldn't be reached, so check again online"
                    else -> "${report.dead.size} of ${report.checked} are broken" +
                        (if (report.unchecked > 0) ", ${report.unchecked} couldn't be reached" else "")
                })
            }
        }
    }

    fun repairDead() {
        val report = _doctorReport.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _doctor.update { ToolProgress(running = true) }
            val fixed = runCatching { CollectionDoctor.repair(app, report.dead) }.getOrDefault(emptySet())
            val left = report.dead.filter { it.id !in fixed }
            _doctorReport.update { report.copy(dead = left) }
            _doctor.update {
                ToolProgress(message = "Repaired ${fixed.size}. " + if (left.isEmpty()) "Nothing left broken" else "${left.size} are gone from their site")
            }
        }
    }

    fun removeDead() {
        val report = _doctorReport.value ?: return
        viewModelScope.launch {
            listPrefs.removeWallpapers(report.dead.mapTo(HashSet()) { it.id })
            _doctorReport.update { report.copy(dead = emptyList()) }
            _doctor.update { ToolProgress(message = "Removed ${report.dead.size} broken wallpapers") }
        }
    }

    // --- HD upgrade ---
    private val _hd = MutableStateFlow(ToolProgress())
    val hd: StateFlow<ToolProgress> = _hd.asStateFlow()
    private val _hdMatches = MutableStateFlow<List<HdMatch>>(emptyList())
    val hdMatches: StateFlow<List<HdMatch>> = _hdMatches.asStateFlow()

    fun findHd() {
        if (_hd.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            _hdMatches.update { emptyList() }
            _hd.update { ToolProgress(running = true) }
            val candidates = runCatching { HdUpgrade.candidates(app) }.getOrDefault(emptyList())
            if (candidates.isEmpty()) {
                _hd.update { ToolProgress(message = "Everything saved is already sharp enough for this screen") }
                return@launch
            }
            _hd.update { it.copy(total = candidates.size) }
            try {
                val matches = HdUpgrade.findAll(app, candidates) { done, total -> _hd.update { it.copy(done = done, total = total) } }
                _hdMatches.update { matches }
                _hd.update {
                    ToolProgress(message = if (matches.isEmpty()) "Checked ${candidates.size} low-res wallpapers; no sharper copies found"
                        else "Found sharper copies for ${matches.size} of ${candidates.size}")
                }
            } catch (e: HdUpgrade.NeedsAccount) {
                _hd.update { ToolProgress(message = e.message) }
            }
        }
    }

    fun applyHd(selected: List<HdMatch>) {
        viewModelScope.launch(Dispatchers.IO) {
            _hd.update { ToolProgress(running = true) }
            runCatching { HdUpgrade.apply(app, selected) }
            _hdMatches.update { it - selected.toSet() }
            _hd.update { ToolProgress(message = "Upgraded ${selected.size} wallpapers") }
        }
    }

    // --- Auto-tag photos ---
    private val _tagger = MutableStateFlow(ToolProgress())
    val tagger: StateFlow<ToolProgress> = _tagger.asStateFlow()
    private val _untagged = MutableStateFlow<Int?>(null)
    val untagged: StateFlow<Int?> = _untagged.asStateFlow()

    fun countUntagged() {
        viewModelScope.launch { _untagged.update { runCatching { PhotoTagger.pending(app) }.getOrNull() } }
    }

    fun tagPhotos() {
        if (_tagger.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            _tagger.update { ToolProgress(running = true) }
            val tagged = runCatching {
                PhotoTagger.tagAll(app) { done, total -> _tagger.update { it.copy(done = done, total = total) } }
            }
            _tagger.update {
                ToolProgress(message = tagged.fold(
                    { n -> if (n == 0) "Nothing new to tag" else "Tagged $n photos" },
                    { "Couldn't tag photos. Google Play services may still be downloading the model" },
                ))
            }
            countUntagged()
        }
    }

    // --- Offline pack ---
    private val _offlineEnabled = MutableStateFlow(OfflinePack.enabled(app))
    val offlineEnabled: StateFlow<Boolean> = _offlineEnabled.asStateFlow()
    fun setOfflineEnabled(on: Boolean) {
        OfflinePack.setEnabled(app, on)
        _offlineEnabled.update { on }
    }
    fun refreshOffline() {
        OfflinePack.refreshNow(app)
        _toast.update { "Updating the offline pack next time you're on Wi-Fi" }
    }

    // --- Place rules ---
    private val _placeEnabled = MutableStateFlow(PlaceRules.enabled(app))
    val placeEnabled: StateFlow<Boolean> = _placeEnabled.asStateFlow()
    private val _placeRules = MutableStateFlow(PlaceRules.rules(app))
    val placeRules: StateFlow<List<PlaceRule>> = _placeRules.asStateFlow()
    private val _elsewhere = MutableStateFlow(PlaceRules.elsewhere(app))
    val elsewhere: StateFlow<String> = _elsewhere.asStateFlow()

    fun currentSsid(): String? = PlaceRules.currentSsid(app)

    fun setPlaceEnabled(on: Boolean) {
        PlaceRules.setEnabled(app, on)
        _placeEnabled.update { on }
    }

    fun addRule(ssid: String, collectionId: String) {
        val updated = _placeRules.value.filter { it.ssid != ssid } + PlaceRule(ssid, collectionId)
        PlaceRules.setRules(app, updated)
        _placeRules.update { updated }
    }

    fun removeRule(ssid: String) {
        val updated = _placeRules.value.filter { it.ssid != ssid }
        PlaceRules.setRules(app, updated)
        _placeRules.update { updated }
    }

    fun setElsewhere(collectionId: String) {
        PlaceRules.setElsewhere(app, collectionId)
        _elsewhere.update { collectionId }
    }
}
