package com.chrisalvis.rotato.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
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
        private const val RETRY_INTERVAL_MS = 5 * 60_000L
        const val RETRY_WINDOW_MS = 60 * 60_000L

        /** Stable per-entry notification ID so two locked entries each get their own notification. */
        fun lockedNotifId(entryId: String): Int = try {
            java.util.UUID.fromString(entryId).leastSignificantBits.toInt() and Int.MAX_VALUE
        } catch (_: IllegalArgumentException) {
            entryId.hashCode() and Int.MAX_VALUE
        }

        /**
         * Apply a schedule entry: update rotation flags, sync image pool, enqueue WallpaperWorker,
         * and arm the next alarm. Called by [ScheduleReceiver.onReceive] after the lock check, and
         * directly by [com.chrisalvis.rotato.ui.BrowseViewModel] when a locked collection is
         * unlocked so the schedule applies immediately without waiting for a retry alarm.
         */
        suspend fun applyEntry(
            context: Context,
            entry: ScheduleEntry,
            allEntries: List<ScheduleEntry>,
            schedPrefs: SchedulePreferences,
            listPrefs: LocalListsPreferences,
        ) {
            schedPrefs.clearLockedEvent(entry.id)

            val scheduledListIds = allEntries.filter { it.enabled }.map { it.listId }.toSet()
            scheduledListIds.forEach { listId ->
                val active = listId == entry.listId
                listPrefs.setUseAsRotation(listId, active)
                if (!active) removeRotationFiles(context, listId, listPrefs)
            }

            syncRotationPool(context, entry.listId, listPrefs)

            val imagesInPool = File(context.filesDir, "rotato_images")
                .listFiles()?.count { it.isFile } ?: 0
            val triggerResult = if (imagesInPool == 0) "applied (empty pool!)" else "applied ($imagesInPool images)"
            schedPrefs.recordTrigger(entry.id, triggerResult)

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "schedule_trigger",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WallpaperWorker>().build(),
                )

            ScheduleManager.schedule(context, entry)
        }

        private suspend fun syncRotationPool(
            context: Context,
            listId: String,
            listPrefs: LocalListsPreferences,
        ) {
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
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedPrefs = SchedulePreferences(context)
                val listPrefs = LocalListsPreferences(context)
                val entries = schedPrefs.entries.first()
                val fired = entries.find { it.id == entryId } ?: run {
                    // Entry was deleted after the alarm was set — nothing to do.
                    schedPrefs.recordTrigger(entryId, "entry not found")
                    return@launch
                }

                val allLists = listPrefs.lists.first()
                val firedList = allLists.find { it.id == fired.listId }
                // Also check the session-level unlock (covers biometric-verify-only case where
                // isLocked stays true in DataStore but the user granted session access).
                val sessionUnlocked = (context.applicationContext as? RotatoApp)
                    ?.unlockedListIds?.value?.contains(fired.listId) == true
                if (firedList?.isLocked == true && !sessionUnlocked) {
                    schedPrefs.recordLockedEvent(entryId)
                    schedPrefs.recordTrigger(entryId, "blocked: locked")
                    postLockedNotification(context, entryId, firedList.name)
                    val scheduledTodayMs = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, fired.startHour)
                        set(java.util.Calendar.MINUTE, fired.startMinute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val pastWindow = System.currentTimeMillis() > scheduledTodayMs + RETRY_WINDOW_MS
                    if (pastWindow) {
                        schedPrefs.clearLockedEvent(entryId)
                        ScheduleManager.schedule(context, fired)
                    } else {
                        ScheduleManager.scheduleAt(context, fired.id, System.currentTimeMillis() + RETRY_INTERVAL_MS)
                    }
                    return@launch
                }

                schedPrefs.clearLockedEvent(entryId)

                applyEntry(context, fired, entries, schedPrefs, listPrefs)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postLockedNotification(context: Context, entryId: String, listName: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return

        val notifId = lockedNotifId(entryId)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "browse")
        }
        val pi = PendingIntent.getActivity(
            context, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, RotatoApp.CHANNEL_LOCKED_LIST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Collection locked")
            .setContentText("\"$listName\" is locked. Unlock it in Collections and the schedule will apply immediately.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(notifId, notif)
    }
}
