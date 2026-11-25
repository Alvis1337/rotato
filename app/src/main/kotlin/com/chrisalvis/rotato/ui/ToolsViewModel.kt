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
import com.chrisalvis.rotato.data.ArtistRule
import com.chrisalvis.rotato.data.NowPlaying
import com.chrisalvis.rotato.data.OfflinePack
import com.chrisalvis.rotato.data.PhotoTagger
import com.chrisalvis.rotato.data.PlaceRule
import com.chrisalvis.rotato.data.PlaceRules
import com.chrisalvis.rotato.data.StorageItem
import com.chrisalvis.rotato.data.StorageKind
import com.chrisalvis.rotato.data.StorageTools
import com.chrisalvis.rotato.data.WeatherKind
import com.chrisalvis.rotato.data.WeatherPlace
import com.chrisalvis.rotato.data.WeatherRules
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

/** Backs the Tools screen: storage, collection doctor, HD upgrade, photo tags, offline pack, place and weather rules. */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val listPrefs = LocalListsPreferences(app)
    private val rotatoPrefs = com.chrisalvis.rotato.data.RotatoPreferences(app)

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
            rotatoPrefs.setBrokenEntryIds(report.dead.map { it.id })
            _doctor.update {
                val fixedNote = if (report.repaired > 0) " Updated ${report.repaired} links that had moved." else ""
                ToolProgress(message = when {
                    report.dead.isEmpty() && report.unchecked == 0 -> "All ${report.checked} saved wallpapers are fine.$fixedNote"
                    report.dead.isEmpty() -> "Nothing broken found. ${report.unchecked} couldn't be checked; their site didn't answer.$fixedNote"
                    else -> "${report.dead.size} of ${report.checked} are gone from their site" +
                        (if (report.unchecked > 0) ", ${report.unchecked} couldn't be checked" else "") + ".$fixedNote"
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
            rotatoPrefs.setBrokenEntryIds(left.map { it.id })
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
            rotatoPrefs.setBrokenEntryIds(emptyList())
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

    // --- Weather rules ---
    private val _weatherEnabled = MutableStateFlow(WeatherRules.enabled(app))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled.asStateFlow()
    private val _weatherRules = MutableStateFlow(WeatherRules.rules(app))
    val weatherRules: StateFlow<Map<WeatherKind, String>> = _weatherRules.asStateFlow()
    private val _weatherOtherwise = MutableStateFlow(WeatherRules.otherwise(app))
    val weatherOtherwise: StateFlow<String> = _weatherOtherwise.asStateFlow()
    private val _weatherPlace = MutableStateFlow(WeatherRules.place(app))
    val weatherPlace: StateFlow<WeatherPlace?> = _weatherPlace.asStateFlow()
    private val _weatherNow = MutableStateFlow(WeatherRules.cached(app))
    val weatherNow: StateFlow<Triple<WeatherKind, Double, Long>?> = _weatherNow.asStateFlow()
    private val _placeResults = MutableStateFlow<List<WeatherPlace>>(emptyList())
    val placeResults: StateFlow<List<WeatherPlace>> = _placeResults.asStateFlow()
    private val _placeSearching = MutableStateFlow(false)
    val placeSearching: StateFlow<Boolean> = _placeSearching.asStateFlow()

    fun setWeatherEnabled(on: Boolean) {
        WeatherRules.setEnabled(app, on)
        _weatherEnabled.update { on }
        if (on) refreshWeather()
    }

    fun setWeatherRule(kind: WeatherKind, collectionId: String?) {
        WeatherRules.setRule(app, kind, collectionId)
        _weatherRules.update { WeatherRules.rules(app) }
    }

    fun setWeatherOtherwise(collectionId: String) {
        WeatherRules.setOtherwise(app, collectionId)
        _weatherOtherwise.update { collectionId }
    }

    fun searchPlaces(query: String) {
        viewModelScope.launch {
            _placeSearching.update { true }
            val found = WeatherRules.searchPlaces(query)
            _placeResults.update { found }
            _placeSearching.update { false }
            if (found.isEmpty()) _toast.update { "No places found for \"$query\"" }
        }
    }

    fun pickPlace(place: WeatherPlace, fromDevice: Boolean = false) {
        WeatherRules.setPlace(app, place, fromDevice)
        _weatherPlace.update { WeatherRules.place(app) }
        _placeResults.update { emptyList() }
        refreshWeather()
    }

    /** Uses the phone's last known location. False when there's no fix to use yet. */
    fun useDeviceLocation(): Boolean {
        val here = WeatherRules.deviceLocation(app) ?: return false
        pickPlace(here, fromDevice = true)
        return true
    }

    fun refreshWeather() {
        viewModelScope.launch(Dispatchers.IO) {
            WeatherRules.currentWeather(app, force = true)
            _weatherNow.update { WeatherRules.cached(app) }
            runCatching { WeatherRules.apply(app) }
        }
    }

    // --- Now playing ---
    private val _nowPlayingEnabled = MutableStateFlow(NowPlaying.enabled(app))
    val nowPlayingEnabled: StateFlow<Boolean> = _nowPlayingEnabled.asStateFlow()
    private val _albumArt = MutableStateFlow(NowPlaying.albumArt(app))
    val albumArt: StateFlow<Boolean> = _albumArt.asStateFlow()
    private val _artistRules = MutableStateFlow(NowPlaying.rules(app))
    val artistRules: StateFlow<List<ArtistRule>> = _artistRules.asStateFlow()

    fun lastArtist(): String = NowPlaying.lastArtist(app)

    fun setNowPlayingEnabled(on: Boolean) {
        NowPlaying.setEnabled(app, on)
        _nowPlayingEnabled.update { on }
    }

    fun setAlbumArt(on: Boolean) {
        NowPlaying.setAlbumArt(app, on)
        _albumArt.update { on }
    }

    fun addArtistRule(artist: String, collectionId: String) {
        val name = artist.trim()
        if (name.isEmpty()) return
        val updated = _artistRules.value.filter { !it.artist.equals(name, ignoreCase = true) } + ArtistRule(name, collectionId)
        NowPlaying.setRules(app, updated)
        _artistRules.update { updated }
    }

    fun removeArtistRule(artist: String) {
        val updated = _artistRules.value.filter { it.artist != artist }
        NowPlaying.setRules(app, updated)
        _artistRules.update { updated }
    }
}
