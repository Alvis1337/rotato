package com.chrisalvis.rotato.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Collections and their wallpapers, stored in [CollectionsDatabase]. The API is unchanged from
 * when they lived in DataStore; that data is moved into the database once, on first use.
 */
class LocalListsPreferences(private val context: Context) {

    companion object {
        private val LISTS_KEY = stringPreferencesKey("local_lists_json")
        private val WALLPAPERS_KEY = stringPreferencesKey("local_list_wallpapers_json")
        private val migration = Mutex()
        @Volatile private var migrated = false
    }

    private val db = CollectionsDatabase.get(context)
    private val dao = db.dao()

    /** Moves collections out of DataStore the first time anything reads or writes them. */
    private suspend fun ensureMigrated() {
        if (migrated) return
        migration.withLock {
            if (migrated) return
            val prefs = context.dataStore.data.first()
            val listsJson = prefs[LISTS_KEY]
            val entriesJson = prefs[WALLPAPERS_KEY]
            if (listsJson != null || entriesJson != null) {
                val lists = parseLists(listsJson ?: "[]")
                val entries = parseWallpapers(entriesJson ?: "[]")
                db.withTransaction {
                    if (dao.collectionCount() == 0 && dao.entryCount() == 0) {
                        dao.upsertCollections(lists.mapIndexed { i, l -> l.toRow(i) })
                        dao.insertEntries(entries.map { it.toRow() })
                    }
                }
                context.dataStore.edit { it.remove(LISTS_KEY); it.remove(WALLPAPERS_KEY) }
            }
            migrated = true
        }
    }

    private fun <T> migratedFlow(source: () -> Flow<T>): Flow<T> = flow {
        ensureMigrated()
        source().collect { emit(it) }
    }

    val lists: Flow<List<LocalList>> = migratedFlow {
        dao.collectionsFlow().map { rows -> rows.mapNotNull { it.toList() } }
    }

    val allWallpapers: Flow<List<LocalWallpaperEntry>> = migratedFlow {
        dao.entriesFlow().map { rows -> rows.mapNotNull { it.toEntry() } }
    }

    fun wallpapersForList(listId: String): Flow<List<LocalWallpaperEntry>> =
        allWallpapers.map { all -> all.filter { it.listId == listId } }

    suspend fun createList(
        name: String,
        useAsRotation: Boolean = false,
        malConfig: MalCollectionConfig? = null,
    ): LocalList? {
        ensureMigrated()
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val list = LocalList(name = trimmed, useAsRotation = useAsRotation, malConfig = malConfig)
        // Callers rely on null for "name already taken"; returning the unsaved list made
        // them write entries under an id that doesn't exist.
        return db.withTransaction {
            val taken = dao.collections().mapNotNull { it.toList() }.any { it.name.equals(trimmed, ignoreCase = true) }
            if (taken) null else list.also { dao.upsertCollections(listOf(it.toRow(dao.maxPosition() + 1))) }
        }
    }

    suspend fun createListWithId(list: LocalList) {
        ensureMigrated()
        db.withTransaction {
            if (dao.collection(list.id) == null) dao.upsertCollections(listOf(list.toRow(dao.maxPosition() + 1)))
        }
    }

    suspend fun deleteList(id: String) {
        ensureMigrated()
        db.withTransaction {
            dao.deleteCollection(id)
            dao.deleteEntriesIn(id)
        }
    }

    /** Moves a collection [delta] places earlier (negative) or later (positive) in the grid. */
    suspend fun moveList(id: String, delta: Int) {
        ensureMigrated()
        db.withTransaction {
            val rows = dao.collections().toMutableList()
            val from = rows.indexOfFirst { it.id == id }
            if (from == -1) return@withTransaction
            val to = (from + delta).coerceIn(0, rows.lastIndex)
            if (to == from) return@withTransaction
            rows.add(to, rows.removeAt(from))
            dao.upsertCollections(rows.mapIndexed { i, r -> r.copy(position = i) })
        }
    }

    /**
     * Moves entries to [targetListId] in one transaction. Entries already in the target (same
     * source id or URL) are dropped instead of duplicated. Returns how many were moved.
     */
    suspend fun moveEntries(entryIds: Set<String>, targetListId: String): Int {
        ensureMigrated()
        if (entryIds.isEmpty()) return 0
        return db.withTransaction {
            val inTarget = dao.entriesIn(targetListId).mapNotNull { it.toEntry() }
            val ids = inTarget.mapTo(HashSet()) { it.sourceId }
            val urls = inTarget.mapNotNullTo(HashSet()) { it.fullUrl.ifBlank { null } }
            val drop = mutableListOf<String>()
            var moved = 0
            for (e in dao.entriesById(entryIds.toList()).mapNotNull { it.toEntry() }) {
                if (e.listId == targetListId) continue
                if (e.sourceId in ids || (e.fullUrl.isNotBlank() && e.fullUrl in urls)) {
                    drop += e.id
                    continue
                }
                ids += e.sourceId
                if (e.fullUrl.isNotBlank()) urls += e.fullUrl
                val m = e.copy(listId = targetListId)
                dao.moveEntry(m.id, targetListId, entryJson(m))
                moved++
            }
            if (drop.isNotEmpty()) dao.deleteEntries(drop)
            moved
        }
    }

    /** Moves everything from [fromId] into [intoId] (skipping duplicates) and deletes [fromId]. */
    suspend fun mergeLists(fromId: String, intoId: String): Int {
        if (fromId == intoId) return 0
        ensureMigrated()
        val ids = dao.entriesIn(fromId).mapTo(HashSet()) { it.id }
        val moved = moveEntries(ids, intoId)
        deleteList(fromId)
        return moved
    }

    /** Returns true if the rename was applied, false if name is blank or already taken by another list. */
    suspend fun renameList(id: String, name: String): Boolean {
        ensureMigrated()
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        return db.withTransaction {
            val lists = dao.collections().mapNotNull { it.toList() }
            if (lists.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) return@withTransaction false
            updateList(id) { it.copy(name = trimmed) }
        }
    }

    /** Applies [change] to one collection. Returns false if it doesn't exist. */
    private suspend fun updateList(id: String, change: (LocalList) -> LocalList): Boolean {
        ensureMigrated()
        return db.withTransaction {
            val row = dao.collection(id) ?: return@withTransaction false
            val list = row.toList() ?: return@withTransaction false
            dao.upsertCollections(listOf(change(list).toRow(row.position)))
            true
        }
    }

    suspend fun setUseAsRotation(listId: String, enabled: Boolean) { updateList(listId) { it.copy(useAsRotation = enabled) } }

    suspend fun setBlurExempt(listId: String, exempt: Boolean) { updateList(listId) { it.copy(blurExempt = exempt) } }

    suspend fun setLocked(listId: String, locked: Boolean) { updateList(listId) { it.copy(isLocked = locked) } }

    suspend fun setCoverImage(listId: String, coverUrl: String) { updateList(listId) { it.copy(coverUrl = coverUrl) } }

    suspend fun setRotationTarget(listId: String, target: ScreenRotationTarget) { updateList(listId) { it.copy(rotationTarget = target) } }

    suspend fun setSmartRule(listId: String, rule: SmartRule?) { updateList(listId) { it.copy(smartRule = rule) } }

    suspend fun setRotationInterval(listId: String, minutes: Int?) { updateList(listId) { it.copy(rotationIntervalMinutes = minutes) } }

    suspend fun setLastRotationMs(listId: String, ms: Long) { updateList(listId) { it.copy(lastRotationMs = ms) } }

    suspend fun setMalConfig(listId: String, config: MalCollectionConfig?) { updateList(listId) { it.copy(malConfig = config) } }

    suspend fun addWallpaper(listId: String, wallpaper: BrainrotWallpaper): Boolean {
        ensureMigrated()
        return db.withTransaction {
            if (dao.countMatching(listId, wallpaper.id, wallpaper.fullUrl) > 0) return@withTransaction false
            val entry = LocalWallpaperEntry(
                listId = listId,
                sourceId = wallpaper.id,
                source = wallpaper.source,
                thumbUrl = wallpaper.thumbUrl,
                sampleUrl = wallpaper.sampleUrl,
                fullUrl = wallpaper.fullUrl,
                resolution = wallpaper.resolution,
                pageUrl = wallpaper.pageUrl,
                tags = wallpaper.tags,
                isVideo = wallpaper.isVideo,
                isNsfw = wallpaper.isNsfw
            )
            dao.insertEntries(listOf(entry.toRow()))
            true
        }
    }

    suspend fun addLocalImage(listId: String, relativePath: String) {
        val uuid = relativePath.substringAfterLast("/").substringBeforeLast(".")
        addWallpaperEntries(listOf(LocalWallpaperEntry(
            listId = listId,
            sourceId = uuid,
            source = "device",
            thumbUrl = relativePath,
            fullUrl = relativePath,
            resolution = "",
            pageUrl = "",
            tags = emptyList()
        )))
    }

    suspend fun addWallpaperEntry(entry: LocalWallpaperEntry) = addWallpaperEntries(listOf(entry))

    /** Removes several entries in one write. */
    suspend fun removeWallpapers(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        ensureMigrated()
        // SQLite caps bound parameters per statement; delete in chunks.
        db.withTransaction { entryIds.chunked(500).forEach { dao.deleteEntries(it) } }
    }

    /** Adds several entries in one write, skipping ones already in their collection. */
    suspend fun addWallpaperEntries(entries: List<LocalWallpaperEntry>) {
        if (entries.isEmpty()) return
        ensureMigrated()
        db.withTransaction {
            val existing = HashSet<Pair<String, String>>()
            entries.map { it.listId }.distinct().forEach { listId ->
                dao.entriesIn(listId).forEach { existing += it.listId to it.sourceId }
            }
            val fresh = entries.filter { existing.add(it.listId to it.sourceId) }
            if (fresh.isNotEmpty()) dao.insertEntries(fresh.map { it.toRow() })
        }
    }

    suspend fun removeWallpaper(entryId: String) = removeWallpapers(setOf(entryId))

    // --- rows ---

    private fun LocalList.toRow(position: Int) = CollectionRow(id = id, position = position, json = listJson(this))

    private fun CollectionRow.toList(): LocalList? = parseLists("[$json]").firstOrNull()

    private fun LocalWallpaperEntry.toRow() = EntryRow(
        id = id, listId = listId, sourceId = sourceId, source = source, fullUrl = fullUrl, json = entryJson(this),
    )

    private fun EntryRow.toEntry(): LocalWallpaperEntry? = parseWallpapers("[$json]").firstOrNull()

    private fun listJson(l: LocalList): String = JSONArray(serializeLists(listOf(l))).getJSONObject(0).toString()

    private fun entryJson(e: LocalWallpaperEntry): String = JSONArray(serializeWallpapers(listOf(e))).getJSONObject(0).toString()

    // --- serialization ---

    private fun parseLists(json: String): List<LocalList> = try {
        val arr = JSONArray(json)
        arr.mapObjectsSafely { o ->
            val smartRuleObj = o.optJSONObject("smartRule")
            val smartRule = if (smartRuleObj != null) SmartRule(
                requireAll = smartRuleObj.optJSONArray("requireAll")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                requireAny = smartRuleObj.optJSONArray("requireAny")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                excludeAny = smartRuleObj.optJSONArray("excludeAny")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            ) else null
            val malConfigObj = o.optJSONObject("malConfig")
            val malConfig = if (malConfigObj != null) {
                MalCollectionConfig(
                    animeTitle = malConfigObj.optString("animeTitle", ""),
                    animeQuery = malConfigObj.optString("animeQuery", ""),
                    characterTags = malConfigObj.optJSONArray("characterTags")
                        ?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
                        ?: emptyList(),
                    sourcePluginId = malConfigObj.optString("sourcePluginId").ifBlank { null },
                    sourceInstanceId = malConfigObj.optString("sourceInstanceId", ""),
                    fillCount = malConfigObj.optInt("fillCount", 25).coerceAtLeast(1),
                    matchAny = malConfigObj.optBoolean("matchAny", false),
                    autoAddToLibrary = malConfigObj.optBoolean("autoAddToLibrary", false),
                    nsfwOverride = malConfigObj.takeIf { it.has("nsfwOverride") }?.optBoolean("nsfwOverride"),
                    minResolution = runCatching {
                        MinResolution.valueOf(malConfigObj.optString("minResolution", MinResolution.ANY.name))
                    }.getOrDefault(MinResolution.ANY),
                    aspectRatio = runCatching {
                        AspectRatio.valueOf(malConfigObj.optString("aspectRatio", AspectRatio.ANY.name))
                    }.getOrDefault(AspectRatio.ANY),
                    useMalFilter = malConfigObj.optBoolean("useMalFilter", false),
                ).takeIf { it.animeTitle.isNotBlank() }
            } else null
            LocalList(
                id = o.getString("id"),
                name = o.getString("name"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                useAsRotation = o.optBoolean("useAsRotation", false),
                rotationTarget = runCatching { ScreenRotationTarget.valueOf(o.optString("rotationTarget", "BOTH")) }.getOrDefault(ScreenRotationTarget.BOTH),
                isLocked = o.optBoolean("isLocked", false),
                coverUrl = o.optString("coverUrl", ""),
                smartRule = smartRule,
                malConfig = malConfig,
                rotationIntervalMinutes = o.takeIf { it.has("rotationIntervalMinutes") }?.optInt("rotationIntervalMinutes"),
                lastRotationMs = o.optLong("lastRotationMs", 0L),
                blurExempt = o.optBoolean("blurExempt", false),
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
                    put("rotationTarget", l.rotationTarget.name)
                    put("isLocked", l.isLocked)
                    put("coverUrl", l.coverUrl)
                    put("blurExempt", l.blurExempt)
                    if (l.smartRule != null && !l.smartRule.isEmpty) {
                        put("smartRule", JSONObject().apply {
                            put("requireAll", JSONArray(l.smartRule.requireAll))
                            put("requireAny", JSONArray(l.smartRule.requireAny))
                            put("excludeAny", JSONArray(l.smartRule.excludeAny))
                        })
                    }
                    l.rotationIntervalMinutes?.let { put("rotationIntervalMinutes", it) }
                    if (l.lastRotationMs > 0L) put("lastRotationMs", l.lastRotationMs)
                    if (l.malConfig != null && l.malConfig.animeTitle.isNotBlank()) {
                        put("malConfig", JSONObject().apply {
                            put("animeTitle", l.malConfig.animeTitle)
                            put("animeQuery", l.malConfig.animeQuery)
                            put("characterTags", JSONArray(l.malConfig.characterTags))
                            put("sourcePluginId", l.malConfig.sourcePluginId ?: "")
                            put("sourceInstanceId", l.malConfig.sourceInstanceId)
                            put("fillCount", l.malConfig.fillCount)
                            put("matchAny", l.malConfig.matchAny)
                            put("autoAddToLibrary", l.malConfig.autoAddToLibrary)
                            l.malConfig.nsfwOverride?.let { put("nsfwOverride", it) }
                            put("minResolution", l.malConfig.minResolution.name)
                            put("aspectRatio", l.malConfig.aspectRatio.name)
                            put("useMalFilter", l.malConfig.useMalFilter)
                        })
                    }
                })
            }
        }.toString()

    private fun parseWallpapers(json: String): List<LocalWallpaperEntry> = try {
        val arr = JSONArray(json)
        arr.mapObjectsSafely { o ->
            val tagsArr = o.optJSONArray("tags")
            val listId = o.getString("listId")
            val sourceId = o.getString("sourceId")
            LocalWallpaperEntry(
                // Deterministic fallback so the same entry always gets the same ID
                // even for entries persisted before we started writing the "id" field.
                id = o.optString("id").ifBlank {
                    UUID.nameUUIDFromBytes("$listId:$sourceId".toByteArray()).toString()
                },
                listId = listId,
                sourceId = sourceId,
                source = o.optString("source", ""),
                thumbUrl = o.optString("thumbUrl", ""),
                sampleUrl = o.optString("sampleUrl", ""),
                fullUrl = o.optString("fullUrl", ""),
                resolution = o.optString("resolution", ""),
                pageUrl = o.optString("pageUrl", ""),
                tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.getString(it) } else emptyList(),
                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                isVideo = o.optBoolean("isVideo", false),
                isNsfw = o.optBoolean("isNsfw", false)
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
        }.toString()
}
