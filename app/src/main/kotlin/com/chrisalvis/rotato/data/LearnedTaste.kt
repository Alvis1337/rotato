package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What Discover has learned from the user without being told: each save, download or wallpaper
 * set nudges that image's tags up, each skip or block nudges them down. Older signals fade so
 * the feed follows changing taste. Drives the "For you" ordering.
 */
class LearnedTaste(private val context: Context) {

    enum class Signal(val weight: Float) {
        SET_WALLPAPER(3f), SAVED(2f), DOWNLOADED(1.5f), SKIPPED(-1f), BLOCKED(-3f)
    }

    val weights: Flow<Map<String, Float>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { parse(it[WEIGHTS_KEY]) }

    val forYouEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[FOR_YOU_KEY] ?: true }

    suspend fun setForYouEnabled(enabled: Boolean) {
        context.dataStore.edit { it[FOR_YOU_KEY] = enabled }
    }

    suspend fun record(tags: List<String>, signal: Signal) {
        val normalized = tags.map(::normalizeTag).filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return
        // Spread the signal over the tags so a 40-tag post doesn't outweigh a 5-tag one.
        val perTag = signal.weight / sqrt(normalized.size.toFloat())
        context.dataStore.edit { prefs ->
            val current = parse(prefs[WEIGHTS_KEY]).mapValues { it.value * DECAY }.toMutableMap()
            normalized.forEach { tag -> current[tag] = ((current[tag] ?: 0f) + perTag).coerceIn(-MAX_WEIGHT, MAX_WEIGHT) }
            val kept = current.filterValues { abs(it) >= 0.05f }
                .entries.sortedByDescending { abs(it.value) }
                .take(MAX_TAGS)
            prefs[WEIGHTS_KEY] = JSONObject().apply { kept.forEach { (k, v) -> put(k, v.toDouble()) } }.toString()
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.remove(WEIGHTS_KEY) }
    }

    private fun parse(json: String?): Map<String, Float> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap { obj.keys().forEach { k -> put(k, obj.optDouble(k, 0.0).toFloat()) } }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private val WEIGHTS_KEY = stringPreferencesKey("learned_tag_weights")
        private val FOR_YOU_KEY = booleanPreferencesKey("discover_for_you")
        private const val DECAY = 0.985f
        private const val MAX_WEIGHT = 20f
        private const val MAX_TAGS = 600

        /**
         * Ranking score for [wp]: learned tag affinity, plus a bonus for images that will look
         * good as a wallpaper here (high resolution, and fill both Fold screens when relevant).
         */
        fun score(wp: BrainrotWallpaper, weights: Map<String, Float>, foldCanvas: WallpaperCanvas?): Float {
            val tags = wp.tags.map(::normalizeTag)
            val affinity = if (tags.isEmpty() || weights.isEmpty()) 0f
                else tags.sumOf { (weights[it] ?: 0f).toDouble() }.toFloat() / sqrt(tags.size.toFloat())
            val (w, h) = wp.resolution.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }
                .let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
            val longSide = maxOf(w, h)
            val resBonus = when {
                longSide >= 3840 -> 1.2f
                longSide >= 2560 -> 0.7f
                longSide >= 1920 -> 0.3f
                longSide in 1..999 -> -0.8f
                else -> 0f
            }
            val foldBonus = if (foldCanvas != null && w > 0 && isFoldFriendly(w, h, foldCanvas)) 1.5f else 0f
            return affinity + resBonus + foldBonus
        }
    }
}
