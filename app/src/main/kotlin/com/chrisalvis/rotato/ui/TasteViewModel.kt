package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.InterestProfile
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.TagTier
import com.chrisalvis.rotato.data.TastePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TasteViewModel(app: Application) : AndroidViewModel(app) {

    private val tastePrefs = TastePreferences(app)
    private val rotatoPrefs = RotatoPreferences(app)
    private val listsPrefs = LocalListsPreferences(app)

    val sfwTagTiers: StateFlow<Map<String, TagTier>> = tastePrefs.sfwTagTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val nsfwTagTiers: StateFlow<Map<String, TagTier>> = tastePrefs.nsfwTagTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val nsfwMode: StateFlow<Boolean> = rotatoPrefs.nsfwMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val interestProfiles: StateFlow<List<InterestProfile>> = tastePrefs.interestProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Combined map for My Taste tab — both SFW and NSFW tiers merged for display. */
    val allTagTiers: StateFlow<Map<String, TagTier>> = combine(
        tastePrefs.sfwTagTiers, tastePrefs.nsfwTagTiers
    ) { sfw, nsfw -> sfw + nsfw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Tag → frequency count, sorted descending. Derived from all saved LocalWallpaperEntry tags. */
    val tasteProfile: StateFlow<List<Pair<String, Int>>> = listsPrefs.allWallpapers
        .map { entries ->
            entries.flatMap { it.tags }
                .filter { it.isNotBlank() }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(100)
                .map { it.key to it.value }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coTagMap: StateFlow<Map<String, List<String>>> = listsPrefs.allWallpapers
        .map { entries ->
            val tagToImages = mutableMapOf<String, MutableSet<String>>()
            entries.forEach { entry ->
                entry.tags.forEach { tag ->
                    tagToImages.getOrPut(tag.lowercase()) { mutableSetOf() }.add(entry.id)
                }
            }
            val imageToTags = mutableMapOf<String, List<String>>()
            entries.forEach { entry ->
                imageToTags[entry.id] = entry.tags.map { it.lowercase() }
            }
            tagToImages
                .filter { it.value.size >= 2 }
                .mapValues { (tag, imageIds) ->
                    imageIds.flatMap { imageToTags[it] ?: emptyList() }
                        .filter { it != tag }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { it.key }
                }
                .filter { it.value.isNotEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _editingProfile = MutableStateFlow<InterestProfile?>(null)
    val editingProfile: StateFlow<InterestProfile?> = _editingProfile

    fun setTagTier(tag: String, tier: TagTier, isNsfw: Boolean = false) {
        viewModelScope.launch { tastePrefs.setTagTier(tag, tier, isNsfw) }
    }

    fun removeTagTier(tag: String, isNsfw: Boolean = false) {
        viewModelScope.launch { tastePrefs.removeTagTier(tag, isNsfw) }
    }

    fun moveTagTier(tag: String, tier: TagTier, fromIsNsfw: Boolean) {
        viewModelScope.launch {
            tastePrefs.removeTagTier(tag, fromIsNsfw)
            tastePrefs.setTagTier(tag, tier, !fromIsNsfw)
        }
    }

    fun saveProfile(profile: InterestProfile) {
        viewModelScope.launch {
            tastePrefs.saveProfile(profile)
            _editingProfile.update { null }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch { tastePrefs.deleteProfile(profileId) }
    }

    fun toggleProfile(profileId: String) {
        viewModelScope.launch { tastePrefs.toggleProfile(profileId) }
    }

    fun startEditProfile(profile: InterestProfile?) {
        _editingProfile.update { profile ?: InterestProfile(name = "") }
    }

    fun cancelEditProfile() {
        _editingProfile.update { null }
    }
}
