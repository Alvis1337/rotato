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
    val nsfwMode = preferences.nsfwModeSetting.first()
    val nsfwHidden = preferences.nsfwHidden.first()
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
                put("rotationTarget", l.rotationTarget.name)
                put("coverUrl", l.coverUrl)
                put("blurExempt", l.blurExempt)
                l.rotationIntervalMinutes?.let { put("rotationIntervalMinutes", it) }
                l.smartRule?.takeIf { !it.isEmpty }?.let { rule ->
                    put("smartRule", JSONObject().apply {
                        put("requireAll", JSONArray(rule.requireAll))
                        put("requireAny", JSONArray(rule.requireAny))
                        put("excludeAny", JSONArray(rule.excludeAny))
                    })
                }
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
                put("isVideo", e.isVideo)
                put("isNsfw", e.isNsfw)
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
            put("nsfwHidden", nsfwHidden)
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

/**
 * [this] with the collection settings a backup entry [o] carries beyond name, lock and MAL config.
 * Older backups don't have them, so anything missing keeps its current value.
 */
fun LocalList.withBackupSettings(o: JSONObject): LocalList {
    fun strings(rule: JSONObject, key: String): List<String> =
        rule.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
    val rule = o.optJSONObject("smartRule")?.let { r ->
        SmartRule(requireAll = strings(r, "requireAll"), requireAny = strings(r, "requireAny"), excludeAny = strings(r, "excludeAny"))
    }?.takeIf { !it.isEmpty }
    return copy(
        rotationTarget = runCatching { ScreenRotationTarget.valueOf(o.optString("rotationTarget", rotationTarget.name)) }
            .getOrDefault(rotationTarget),
        coverUrl = o.optString("coverUrl", coverUrl),
        blurExempt = o.optBoolean("blurExempt", blurExempt),
        smartRule = rule ?: smartRule,
        rotationIntervalMinutes = if (o.has("rotationIntervalMinutes")) o.optInt("rotationIntervalMinutes") else rotationIntervalMinutes,
    )
}
