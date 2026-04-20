package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class MalPreferences(private val context: Context) {

    companion object {
        val MAL_ACCESS_TOKEN = stringPreferencesKey("mal_access_token")
        val MAL_REFRESH_TOKEN = stringPreferencesKey("mal_refresh_token")
        val MAL_USERNAME = stringPreferencesKey("mal_username")
        val MAL_ANIME_LIST_JSON = stringPreferencesKey("mal_anime_list_json")
        val MAL_CODE_VERIFIER = stringPreferencesKey("mal_code_verifier")
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

    private val animeListJson: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[MAL_ANIME_LIST_JSON] ?: "[]" }

    val animeList: Flow<List<String>> = animeListJson.map { json ->
        runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
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

    suspend fun setAnimeList(titles: List<String>) {
        context.dataStore.edit { it[MAL_ANIME_LIST_JSON] = JSONArray(titles).toString() }
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
            prefs.remove(MAL_ANIME_LIST_JSON)
            prefs.remove(MAL_CODE_VERIFIER)
        }
    }
}
