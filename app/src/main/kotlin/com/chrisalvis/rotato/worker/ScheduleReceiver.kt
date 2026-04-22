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
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.SchedulePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ENTRY_ID = "schedule_entry_id"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val NOTIF_ID_LOCKED = 7002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedPrefs = SchedulePreferences(context)
                val listPrefs = LocalListsPreferences(context)
                val entries = schedPrefs.entries.first()
                val fired = entries.find { it.id == entryId } ?: return@launch

                // Check if the scheduled list is locked
                val allLists = listPrefs.lists.first()
                val firedList = allLists.find { it.id == fired.listId }
                if (firedList?.isLocked == true) {
                    postLockedNotification(context, firedList.name)
                    ScheduleManager.schedule(context, fired)
                    return@launch
                }

                val scheduledListIds = entries.map { it.listId }.toSet()

                scheduledListIds.forEach { listId ->
                    val active = listId == fired.listId
                    listPrefs.setUseAsRotation(listId, active)
                    if (!active) removeRotationFiles(context, listId, listPrefs)
                }

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "schedule_trigger",
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<WallpaperWorker>().build(),
                    )

                ScheduleManager.schedule(context, fired)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postLockedNotification(context: Context, listName: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "browse")
        }
        val pi = PendingIntent.getActivity(
            context, NOTIF_ID_LOCKED, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, RotatoApp.CHANNEL_LOCKED_LIST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Collection locked")
            .setContentText("\"$listName\" is locked. Tap to unlock for today's schedule.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_LOCKED, notif)
    }

    private suspend fun removeRotationFiles(
        context: Context,
        listId: String,
        listPrefs: LocalListsPreferences,
    ) {
        val wallpapers = listPrefs.wallpapersForList(listId).first()
        val imageDir = File(context.filesDir, "rotato_images")
        wallpapers.forEach { entry ->
            val key = entry.sourceId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            imageDir.listFiles()?.filter { it.nameWithoutExtension == key }?.forEach { it.delete() }
        }
    }
}
