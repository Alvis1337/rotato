package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.InterestProfile
import com.chrisalvis.rotato.data.LocalListsPreferences
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
    private val listsPrefs = LocalListsPreferences(app)

    val tagTiers: StateFlow<Map<String, TagTier>> = tastePrefs.tagTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val interestProfiles: StateFlow<List<InterestProfile>> = tastePrefs.interestProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /**
     * For each tag in the taste profile, the top 5 co-occurring tags from saved images.
     * Only populated for tags that appear in at least 2 images.
     */
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

    // UI state for the profile editor sheet
    private val _editingProfile = MutableStateFlow<InterestProfile?>(null)
    val editingProfile: StateFlow<InterestProfile?> = _editingProfile

    fun setTagTier(tag: String, tier: TagTier) {
        viewModelScope.launch { tastePrefs.setTagTier(tag, tier) }
    }

    fun removeTagTier(tag: String) {
        viewModelScope.launch { tastePrefs.removeTagTier(tag) }
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
