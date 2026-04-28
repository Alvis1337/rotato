package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class SchedulePreferences(private val context: Context) {

    companion object {
        private val SCHEDULE_KEY = stringPreferencesKey("schedule_entries_json")
    }

    val entries: Flow<List<ScheduleEntry>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { parseEntries(it[SCHEDULE_KEY] ?: "[]") }

    suspend fun upsert(entry: ScheduleEntry) {
        context.dataStore.edit { prefs ->
            val current = parseEntries(prefs[SCHEDULE_KEY] ?: "[]").toMutableList()
            val idx = current.indexOfFirst { it.id == entry.id }
            if (idx >= 0) {
                val existing = current[idx]
                // Preserve runtime diagnostic fields so that editing a schedule via the UI
                // doesn't clobber trigger history written concurrently by the alarm receiver.
                current[idx] = entry.copy(
                    lastFiredMs = existing.lastFiredMs,
                    lastFiredResult = existing.lastFiredResult,
                    lastLockedMs = existing.lastLockedMs,
                )
            } else {
                current.add(entry)
            }
            prefs[SCHEDULE_KEY] = serialize(current)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val updated = parseEntries(prefs[SCHEDULE_KEY] ?: "[]").filter { it.id != id }
            prefs[SCHEDULE_KEY] = serialize(updated)
        }
    }

    suspend fun recordLockedEvent(entryId: String) {
        context.dataStore.edit { prefs ->
            val entries = parseEntries(prefs[SCHEDULE_KEY] ?: "[]").map {
                if (it.id == entryId) it.copy(lastLockedMs = System.currentTimeMillis()) else it
            }
            prefs[SCHEDULE_KEY] = serialize(entries)
        }
    }

    suspend fun clearLockedEvent(entryId: String) {
        context.dataStore.edit { prefs ->
            val entries = parseEntries(prefs[SCHEDULE_KEY] ?: "[]").map {
                if (it.id == entryId) it.copy(lastLockedMs = 0L) else it
            }
            prefs[SCHEDULE_KEY] = serialize(entries)
        }
    }

    suspend fun recordTrigger(entryId: String, result: String) {
        context.dataStore.edit { prefs ->
            val entries = parseEntries(prefs[SCHEDULE_KEY] ?: "[]").map {
                if (it.id == entryId) it.copy(lastFiredMs = System.currentTimeMillis(), lastFiredResult = result) else it
            }
            prefs[SCHEDULE_KEY] = serialize(entries)
        }
    }

    private fun parseEntries(json: String): List<ScheduleEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val daysArr = o.getJSONArray("days")
            ScheduleEntry(
                id = o.getString("id"),
                days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                startHour = o.getInt("startHour"),
                startMinute = o.getInt("startMinute"),
                listId = o.getString("listId"),
                enabled = o.optBoolean("enabled", true),
                lastLockedMs = o.optLong("lastLockedMs", 0L),
                lastFiredMs = o.optLong("lastFiredMs", 0L),
                lastFiredResult = o.optString("lastFiredResult", ""),
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun serialize(entries: List<ScheduleEntry>): String =
        JSONArray().also { arr ->
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("days", JSONArray(e.days.toList()))
                    put("startHour", e.startHour)
                    put("startMinute", e.startMinute)
                    put("listId", e.listId)
                    put("enabled", e.enabled)
                    put("lastLockedMs", e.lastLockedMs)
                    put("lastFiredMs", e.lastFiredMs)
                    put("lastFiredResult", e.lastFiredResult)
                })
            }
        }.toString()
}
