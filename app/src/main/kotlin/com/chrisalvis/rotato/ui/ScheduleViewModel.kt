package com.chrisalvis.rotato.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.ScheduleEntry
import com.chrisalvis.rotato.data.SchedulePreferences
import com.chrisalvis.rotato.worker.ScheduleManager
import com.chrisalvis.rotato.worker.ScheduleReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val schedPrefs = SchedulePreferences(application)
    private val listPrefs = LocalListsPreferences(application)

    val entries: StateFlow<List<ScheduleEntry>> = schedPrefs.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lists: StateFlow<List<LocalList>> = listPrefs.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editEntry = MutableStateFlow<ScheduleEntry?>(null)
    val editEntry: StateFlow<ScheduleEntry?> = _editEntry.asStateFlow()

    /** Emits the name of a locked collection when saveEdit fires its schedule immediately. */
    private val _lockedListWarning = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val lockedListWarning: SharedFlow<String> = _lockedListWarning.asSharedFlow()

    /** Emits a one-shot result message from [triggerNow] or [saveEdit]. */
    private val _triggerResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val triggerResult: SharedFlow<String> = _triggerResult.asSharedFlow()

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
            val now = Calendar.getInstance()
            val todayDow = now.get(Calendar.DAY_OF_WEEK)
            val triggerTodayMs = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, entry.startHour)
                set(Calendar.MINUTE, entry.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (entry.enabled && todayDow in entry.days && triggerTodayMs <= now.timeInMillis) {
                withContext(Dispatchers.IO) {
                    try {
                        val allEntries = schedPrefs.entries.first()
                        val targetList = listPrefs.lists.first().find { it.id == entry.listId }
                        val sessionUnlocked = (getApplication() as? RotatoApp)
                            ?.unlockedListIds?.value?.contains(entry.listId) == true
                        if (targetList?.isLocked == true && !sessionUnlocked) {
                            _lockedListWarning.tryEmit(targetList.name)
                            // applyEntry wasn't called, so arm the next alarm manually.
                            ScheduleManager.schedule(getApplication(), entry)
                        } else {
                            // applyEntry arms the next alarm internally — no duplicate schedule() call.
                            val result = ScheduleReceiver.applyEntry(getApplication(), entry, allEntries, schedPrefs, listPrefs)
                            _triggerResult.tryEmit(result)
                        }
                    } catch (e: Exception) {
                        Log.e("ScheduleViewModel", "Failed to apply entry ${entry.id} in saveEdit", e)
                        runCatching { schedPrefs.recordTrigger(entry.id, "error: ${e.javaClass.simpleName}") }
                        ScheduleManager.schedule(getApplication(), entry)
                    }
                }
            } else {
                ScheduleManager.schedule(getApplication(), entry)
            }
        }
        _editEntry.update { null }
    }

    /**
     * Manually apply this schedule entry right now, bypassing the time-window check.
     * If the collection is still locked and not session-unlocked, emits a prompt via [triggerResult].
     */
    fun triggerNow(entry: ScheduleEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val targetList = listPrefs.lists.first().find { it.id == entry.listId }
                    val sessionUnlocked = (getApplication() as? RotatoApp)
                        ?.unlockedListIds?.value?.contains(entry.listId) == true
                    if (targetList?.isLocked == true && !sessionUnlocked) {
                        _triggerResult.tryEmit("Unlock \"${targetList.name}\" in Collections first")
                        return@withContext
                    }
                    val allEntries = schedPrefs.entries.first()
                    val result = ScheduleReceiver.applyEntry(getApplication(), entry, allEntries, schedPrefs, listPrefs)
                    _triggerResult.tryEmit(result)
                } catch (e: Exception) {
                    Log.e("ScheduleViewModel", "triggerNow failed for ${entry.id}", e)
                    _triggerResult.tryEmit("Error: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
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
