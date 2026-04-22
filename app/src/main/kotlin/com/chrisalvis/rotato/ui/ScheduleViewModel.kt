package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.ScheduleEntry
import com.chrisalvis.rotato.data.SchedulePreferences
import com.chrisalvis.rotato.worker.ScheduleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val schedPrefs = SchedulePreferences(application)
    private val listPrefs = LocalListsPreferences(application)

    val entries: StateFlow<List<ScheduleEntry>> = schedPrefs.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lists: StateFlow<List<LocalList>> = listPrefs.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editEntry = MutableStateFlow<ScheduleEntry?>(null)
    val editEntry: StateFlow<ScheduleEntry?> = _editEntry.asStateFlow()

    fun startAdd() {
        val defaultList = lists.value.firstOrNull()?.id ?: ""
        _editEntry.update {
            ScheduleEntry(days = setOf(2, 3, 4, 5, 6), startHour = 8, startMinute = 0, listId = defaultList)
        }
    }

    fun startEdit(entry: ScheduleEntry) {
        _editEntry.update { entry }
    }

    fun dismissEdit() {
        _editEntry.update { null }
    }

    fun saveEdit(entry: ScheduleEntry) {
        viewModelScope.launch {
            schedPrefs.upsert(entry)
            ScheduleManager.schedule(getApplication(), entry)
        }
        _editEntry.update { null }
    }

    fun delete(entry: ScheduleEntry) {
        viewModelScope.launch {
            schedPrefs.delete(entry.id)
            ScheduleManager.cancel(getApplication(), entry.id)
        }
    }

    fun setEnabled(entry: ScheduleEntry, enabled: Boolean) {
        viewModelScope.launch {
            val updated = entry.copy(enabled = enabled)
            schedPrefs.upsert(updated)
            if (enabled) ScheduleManager.schedule(getApplication(), updated)
            else ScheduleManager.cancel(getApplication(), updated.id)
        }
    }
}
