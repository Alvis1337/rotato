package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rotato_prefs")

class RotatoPreferences(private val context: Context) {

    companion object {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val LAST_ROTATION_MS = longPreferencesKey("last_rotation_ms")
    }

    val settings: Flow<RotatoSettings> = context.dataStore.data.map { prefs ->
        RotatoSettings(
            isEnabled = prefs[IS_ENABLED] ?: false,
            intervalMinutes = prefs[INTERVAL_MINUTES] ?: 60,
            shuffleMode = prefs[SHUFFLE_MODE] ?: true,
            currentIndex = prefs[CURRENT_INDEX] ?: 0
        )
    }

    val lastRotationMs: Flow<Long> = context.dataStore.data.map { it[LAST_ROTATION_MS] ?: 0L }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_ENABLED] = enabled }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[INTERVAL_MINUTES] = minutes }
    }

    suspend fun setShuffleMode(shuffle: Boolean) {
        context.dataStore.edit { it[SHUFFLE_MODE] = shuffle }
    }

    suspend fun setCurrentIndex(index: Int) {
        context.dataStore.edit { it[CURRENT_INDEX] = index }
    }

    suspend fun recordRotation() {
        context.dataStore.edit { it[LAST_ROTATION_MS] = System.currentTimeMillis() }
    }
}
