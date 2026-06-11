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
import java.util.UUID

enum class TagTier { LOVE, LIKE, NEUTRAL, DISLIKE, NEVER }

data class InterestProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val isActive: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("includeTags", JSONArray().also { arr -> includeTags.forEach(arr::put) })
        put("excludeTags", JSONArray().also { arr -> excludeTags.forEach(arr::put) })
        put("isActive", isActive)
    }

    companion object {
        fun fromJson(o: JSONObject) = InterestProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            includeTags = o.optJSONArray("includeTags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            excludeTags = o.optJSONArray("excludeTags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            isActive = o.optBoolean("isActive", false),
        )
    }
}

class TastePreferences(private val context: Context) {

    companion object {
        // Historical key kept as the SFW tier for backward compatibility.
        private val SFW_TAG_TIERS_KEY = stringPreferencesKey("tag_tiers_json")
        private val NSFW_TAG_TIERS_KEY = stringPreferencesKey("nsfw_tag_tiers_json")
        private val INTEREST_PROFILES_KEY = stringPreferencesKey("interest_profiles_json")
    }

    val sfwTagTiers: Flow<Map<String, TagTier>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseTierMap(prefs[SFW_TAG_TIERS_KEY]) }

    val nsfwTagTiers: Flow<Map<String, TagTier>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parseTierMap(prefs[NSFW_TAG_TIERS_KEY]) }

    private fun parseTierMap(json: String?): Map<String, TagTier> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap { obj.keys().forEach { k -> put(k, TagTier.valueOf(obj.getString(k))) } }
        }.getOrDefault(emptyMap())
    }

    val interestProfiles: Flow<List<InterestProfile>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val json = prefs[INTEREST_PROFILES_KEY] ?: return@map emptyList()
            runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { InterestProfile.fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

    suspend fun setTagTier(tag: String, tier: TagTier, isNsfw: Boolean = false) {
        val key = if (isNsfw) NSFW_TAG_TIERS_KEY else SFW_TAG_TIERS_KEY
        context.dataStore.edit { prefs ->
            val obj = runCatching { JSONObject(prefs[key] ?: "{}") }.getOrDefault(JSONObject())
            if (tier == TagTier.NEUTRAL) obj.remove(tag) else obj.put(tag, tier.name)
            prefs[key] = obj.toString()
        }
    }

    suspend fun removeTagTier(tag: String, isNsfw: Boolean = false) {
        val key = if (isNsfw) NSFW_TAG_TIERS_KEY else SFW_TAG_TIERS_KEY
        context.dataStore.edit { prefs ->
            val obj = runCatching { JSONObject(prefs[key] ?: "{}") }.getOrDefault(JSONObject())
            obj.remove(tag)
            prefs[key] = obj.toString()
        }
    }

    suspend fun saveProfile(profile: InterestProfile) {
        context.dataStore.edit { prefs ->
            val arr = runCatching { JSONArray(prefs[INTEREST_PROFILES_KEY] ?: "[]") }.getOrDefault(JSONArray())
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filterNot { it.optString("id") == profile.id }
                .toMutableList()
            list.add(profile.toJson())
            prefs[INTEREST_PROFILES_KEY] = JSONArray(list).toString()
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            val arr = runCatching { JSONArray(prefs[INTEREST_PROFILES_KEY] ?: "[]") }.getOrDefault(JSONArray())
            val filtered = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("id") != profileId }
            prefs[INTEREST_PROFILES_KEY] = JSONArray(filtered).toString()
        }
    }

    suspend fun toggleProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            val arr = runCatching { JSONArray(prefs[INTEREST_PROFILES_KEY] ?: "[]") }.getOrDefault(JSONArray())
            val updated = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                if (o.optString("id") == profileId) {
                    o.put("isActive", !o.optBoolean("isActive", false))
                } else o
            }
            prefs[INTEREST_PROFILES_KEY] = JSONArray(updated).toString()
        }
    }
}
