package com.chrisalvis.rotato.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
