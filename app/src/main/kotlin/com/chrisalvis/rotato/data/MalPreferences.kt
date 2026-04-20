package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class MalAnimeEntry(val title: String, val score: Int)

class MalPreferences(private val context: Context) {

    companion object {
        val MAL_ACCESS_TOKEN = stringPreferencesKey("mal_access_token")
        val MAL_REFRESH_TOKEN = stringPreferencesKey("mal_refresh_token")
        val MAL_USERNAME = stringPreferencesKey("mal_username")
        /** JSON array of {title, score} objects */
        val MAL_ANIME_ENTRIES_JSON = stringPreferencesKey("mal_anime_entries_json")
        val MAL_CODE_VERIFIER = stringPreferencesKey("mal_code_verifier")
        /** JSON array of status strings, e.g. ["completed","watching"] */
        val MAL_FILTER_STATUSES = stringPreferencesKey("mal_filter_statuses")
        val MAL_FILTER_MIN_SCORE = intPreferencesKey("mal_filter_min_score")

        val DEFAULT_STATUSES = setOf("completed", "watching")
    }

    val accessToken: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_ACCESS_TOKEN] ?: "" }

    val refreshToken: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_REFRESH_TOKEN] ?: "" }

    val username: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_USERNAME] ?: "" }

    val filterStatuses: Flow<Set<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val json = prefs[MAL_FILTER_STATUSES] ?: return@map DEFAULT_STATUSES
            runCatching {
                val arr = JSONArray(json)
                Set(arr.length()) { arr.getString(it) }
            }.getOrDefault(DEFAULT_STATUSES)
        }

    val filterMinScore: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_FILTER_MIN_SCORE] ?: 0 }

    private val animeEntriesJson: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_ANIME_ENTRIES_JSON] ?: "[]" }

    val animeEntries: Flow<List<MalAnimeEntry>> = animeEntriesJson.map { json ->
        runCatching {
            val arr = JSONArray(json)
            List(arr.length()) {
                val obj = arr.getJSONObject(it)
                MalAnimeEntry(obj.getString("title"), obj.getInt("score"))
            }
        }.getOrDefault(emptyList())
    }

    /** Titles pre-filtered by the current min-score preference — used by BrainrotViewModel. */
    val animeList: Flow<List<String>> = combine(animeEntries, filterMinScore) { entries, minScore ->
        entries.filter { it.score >= minScore || minScore == 0 }.map { it.title }
    }

    val isLoggedIn: Flow<Boolean> = accessToken.map { it.isNotBlank() }

    val codeVerifier: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_CODE_VERIFIER] ?: "" }

    suspend fun setTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[MAL_ACCESS_TOKEN] = accessToken
            prefs[MAL_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun setUsername(username: String) {
        context.dataStore.edit { it[MAL_USERNAME] = username }
    }

    suspend fun setAnimeEntries(entries: List<MalAnimeEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(JSONObject().put("title", entry.title).put("score", entry.score))
        }
        context.dataStore.edit { it[MAL_ANIME_ENTRIES_JSON] = arr.toString() }
    }

    suspend fun setFilterStatuses(statuses: Set<String>) {
        context.dataStore.edit { it[MAL_FILTER_STATUSES] = JSONArray(statuses.toList()).toString() }
    }

    suspend fun setFilterMinScore(score: Int) {
        context.dataStore.edit { it[MAL_FILTER_MIN_SCORE] = score }
    }

    suspend fun setCodeVerifier(verifier: String) {
        context.dataStore.edit { it[MAL_CODE_VERIFIER] = verifier }
    }

    suspend fun clearCodeVerifier() {
        context.dataStore.edit { it.remove(MAL_CODE_VERIFIER) }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(MAL_ACCESS_TOKEN)
            prefs.remove(MAL_REFRESH_TOKEN)
            prefs.remove(MAL_USERNAME)
            prefs.remove(MAL_ANIME_ENTRIES_JSON)
            prefs.remove(MAL_CODE_VERIFIER)
        }
    }
}
