package com.chrisalvis.rotato.data.plugins

import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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
        Protocol.BING to BingEngine,
        Protocol.WIKIMEDIA to WikimediaEngine,
        Protocol.UNSPLASH to UnsplashEngine,
    )

    fun engineFor(manifest: PluginManifest): PluginEngine? = engines[manifest.protocol]

    suspend fun fetch(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
    ): BrainrotWallpaper? {
        val engine = engineFor(manifest) ?: return null
        val q = manifest.translateQuery(query)
        // Every searched tag was one this site doesn't have: nothing to show, not everything.
        if (q.isBlank() && query.isNotBlank()) return null
        engine.fetch(manifest, source, q, exclude, nsfw, filters)?.let { return it }
        val resolved = resolveQuery(engine, manifest, source, q) ?: return null
        return engine.fetch(manifest, source, resolved, exclude, nsfw, filters)
    }

    suspend fun fetchPage(
        manifest: PluginManifest,
        source: LocalSource,
        query: String,
        exclude: List<String>,
        nsfw: Boolean,
        filters: BrainrotFilters,
        limit: Int = 100,
    ): List<BrainrotWallpaper> {
        val engine = engineFor(manifest) ?: return emptyList()
        val q = manifest.translateQuery(query)
        if (q.isBlank() && query.isNotBlank()) return emptyList()
        val page = engine.fetchPage(manifest, source, q, exclude, nsfw, filters, limit)
        if (page.isNotEmpty()) return page
        val resolved = resolveQuery(engine, manifest, source, q) ?: return page
        return engine.fetchPage(manifest, source, resolved, exclude, nsfw, filters, limit)
    }

    /** Site tag chosen for a searched word, per site; "" when the site had nothing better. */
    private val resolvedTags = ConcurrentHashMap<String, String>()

    /**
     * [query] with each plain word swapped for the tag the site actually uses ("jessie" →
     * "jessie_(pokemon)"), or null when nothing changes. Only runs after a search came back
     * empty, and each word is looked up once per site.
     */
    private suspend fun resolveQuery(engine: PluginEngine, manifest: PluginManifest, source: LocalSource, query: String): String? {
        if (query.isBlank()) return null
        val tokens = normalizeUserQuery(query).split(' ').filter { it.isNotBlank() }
        var changed = false
        val out = withContext(Dispatchers.IO) {
            tokens.map { token ->
                // Excludes, ORs, wildcards, metatags and already-qualified tags are left as typed.
                if (token.length < 2 || token[0] == '-' || token[0] == '~' || token.any { it == '*' || it == ':' || it == '(' }) {
                    return@map token
                }
                val key = "${manifest.id}|${source.baseUrl}|$token"
                val tag = resolvedTags[key] ?: (pickTag(token, runCatching { engine.lookupTags(manifest, source, token) }.getOrDefault(emptyList())) ?: "")
                    .also { resolvedTags[key] = it }
                if (tag.isNotBlank() && tag != token) { changed = true; tag } else token
            }
        }
        return if (changed) out.joinToString(" ") else null
    }

    /**
     * The word itself or a qualified version of it ("jessie_(pokemon)") when the site has one,
     * the busiest; otherwise the site's busiest match (an alias target on Danbooru).
     */
    private fun pickTag(token: String, candidates: List<Pair<String, Int>>): String? {
        val used = candidates.filter { it.second > 0 }
        return (used.filter { (n, _) -> n == token || n.startsWith("${token}_(") }.maxByOrNull { it.second }
            ?: used.maxByOrNull { it.second })?.first
    }

    fun canServe(manifest: PluginManifest, nsfw: Boolean, source: LocalSource): Boolean =
        engineFor(manifest)?.canServe(manifest, nsfw, source) ?: false
}
