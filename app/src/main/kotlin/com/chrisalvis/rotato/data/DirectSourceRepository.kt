package com.chrisalvis.rotato.data

import android.util.Log
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.SourcePluginRegistry

private const val TAG = "DirectSource"

/**
 * Entry point for fetching one wallpaper from a [LocalSource].
 * Delegates to the matching [SourcePlugin] in [SourcePluginRegistry] and
 * records fetch health in [SourceHealthTracker].
 */
suspend fun fetchFromSource(
    source: LocalSource,
    query: String,
    exclude: List<String> = emptyList(),
    nsfwMode: Boolean = false,
    filters: BrainrotFilters = BrainrotFilters(),
): BrainrotWallpaper? {
    val plugin = SourcePluginRegistry.forType(source.type)
    if (plugin == null) {
        Log.w(TAG, "No plugin registered for source type: ${source.type}")
        SourceHealthTracker.recordError(source.type, "No plugin registered")
        return null
    }
    if (!PluginEntitlement.isUnlocked(plugin)) {
        Log.d(TAG, "Plugin ${plugin.displayName} is locked — skipping")
        return null
    }
    return try {
        val result = plugin.fetch(source, query, exclude, nsfwMode, filters)
        if (result != null) {
            SourceHealthTracker.recordSuccess(source.type)
        } else {
            // null = no matching results — not a hard error, don't penalize source health
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Plugin ${plugin.displayName} threw exception", e)
        SourceHealthTracker.recordError(source.type, e.message ?: "Unknown error")
        null
    }
}
