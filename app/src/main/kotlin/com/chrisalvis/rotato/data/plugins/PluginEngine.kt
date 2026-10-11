package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource

/**
 * Executes fetches for a specific API protocol family.
 * An engine is stateless — all configuration comes from [manifest] + [source].
 */
abstract class PluginEngine {
    abstract val protocol: Protocol

    abstract suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper?

    open suspend fun fetchPage(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int = 100,
    ): List<BrainrotWallpaper> = listOfNotNull(fetch(manifest, source, query, exclude, nsfw, filters))

    /**
     * Tags on this site that start with (or, where the site supports it, are an alias of)
     * [token], most-used first, as name to post count. Used when a search comes back empty
     * because people search "jessie" but the site files her under "jessie_(pokemon)".
     * Blocking; sites without a tag list return nothing.
     */
    open fun lookupTags(manifest: PluginManifest, source: LocalSource, token: String): List<Pair<String, Int>> = emptyList()

    /** Resolved base URL: user override wins over manifest default. */
    protected fun baseUrl(manifest: PluginManifest, source: LocalSource): String =
        source.baseUrl.ifBlank { manifest.defaultBaseUrl }.trimEnd('/')

    open fun canServe(manifest: PluginManifest, nsfw: Boolean, source: LocalSource): Boolean {
        if (manifest.requiresCredentials) {
            if (manifest.needsApiKey && source.apiKey.isBlank()) return false
            if (manifest.needsApiUser && source.apiUser.isBlank()) return false
        }
        if (!nsfw && manifest.adultOnly) return false
        return !(nsfw && manifest.safeContent)
    }
}
