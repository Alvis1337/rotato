package com.chrisalvis.rotato.data

import android.content.Context
import android.util.Log
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginRepository

private const val TAG = "DirectSource"

/**
 * Entry point for fetching one wallpaper from a [LocalSource].
 * Delegates to [PluginExecutor] via [PluginRepository] and records fetch health in [SourceHealthTracker].
 */
suspend fun fetchFromSource(
    context: Context,
    source: LocalSource,
    query: String,
    exclude: List<String> = emptyList(),
    nsfwMode: Boolean = false,
    filters: BrainrotFilters = BrainrotFilters(),
): BrainrotWallpaper? {
    val manifest = PluginRepository(context).getManifest(source.pluginId)
    if (manifest == null) {
        Log.w(TAG, "No manifest for pluginId: ${source.pluginId}")
        SourceHealthTracker.recordError(source.pluginId, "No manifest registered")
        return null
    }
    if (!PluginEntitlement.isUnlocked(manifest)) {
        Log.d(TAG, "Plugin ${manifest.name} is locked — skipping")
        return null
    }
    return try {
        val result = PluginExecutor.fetch(manifest, source, query, exclude, nsfwMode, filters)
        if (result != null) SourceHealthTracker.recordSuccess(source.pluginId)
        result
    } catch (e: Exception) {
        Log.e(TAG, "Plugin ${manifest.name} threw exception", e)
        SourceHealthTracker.recordError(source.pluginId, e.message ?: "Unknown error")
        null
    }
}

/**
 * Bulk version — returns a full page of wallpapers.
 */
suspend fun fetchPageFromSource(
    context: Context,
    source: LocalSource,
    query: String,
    exclude: List<String> = emptyList(),
    nsfwMode: Boolean = false,
    filters: BrainrotFilters = BrainrotFilters(),
    limit: Int = 100,
): List<BrainrotWallpaper> {
    val manifest = PluginRepository(context).getManifest(source.pluginId)
    if (manifest == null) {
        Log.w(TAG, "No manifest for pluginId: ${source.pluginId}")
        SourceHealthTracker.recordError(source.pluginId, "No manifest registered")
        return emptyList()
    }
    if (!PluginEntitlement.isUnlocked(manifest)) {
        Log.d(TAG, "Plugin ${manifest.name} is locked — skipping")
        return emptyList()
    }
    return try {
        val results = PluginExecutor.fetchPage(manifest, source, query, exclude, nsfwMode, filters, limit)
        if (results.isNotEmpty()) SourceHealthTracker.recordSuccess(source.pluginId)
        results
    } catch (e: Exception) {
        Log.e(TAG, "Plugin ${manifest.name} threw exception", e)
        SourceHealthTracker.recordError(source.pluginId, e.message ?: "Unknown error")
        emptyList()
    }
}
