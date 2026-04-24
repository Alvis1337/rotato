package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rotato_prefs")

class RotatoPreferences(private val context: Context) {

    companion object {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val LAST_ROTATION_MS = longPreferencesKey("last_rotation_ms")
        val WALLPAPER_TARGET = stringPreferencesKey("wallpaper_target")
        val HISTORY_JSON = stringPreferencesKey("wallpaper_history_json")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val NSFW_MODE = booleanPreferencesKey("nsfw_mode")
        val MIN_RESOLUTION = stringPreferencesKey("min_resolution")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val PHONE_WIDTH_PARTS = intPreferencesKey("phone_width_parts")
        val PHONE_HEIGHT_PARTS = intPreferencesKey("phone_height_parts")
        val PHONE_SCREEN_WIDTH = intPreferencesKey("phone_screen_width")
        val PHONE_SCREEN_HEIGHT = intPreferencesKey("phone_screen_height")
        val USE_MAL_FILTER = booleanPreferencesKey("use_mal_filter")
        val GLOBAL_BLACKLIST = stringPreferencesKey("global_blacklist_tags")
        val BLOCKED_URLS = stringPreferencesKey("blocked_urls")
        val DISCOVER_BATCH_SIZE = intPreferencesKey("discover_batch_size")
    }

    val settings: Flow<RotatoSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
        RotatoSettings(
            isEnabled = prefs[IS_ENABLED] ?: false,
            intervalMinutes = prefs[INTERVAL_MINUTES] ?: 60,
            shuffleMode = prefs[SHUFFLE_MODE] ?: true,
            currentIndex = prefs[CURRENT_INDEX] ?: 0,
            wallpaperTarget = prefs[WALLPAPER_TARGET]?.let {
                runCatching { WallpaperTarget.valueOf(it) }.getOrNull()
            } ?: WallpaperTarget.BOTH
        )
    }

    val lastRotationMs: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_ROTATION_MS] ?: 0L }

    val historyJson: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[HISTORY_JSON] ?: "[]" }

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

    suspend fun setWallpaperTarget(target: WallpaperTarget) {
        context.dataStore.edit { it[WALLPAPER_TARGET] = target.name }
    }

    suspend fun recordRotation() {
        context.dataStore.edit { it[LAST_ROTATION_MS] = System.currentTimeMillis() }
    }

    suspend fun setHistoryJson(json: String) {
        context.dataStore.edit { it[HISTORY_JSON] = json }
    }

    val nsfwMode: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[NSFW_MODE] ?: false }

    suspend fun setNsfwMode(enabled: Boolean) {
        context.dataStore.edit { it[NSFW_MODE] = enabled }
    }

    val brainrotFilters: Flow<BrainrotFilters> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            BrainrotFilters(
                minResolution = prefs[MIN_RESOLUTION]?.let {
                    runCatching { MinResolution.valueOf(it) }.getOrNull()
                } ?: MinResolution.ANY,
                aspectRatio = prefs[ASPECT_RATIO]?.let {
                    runCatching { AspectRatio.valueOf(it) }.getOrNull()
                } ?: AspectRatio.ANY,
                phoneWidthParts = prefs[PHONE_WIDTH_PARTS] ?: 0,
                phoneHeightParts = prefs[PHONE_HEIGHT_PARTS] ?: 0,
                phoneScreenWidth = prefs[PHONE_SCREEN_WIDTH] ?: 0,
                phoneScreenHeight = prefs[PHONE_SCREEN_HEIGHT] ?: 0,
                useMalFilter = prefs[USE_MAL_FILTER] ?: true,
            )
        }

    suspend fun setMinResolution(value: MinResolution) {
        context.dataStore.edit { it[MIN_RESOLUTION] = value.name }
    }

    suspend fun setAspectRatio(value: AspectRatio) {
        context.dataStore.edit { it[ASPECT_RATIO] = value.name }
    }

    suspend fun setPhoneRatio(widthParts: Int, heightParts: Int) {
        context.dataStore.edit {
            it[PHONE_WIDTH_PARTS] = widthParts
            it[PHONE_HEIGHT_PARTS] = heightParts
        }
    }

    suspend fun setPhoneScreen(width: Int, height: Int) {
        context.dataStore.edit {
            it[PHONE_SCREEN_WIDTH] = width
            it[PHONE_SCREEN_HEIGHT] = height
        }
    }

    suspend fun setUseMalFilter(enabled: Boolean) {
        context.dataStore.edit { it[USE_MAL_FILTER] = enabled }
    }

    /** Comma-separated list of globally blacklisted tags. */
    val globalBlacklist: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[GLOBAL_BLACKLIST] ?: ""
            if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }

    suspend fun setGlobalBlacklist(tags: Set<String>) {
        context.dataStore.edit { it[GLOBAL_BLACKLIST] = tags.joinToString(",") }
    }

    val blockedUrls: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[BLOCKED_URLS] ?: ""
            if (raw.isBlank()) emptySet() else raw.split("\n").filter { it.isNotBlank() }.toSet()
        }

    suspend fun blockUrl(url: String) {
        if (url.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[BLOCKED_URLS] ?: ""
            val set = if (existing.isBlank()) mutableSetOf() else existing.split("\n").filter { it.isNotBlank() }.toMutableSet()
            set.add(url)
            prefs[BLOCKED_URLS] = set.joinToString("\n")
        }
    }

    /** Number of items to fetch per scroll-load on the Discover screen. Default 20. */
    val discoverBatchSize: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[DISCOVER_BATCH_SIZE] ?: 20 }

    suspend fun setDiscoverBatchSize(size: Int) {
        context.dataStore.edit { it[DISCOVER_BATCH_SIZE] = size }
    }

    // null = DataStore hasn't emitted yet (initialValue in collectAsStateWithLifecycle)
    // After first emission this is always true/false, never null:
    //   - true  if SETUP_DONE=true, OR if other prefs exist (dirty install over old version)
    //   - false if fresh install with empty DataStore
    val setupDone: Flow<Boolean?> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[SETUP_DONE] ?: prefs.asMap().isNotEmpty() }

    suspend fun setSetupDone(done: Boolean = true) {
        context.dataStore.edit { it[SETUP_DONE] = done }
    }
}
