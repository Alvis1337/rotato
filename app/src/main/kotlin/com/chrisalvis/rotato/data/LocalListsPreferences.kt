package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalListsPreferences(private val context: Context) {

    companion object {
        private val LISTS_KEY = stringPreferencesKey("local_lists_json")
        private val WALLPAPERS_KEY = stringPreferencesKey("local_list_wallpapers_json")
    }

    val lists: Flow<List<LocalList>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { parseLists(it[LISTS_KEY] ?: "[]") }

    val allWallpapers: Flow<List<LocalWallpaperEntry>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { parseWallpapers(it[WALLPAPERS_KEY] ?: "[]") }

    fun wallpapersForList(listId: String): Flow<List<LocalWallpaperEntry>> =
        allWallpapers.map { all -> all.filter { it.listId == listId } }

    suspend fun createList(name: String): LocalList {
        val list = LocalList(name = name.trim())
        context.dataStore.edit { prefs ->
            val current = parseLists(prefs[LISTS_KEY] ?: "[]").toMutableList()
            current.add(list)
            prefs[LISTS_KEY] = serializeLists(current)
        }
        return list
    }

    suspend fun createListWithId(list: LocalList) {
        context.dataStore.edit { prefs ->
            val current = parseLists(prefs[LISTS_KEY] ?: "[]").toMutableList()
            if (current.none { it.id == list.id }) {
                current.add(list)
                prefs[LISTS_KEY] = serializeLists(current)
            }
        }
    }

    suspend fun deleteList(id: String) {
        context.dataStore.edit { prefs ->
            prefs[LISTS_KEY] = serializeLists(parseLists(prefs[LISTS_KEY] ?: "[]").filter { it.id != id })
            prefs[WALLPAPERS_KEY] = serializeWallpapers(parseWallpapers(prefs[WALLPAPERS_KEY] ?: "[]").filter { it.listId != id })
        }
    }

    suspend fun renameList(id: String, name: String) {
        context.dataStore.edit { prefs ->
            val updated = parseLists(prefs[LISTS_KEY] ?: "[]").map {
                if (it.id == id) it.copy(name = name.trim()) else it
            }
            prefs[LISTS_KEY] = serializeLists(updated)
        }
    }

    suspend fun setUseAsRotation(listId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val updated = parseLists(prefs[LISTS_KEY] ?: "[]").map {
                if (it.id == listId) it.copy(useAsRotation = enabled) else it
            }
            prefs[LISTS_KEY] = serializeLists(updated)
        }
    }

    suspend fun addWallpaper(listId: String, wallpaper: BrainrotWallpaper): Boolean {
        var added = false
        context.dataStore.edit { prefs ->
            val current = parseWallpapers(prefs[WALLPAPERS_KEY] ?: "[]")
            if (current.any { it.listId == listId && it.sourceId == wallpaper.id }) return@edit
            val entry = LocalWallpaperEntry(
                listId = listId,
                sourceId = wallpaper.id,
                source = wallpaper.source,
                thumbUrl = wallpaper.thumbUrl,
                fullUrl = wallpaper.fullUrl,
                resolution = wallpaper.resolution,
                pageUrl = wallpaper.pageUrl,
                tags = wallpaper.tags
            )
            prefs[WALLPAPERS_KEY] = serializeWallpapers(current + entry)
            added = true
        }
        return added
    }

    suspend fun addLocalImage(listId: String, relativePath: String) {        val uuid = relativePath.substringAfterLast("/").substringBeforeLast(".")
        context.dataStore.edit { prefs ->
            val current = parseWallpapers(prefs[WALLPAPERS_KEY] ?: "[]")
            if (current.any { it.listId == listId && it.sourceId == uuid }) return@edit
            val entry = LocalWallpaperEntry(
                listId = listId,
                sourceId = uuid,
                source = "device",
                thumbUrl = relativePath,
                fullUrl = relativePath,
                resolution = "",
                pageUrl = "",
                tags = emptyList()
            )
            prefs[WALLPAPERS_KEY] = serializeWallpapers(current + entry)
        }
    }

    suspend fun addWallpaperEntry(entry: LocalWallpaperEntry) {
        context.dataStore.edit { prefs ->
            val current = parseWallpapers(prefs[WALLPAPERS_KEY] ?: "[]")
            if (current.any { it.id == entry.id }) return@edit
            prefs[WALLPAPERS_KEY] = serializeWallpapers(current + entry)
        }
    }

    suspend fun removeWallpaper(entryId: String) {
        context.dataStore.edit { prefs ->
            val updated = parseWallpapers(prefs[WALLPAPERS_KEY] ?: "[]").filter { it.id != entryId }
            prefs[WALLPAPERS_KEY] = serializeWallpapers(updated)
        }
    }

    // --- serialization ---

    private fun parseLists(json: String): List<LocalList> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LocalList(
                id = o.getString("id"),
                name = o.getString("name"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                useAsRotation = o.optBoolean("useAsRotation", false)
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun serializeLists(lists: List<LocalList>): String =
        JSONArray().also { arr ->
            lists.forEach { l ->
                arr.put(JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("createdAt", l.createdAt)
                    put("useAsRotation", l.useAsRotation)
                })
            }
        }.toString()

    private fun parseWallpapers(json: String): List<LocalWallpaperEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tagsArr = o.optJSONArray("tags")
            LocalWallpaperEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                listId = o.getString("listId"),
                sourceId = o.getString("sourceId"),
                source = o.optString("source", ""),
                thumbUrl = o.optString("thumbUrl", ""),
                fullUrl = o.optString("fullUrl", ""),
                resolution = o.optString("resolution", ""),
                pageUrl = o.optString("pageUrl", ""),
                tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList(),
                addedAt = o.optLong("addedAt", System.currentTimeMillis())
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun serializeWallpapers(entries: List<LocalWallpaperEntry>): String =
        JSONArray().also { arr ->
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("listId", e.listId)
                    put("sourceId", e.sourceId)
                    put("source", e.source)
                    put("thumbUrl", e.thumbUrl)
                    put("fullUrl", e.fullUrl)
                    put("resolution", e.resolution)
                    put("pageUrl", e.pageUrl)
                    put("tags", JSONArray(e.tags))
                    put("addedAt", e.addedAt)
                })
            }
        }.toString()
}
