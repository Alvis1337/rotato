package com.chrisalvis.rotato.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.ScheduleEntry
import com.chrisalvis.rotato.data.SchedulePreferences
import com.chrisalvis.rotato.data.sanitizeFilename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ENTRY_ID = "schedule_entry_id"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val TAG = "ScheduleReceiver"

        /**
         * Apply a schedule entry: update rotation flags, sync image pool, enqueue WallpaperWorker,
         * and arm the next alarm. The biometric lock is intentionally not checked here — scheduled
         * application is an explicit user authorization and must work even when the process is dead.
         */
        /**
         * Returns the trigger result string (e.g. "applied (12 images)") that was also
         * recorded to the schedule status. Callers can surface this in a snackbar.
         */
        suspend fun applyEntry(
            context: Context,
            entry: ScheduleEntry,
            allEntries: List<ScheduleEntry>,
            schedPrefs: SchedulePreferences,
            listPrefs: LocalListsPreferences,
        ): String {
            schedPrefs.clearLockedEvent(entry.id)

            val scheduledListIds = allEntries.filter { it.enabled }.map { it.listId }.toSet()
            scheduledListIds.forEach { listId ->
                val active = listId == entry.listId
                listPrefs.setUseAsRotation(listId, active)
                if (!active) removeRotationFiles(context, listId, listPrefs)
            }

            // Count only images belonging to the active list (not all files in the dir).
            val imagesInPool = syncRotationPool(context, entry.listId, listPrefs)
            val triggerResult = if (imagesInPool == 0) "applied (empty pool!)" else "applied ($imagesInPool images)"
            schedPrefs.recordTrigger(entry.id, triggerResult)

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "schedule_trigger",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WallpaperWorker>().build(),
                )

            ScheduleManager.schedule(context, entry)
            return triggerResult
        }

        private suspend fun syncRotationPool(
            context: Context,
            listId: String,
            listPrefs: LocalListsPreferences,
        ): Int {
            val wallpapers = listPrefs.wallpapersForList(listId).first()
            val imageDir = File(context.filesDir, "rotato_images").also { it.mkdirs() }
            val feedRepo = FeedRepository(imageDir)
            wallpapers.forEach { entry ->
                val key = sanitizeFilename(entry.sourceId)
                val alreadyOnDisk = imageDir.listFiles()?.any { it.nameWithoutExtension == key } == true
                if (alreadyOnDisk) return@forEach
                when {
                    entry.source == "device" && entry.fullUrl.startsWith("list_images/") -> {
                        val src = File(context.filesDir, entry.fullUrl)
                        if (src.exists()) {
                            val ext = src.extension.ifBlank { "jpg" }
                            runCatching { src.copyTo(File(imageDir, "$key.$ext"), overwrite = true) }
                        }
                    }
                    entry.fullUrl.isNotBlank() -> feedRepo.downloadWallpaper(entry.sourceId, entry.fullUrl)
                }
            }
            // Count only this list's wallpapers that are now present on disk.
            return wallpapers.count { wp ->
                val key = sanitizeFilename(wp.sourceId)
                imageDir.listFiles()?.any { it.nameWithoutExtension == key } == true
            }
        }

        private suspend fun removeRotationFiles(
            context: Context,
            listId: String,
            listPrefs: LocalListsPreferences,
        ) {
            val wallpapers = listPrefs.wallpapersForList(listId).first()
            val imageDir = File(context.filesDir, "rotato_images")
            wallpapers.forEach { entry ->
                val key = sanitizeFilename(entry.sourceId)
                imageDir.listFiles()?.filter { it.nameWithoutExtension == key }?.forEach { it.delete() }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        Log.d(TAG, "onReceive: entryId=$entryId")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val schedPrefs = SchedulePreferences(context)
            try {
                val listPrefs = LocalListsPreferences(context)
                val entries = schedPrefs.entries.first()
                val fired = entries.find { it.id == entryId } ?: run {
                    // Entry was deleted after the alarm was set — nothing to do.
                    schedPrefs.recordTrigger(entryId, "entry not found")
                    return@launch
                }

                Log.d(TAG, "  listId=${fired.listId}")
                applyEntry(context, fired, entries, schedPrefs, listPrefs)
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error processing entry $entryId", e)
                runCatching { schedPrefs.recordTrigger(entryId, "error: ${e.javaClass.simpleName}") }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
