package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource

/**
 * Routes fetch calls to the correct [PluginEngine] based on [PluginManifest.protocol].
 */
object PluginExecutor {

    private val engines: Map<Protocol, PluginEngine> = mapOf(
        Protocol.GELBOORU to GelbooruEngine,
        Protocol.DANBOORU to DanbooruEngine,
        Protocol.MOEBOORU to MoebooruEngine,
        Protocol.WALLHAVEN to WallhavenEngine,
        Protocol.REDDIT to RedditEngine,
        Protocol.ZEROCHAN to ZerochanEngine,
    )

    fun engineFor(manifest: PluginManifest): PluginEngine? = engines[manifest.protocol]

    suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? = engineFor(manifest)?.fetch(manifest, source, query, exclude, nsfw, filters)

    suspend fun fetchPage(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int = 100,
    ): List<BrainrotWallpaper> = engineFor(manifest)?.fetchPage(manifest, source, query, exclude, nsfw, filters, limit) ?: emptyList()

    fun canServe(manifest: PluginManifest, nsfw: Boolean, source: LocalSource): Boolean =
        engineFor(manifest)?.canServe(manifest, nsfw, source) ?: false
}
