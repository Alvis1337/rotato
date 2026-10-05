package com.chrisalvis.rotato.data

import android.content.Context
import com.chrisalvis.rotato.data.plugins.PluginRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * The full settings backup (sources and keys, preferences, collections, installed plugins) as
 * pretty-printed JSON. Shared by the manual Export button and the automatic backup worker.
 */
suspend fun buildBackupJson(context: Context): String {
    val sourcesPrefs = LocalSourcesPreferences(context)
    val preferences = RotatoPreferences(context)
    val malPrefs = MalPreferences(context)
    val localLists = LocalListsPreferences(context)
    val pluginRepository = PluginRepository(context)
    val sources = sourcesPrefs.sources.first()
    val prefs = preferences.settings.first()
    val nsfwMode = preferences.nsfwMode.first()
    val minRes = preferences.brainrotFilters.first().minResolution
    val aspectRatio = preferences.brainrotFilters.first().aspectRatio
    val chargingTriggerEnabled = preferences.chargingTriggerEnabled.first()
    val autoFavoriteEnabled = preferences.autoFavoriteEnabled.first()
    val autoFavoriteMinutes = preferences.autoFavoriteMinutes.first()
    val widgetCollectionId = preferences.widgetCollectionId.first()
    val malUsername = malPrefs.username.first()
    val malStatuses = malPrefs.filterStatuses.first()
    val malMinScore = malPrefs.filterMinScore.first()
    val collectionLists = localLists.lists.first()
    val collectionWallpapers = localLists.allWallpapers.first()
    val installedPlugins = pluginRepository.installedManifests.first()

    val sourcesArr = JSONArray().also { arr ->
        sources.forEach { s ->
            arr.put(JSONObject().apply {
                put("pluginId", s.pluginId)
                put("instanceId", s.instanceId)
                put("baseUrl", s.baseUrl)
                put("enabled", s.enabled)
                put("apiKey", s.apiKey)
                put("apiUser", s.apiUser)
                put("tags", s.tags)
                put("wallhavenPurity", s.wallhavenPurity)
                if (s.extraConfig.isNotEmpty()) {
                    put("extraConfig", JSONObject().apply {
                        s.extraConfig.forEach { (k, v) -> put(k, v) }
                    })
                }
            })
        }
    }
    val listsArr = JSONArray().also { arr ->
        collectionLists.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("name", l.name)
                put("createdAt", l.createdAt)
                put("useAsRotation", l.useAsRotation)
                put("isLocked", l.isLocked)
                l.malConfig?.let { config ->
                    put("malConfig", JSONObject().apply {
                        put("animeTitle", config.animeTitle)
                        put("animeQuery", config.animeQuery)
                        put("characterTags", JSONArray(config.characterTags))
                        put("sourcePluginId", config.sourcePluginId ?: "")
                        put("sourceInstanceId", config.sourceInstanceId)
                        put("fillCount", config.fillCount)
                        put("matchAny", config.matchAny)
                        put("autoAddToLibrary", config.autoAddToLibrary)
                        config.nsfwOverride?.let { put("nsfwOverride", it) }
                        put("minResolution", config.minResolution.name)
                        put("aspectRatio", config.aspectRatio.name)
                        put("useMalFilter", config.useMalFilter)
                    })
                }
            })
        }
    }
    val wallpapersArr = JSONArray().also { arr ->
        collectionWallpapers.filter { it.source != "device" }.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("listId", e.listId)
                put("sourceId", e.sourceId)
                put("source", e.source)
                put("thumbUrl", e.thumbUrl)
                put("sampleUrl", e.sampleUrl)
                put("fullUrl", e.fullUrl)
                put("resolution", e.resolution)
                put("pageUrl", e.pageUrl)
                put("tags", JSONArray(e.tags))
                put("addedAt", e.addedAt)
            })
        }
    }
    return JSONObject().apply {
        put("version", 4)
        put("sources", sourcesArr)
        put("preferences", JSONObject().apply {
            put("intervalMinutes", prefs.intervalMinutes)
            put("shuffleMode", prefs.shuffleMode)
            put("wallpaperTarget", prefs.wallpaperTarget.name)
            put("nsfwMode", nsfwMode)
            put("minResolution", minRes.name)
            put("aspectRatio", aspectRatio.name)
            put("chargingTriggerEnabled", chargingTriggerEnabled)
            put("autoFavoriteEnabled", autoFavoriteEnabled)
            put("autoFavoriteMinutes", autoFavoriteMinutes)
            put("widgetCollectionId", widgetCollectionId)
        })
        put("mal", JSONObject().apply {
            put("username", malUsername)
            put("filterStatuses", JSONArray(malStatuses.toList()))
            put("filterMinScore", malMinScore)
        })
        put("collections", listsArr)
        put("collectionWallpapers", wallpapersArr)
        put("installedPlugins", JSONArray().also { arr ->
            installedPlugins.forEach { m -> arr.put(m.toJson()) }
        })
    }.toString(2)
}
