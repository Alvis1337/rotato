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
import org.json.JSONArray

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
        val HANDS_FREE_INTERVAL = intPreferencesKey("hands_free_interval")
        val GLOBAL_BLACKLIST = stringPreferencesKey("global_blacklist_tags")
        val BLOCKED_URLS = stringPreferencesKey("blocked_urls")
        val BLOCKED_IMAGE_KEYS = stringPreferencesKey("blocked_image_keys_json")
        val DISCOVER_BATCH_SIZE = intPreferencesKey("discover_batch_size")
        val AUTO_PAUSE_NIGHT = booleanPreferencesKey("auto_pause_night")
        val AUTO_PAUSE_START = intPreferencesKey("auto_pause_start_hour")
        val AUTO_PAUSE_END = intPreferencesKey("auto_pause_end_hour")
        val AUTO_PAUSE_CHARGING = booleanPreferencesKey("auto_pause_charging")
        val CHARGING_TRIGGER_ENABLED = booleanPreferencesKey("charging_trigger_enabled")
        val AUTO_FAVORITE_ENABLED = booleanPreferencesKey("auto_favorite_enabled")
        val AUTO_FAVORITE_MINUTES = intPreferencesKey("auto_favorite_minutes")
        val LAST_WALLPAPER_THUMB_URL = stringPreferencesKey("last_wallpaper_thumb_url")
        val LAST_WALLPAPER_FULL_URL = stringPreferencesKey("last_wallpaper_full_url")
        val LAST_WALLPAPER_SOURCE = stringPreferencesKey("last_wallpaper_source")
        val LAST_WALLPAPER_SET_MS = longPreferencesKey("last_wallpaper_set_ms")
        val TOTAL_ROTATIONS = longPreferencesKey("total_rotations")
        val ROTATION_ERRORS = stringPreferencesKey("rotation_errors_json")
        val PINNED_SEARCHES = stringPreferencesKey("pinned_searches_json")
        val DISCOVER_MODE = stringPreferencesKey("discover_mode")
        val WIDGET_COLLECTION_ID = stringPreferencesKey("widget_collection_id")
        val SEEN_WALLPAPER_KEYS = stringPreferencesKey("seen_wallpaper_keys_json")
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

    val widgetCollectionId: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[WIDGET_COLLECTION_ID] ?: "" }

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

    suspend fun recordRotationAndIncrement() {
        context.dataStore.edit { prefs ->
            prefs[LAST_ROTATION_MS] = System.currentTimeMillis()
            prefs[TOTAL_ROTATIONS] = (prefs[TOTAL_ROTATIONS] ?: 0L) + 1L
        }
    }

    suspend fun setHistoryJson(json: String) {
        context.dataStore.edit { it[HISTORY_JSON] = json }
    }

    suspend fun setWidgetCollectionId(id: String) {
        context.dataStore.edit { it[WIDGET_COLLECTION_ID] = id }
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
                discoverMode = prefs[DISCOVER_MODE]?.let {
                    runCatching { DiscoverMode.valueOf(it) }.getOrNull()
                } ?: DiscoverMode.RANDOM,
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

    val blockedImageKeys: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val json = prefs[BLOCKED_IMAGE_KEYS] ?: return@map emptySet()
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }

    suspend fun addBlockedImageKey(key: String) {
        if (key.isBlank()) return
        context.dataStore.edit { prefs ->
            val existing = try {
                val arr = JSONArray(prefs[BLOCKED_IMAGE_KEYS] ?: "[]")
                (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
            } catch (_: Exception) {
                mutableSetOf()
            }
            existing.add(key)
            val arr = JSONArray().apply { existing.forEach { put(it) } }
            prefs[BLOCKED_IMAGE_KEYS] = arr.toString()
        }
    }

    /** Number of items to fetch per scroll-load on the Discover screen. Default 20. */
    val discoverBatchSize: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[DISCOVER_BATCH_SIZE] ?: 20 }

    val seenWallpaperKeys: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val json = prefs[SEEN_WALLPAPER_KEYS] ?: return@map emptySet()
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }

    suspend fun setDiscoverBatchSize(size: Int) {
        context.dataStore.edit { it[DISCOVER_BATCH_SIZE] = size }
    }

    suspend fun addSeenWallpaperKeys(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = try {
                val arr = JSONArray(prefs[SEEN_WALLPAPER_KEYS] ?: "[]")
                (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
            } catch (_: Exception) {
                mutableSetOf()
            }
            existing.addAll(keys)
            val capped = if (existing.size > 2000) existing.toList().takeLast(2000).toSet() else existing
            val arr = JSONArray().apply { capped.forEach { put(it) } }
            prefs[SEEN_WALLPAPER_KEYS] = arr.toString()
        }
    }

    suspend fun clearSeenWallpaperKeys() {
        context.dataStore.edit { it.remove(SEEN_WALLPAPER_KEYS) }
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

    val handsFreeInterval: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[HANDS_FREE_INTERVAL] ?: 5 }

    suspend fun setHandsFreeInterval(secs: Int) {
        context.dataStore.edit { it[HANDS_FREE_INTERVAL] = secs }
    }

    val autoPauseSettings: Flow<AutoPauseSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AutoPauseSettings(
                nightEnabled = prefs[AUTO_PAUSE_NIGHT] ?: false,
                nightStartHour = prefs[AUTO_PAUSE_START] ?: 22,
                nightEndHour = prefs[AUTO_PAUSE_END] ?: 7,
                chargingEnabled = prefs[AUTO_PAUSE_CHARGING] ?: false
            )
        }

    suspend fun setAutoPauseNight(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PAUSE_NIGHT] = enabled }
    }

    suspend fun setAutoPauseNightHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[AUTO_PAUSE_START] = start
            it[AUTO_PAUSE_END] = end
        }
    }

    suspend fun setAutoPauseCharging(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PAUSE_CHARGING] = enabled }
    }

    val chargingTriggerEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[CHARGING_TRIGGER_ENABLED] ?: false }

    suspend fun setChargingTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CHARGING_TRIGGER_ENABLED] = enabled }
    }

    val autoFavoriteEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUTO_FAVORITE_ENABLED] ?: false }

    suspend fun setAutoFavoriteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_FAVORITE_ENABLED] = enabled }
    }

    val autoFavoriteMinutes: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[AUTO_FAVORITE_MINUTES] ?: 120 }

    suspend fun setAutoFavoriteMinutes(minutes: Int) {
        context.dataStore.edit { it[AUTO_FAVORITE_MINUTES] = minutes }
    }

    val lastWallpaperThumbUrl: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_WALLPAPER_THUMB_URL] ?: "" }

    val lastWallpaperFullUrl: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_WALLPAPER_FULL_URL] ?: "" }

    val lastWallpaperSource: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_WALLPAPER_SOURCE] ?: "" }

    val lastWallpaperSetMs: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[LAST_WALLPAPER_SET_MS] ?: 0L }

    suspend fun setLastWallpaperState(
        thumbUrl: String,
        fullUrl: String,
        source: String,
        setMs: Long,
    ) {
        context.dataStore.edit {
            it[LAST_WALLPAPER_THUMB_URL] = thumbUrl
            it[LAST_WALLPAPER_FULL_URL] = fullUrl
            it[LAST_WALLPAPER_SOURCE] = source
            it[LAST_WALLPAPER_SET_MS] = setMs
        }
    }

    val totalRotations: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[TOTAL_ROTATIONS] ?: 0L }

    val rotationErrors: Flow<List<RotationError>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { rotationErrorsFromJson(it[ROTATION_ERRORS] ?: "[]") }

    suspend fun addRotationError(error: RotationError) {
        context.dataStore.edit { prefs ->
            val current = rotationErrorsFromJson(prefs[ROTATION_ERRORS] ?: "[]").toMutableList()
            current.add(0, error)
            prefs[ROTATION_ERRORS] = current.take(10).toErrorJson()
        }
    }

    suspend fun clearRotationErrors() {
        context.dataStore.edit { it[ROTATION_ERRORS] = "[]" }
    }

    suspend fun setDiscoverMode(mode: DiscoverMode) {
        context.dataStore.edit { it[DISCOVER_MODE] = mode.name }
    }

    val pinnedSearches: Flow<List<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            try {
                val arr = JSONArray(prefs[PINNED_SEARCHES] ?: "[]")
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun addPinnedSearch(query: String) {
        if (query.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = try {
                val arr = JSONArray(prefs[PINNED_SEARCHES] ?: "[]")
                (0 until arr.length()).map { arr.optString(it) }.toMutableList()
            } catch (_: Exception) { mutableListOf() }
            if (query !in current) {
                current.add(0, query)
                prefs[PINNED_SEARCHES] = JSONArray(current.take(12)).toString()
            }
        }
    }

    suspend fun removePinnedSearch(query: String) {
        context.dataStore.edit { prefs ->
            val current = try {
                val arr = JSONArray(prefs[PINNED_SEARCHES] ?: "[]")
                (0 until arr.length()).map { arr.optString(it) }.toMutableList()
            } catch (_: Exception) { mutableListOf() }
            current.remove(query)
            prefs[PINNED_SEARCHES] = JSONArray(current).toString()
        }
    }
}
