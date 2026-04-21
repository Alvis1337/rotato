package com.chrisalvis.rotato.data

import android.util.Log
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.SourcePluginRegistry

private const val TAG = "DirectSource"

/**
 * Entry point for fetching one wallpaper from a [LocalSource].
 * Delegates to the matching [SourcePlugin] in [SourcePluginRegistry].
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
        return null
    }
    if (!PluginEntitlement.isUnlocked(plugin)) {
        Log.d(TAG, "Plugin ${plugin.displayName} is locked — skipping")
        return null
    }
    return try {
        plugin.fetch(source, query, exclude, nsfwMode, filters)
    } catch (e: Exception) {
        Log.e(TAG, "Plugin ${plugin.displayName} threw exception", e)
        null
    }
}
