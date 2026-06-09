package com.chrisalvis.rotato.data

import android.content.Context
import android.util.Log
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.normalizeBooruQuery
import kotlinx.coroutines.flow.first

class FillHelper(private val context: Context) {

    private val localLists = LocalListsPreferences(context)
    private val localSources = LocalSourcesPreferences(context)
    private val prefs = RotatoPreferences(context)
    private val pluginRepo = PluginRepository(context)

    suspend fun fillCollection(
        list: LocalList,
        tags: String,
        count: Int,
        pluginId: String? = null,
        instanceId: String? = null,
        matchAny: Boolean = false,
        nsfwOverride: Boolean? = null,
        minResolution: MinResolution = MinResolution.ANY,
        aspectRatio: AspectRatio = AspectRatio.ANY,
        useMalFilter: Boolean = false,
    ): Int {
        val manifests = pluginRepo.installedManifests.first()
        val allSources = localSources.sources.first()
        val globalNsfw = prefs.nsfwMode.first()
        val candidates = allSources.filter { src ->
            if (!src.enabled) return@filter false
            if (pluginId != null && src.pluginId != pluginId) return@filter false
            if (instanceId != null && src.instanceId != instanceId) return@filter false
            val manifest = manifests.find { it.id.equals(src.pluginId, ignoreCase = true) } ?: return@filter false
            val effectiveNsfw = nsfwOverride ?: (src.nsfwEnabled ?: globalNsfw)
            PluginExecutor.canServe(manifest, effectiveNsfw, src)
        }
        if (candidates.isEmpty()) return 0

        val filters = BrainrotFilters(
            minResolution = minResolution,
            aspectRatio = aspectRatio,
            useMalFilter = useMalFilter,
            matchAny = matchAny,
        )

        var added = 0
        val shuffledCandidates = candidates.shuffled()
        var round = 0
        val maxRounds = 15
        while (added < count && round < maxRounds) {
            var addedThisRound = 0
            for (src in shuffledCandidates) {
                if (added >= count) break
                val manifest = manifests.find { it.id.equals(src.pluginId, ignoreCase = true) } ?: continue
                val remaining = count - added
                val effectiveNsfw = nsfwOverride ?: (src.nsfwEnabled ?: globalNsfw)
                val wallpapers = try {
                    PluginExecutor.fetchPage(manifest, src, tags.trim(), emptyList(), effectiveNsfw, filters, remaining + 5)
                } catch (e: Exception) {
                    Log.e("FillHelper", "fetchPage error for ${src.pluginId}", e)
                    emptyList()
                }
                for (wp in wallpapers) {
                    if (added >= count) break
                    val ok = localLists.addWallpaper(list.id, wp)
                    if (ok) { added++; addedThisRound++ }
                }
            }
            if (addedThisRound == 0) break
            round++
        }
        return added
    }

    /** Builds the tag query for a MAL-managed collection. */
    fun buildMalQuery(config: MalCollectionConfig): String =
        (listOf(config.resolvedAnimeQuery.ifBlank { config.animeTitle }) + config.characterTags)
            .map { normalizeBooruQuery(it) }
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** Runs auto-refill on all MAL-managed rotation collections that are below [minCount]. */
    suspend fun autoRefillLowCollections(minCount: Int) {
        val lists = localLists.lists.first()
        val allWallpapers = localLists.allWallpapers.first()
        val countByList = allWallpapers.groupBy { it.listId }.mapValues { it.value.size }

        lists.filter { it.useAsRotation && it.isMalManaged }.forEach { list ->
            val config = list.malConfig ?: return@forEach
            val current = countByList[list.id] ?: 0
            if (current >= minCount) return@forEach
            val needed = (config.fillCount - current).coerceAtLeast(1)
            try {
                fillCollection(
                    list = list,
                    tags = buildMalQuery(config),
                    count = needed,
                    pluginId = config.sourcePluginId,
                    instanceId = config.sourceInstanceId.takeIf { it.isNotBlank() },
                    matchAny = config.matchAny,
                    nsfwOverride = config.nsfwOverride,
                    minResolution = config.minResolution,
                    aspectRatio = config.aspectRatio,
                    useMalFilter = config.useMalFilter,
                )
            } catch (e: Exception) {
                Log.e("FillHelper", "autoRefill failed for ${list.name}", e)
            }
        }
    }
}
