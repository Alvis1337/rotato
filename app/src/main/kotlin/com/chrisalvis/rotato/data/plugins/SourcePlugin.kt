package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource

/**
 * A SourcePlugin encapsulates everything about one wallpaper source: its display metadata,
 * credential requirements, and the actual fetch logic. New sources are registered in
 * [SourcePluginRegistry] without touching any core code.
 */
abstract class SourcePlugin {

    /** Stable identifier — matches SourceType.name for built-in sources (e.g. "DANBOORU"). */
    abstract val id: String

    abstract val displayName: String
    abstract val description: String

    /** Whether this plugin requires a Play Store IAP to activate. */
    abstract val isPremium: Boolean

    abstract val needsApiKey: Boolean
    abstract val needsApiUser: Boolean
    abstract val apiKeyLabel: String
    abstract val apiUserLabel: String

    /** If false, may return adult content. Used as a default-enable heuristic. */
    abstract val safeContent: Boolean

    /** Whether this plugin strictly requires credentials to function (returns errors without them). */
    open val requiresCredentials: Boolean = false

    /**
     * Fetch one wallpaper from this source.
     *
     * @param source  User-configured credentials / tags for this source.
     * @param query   Search term (normalised anime title, user search, or "").
     * @param exclude IDs of wallpapers already seen this session (client-side dedup).
     * @param nsfw    Whether the user has enabled NSFW mode.
     * @param filters Resolution / aspect-ratio filters.
     */
    abstract suspend fun fetch(
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper?
}
