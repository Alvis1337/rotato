package com.chrisalvis.rotato.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Collections and their wallpapers in SQLite. They used to be two JSON strings in DataStore that
 * every add, move or delete re-parsed and rewrote in full, which got slow (and risky to write)
 * with thousands of saved wallpapers. Each row keeps the same JSON as before in [json], with the
 * columns lookups need pulled out beside it.
 */
@Entity(tableName = "collections")
data class CollectionRow(
    @PrimaryKey val id: String,
    /** Grid order, as the user arranged it. */
    val position: Int,
    val json: String,
)

@Entity(
    tableName = "entries",
    indices = [Index("id", unique = true), Index("listId"), Index(value = ["listId", "sourceId"])],
)
data class EntryRow(
    /** Insertion order, which is the order entries were saved in. */
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val id: String,
    val listId: String,
    val sourceId: String,
    val source: String,
    val fullUrl: String,
    val json: String,
)

@Dao
interface CollectionsDao {
    @Query("SELECT * FROM collections ORDER BY position")
    fun collectionsFlow(): Flow<List<CollectionRow>>

    @Query("SELECT * FROM collections ORDER BY position")
    suspend fun collections(): List<CollectionRow>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun collection(id: String): CollectionRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollections(rows: List<CollectionRow>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM collections")
    suspend fun maxPosition(): Int

    @Query("SELECT * FROM entries ORDER BY seq")
    fun entriesFlow(): Flow<List<EntryRow>>

    @Query("SELECT * FROM entries ORDER BY seq")
    suspend fun entries(): List<EntryRow>

    @Query("SELECT * FROM entries WHERE listId = :listId ORDER BY seq")
    suspend fun entriesIn(listId: String): List<EntryRow>

    @Query("SELECT * FROM entries WHERE id IN (:ids)")
    suspend fun entriesById(ids: List<String>): List<EntryRow>

    @Query("SELECT COUNT(*) FROM entries WHERE listId = :listId AND (sourceId = :sourceId OR (:fullUrl != '' AND fullUrl = :fullUrl))")
    suspend fun countMatching(listId: String, sourceId: String, fullUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(rows: List<EntryRow>)

    @Query("UPDATE entries SET listId = :listId, json = :json WHERE id = :id")
    suspend fun moveEntry(id: String, listId: String, json: String)

    @Query("DELETE FROM entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<String>)

    @Query("DELETE FROM entries WHERE listId = :listId")
    suspend fun deleteEntriesIn(listId: String)

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun collectionCount(): Int

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun entryCount(): Int
}

@Database(entities = [CollectionRow::class, EntryRow::class], version = 1, exportSchema = false)
abstract class CollectionsDatabase : RoomDatabase() {
    abstract fun dao(): CollectionsDao

    companion object {
        const val FILE_NAME = "collections.db"

        @Volatile private var instance: CollectionsDatabase? = null

        fun get(context: Context): CollectionsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CollectionsDatabase::class.java, FILE_NAME)
                // One file (no -wal sidecar), so Google Drive backup and device transfer copy a
                // complete database.
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
                .also { instance = it }
        }
    }
}
