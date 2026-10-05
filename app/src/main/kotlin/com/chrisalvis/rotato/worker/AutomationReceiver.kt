package com.chrisalvis.rotato.worker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Actions other apps can trigger (Tasker, MacroDroid, Automate, Shortcuts):
 *  - [ACTION_NEXT]: next wallpaper now
 *  - [ACTION_PREVIOUS]: go back to the previous wallpaper
 *  - [ACTION_SAVE]: save the current wallpaper to Favorites
 *  - [ACTION_SWITCH_COLLECTION] with [EXTRA_COLLECTION] (a collection's name): rotate from only
 *    that collection, then change the wallpaper
 * Send them as a broadcast to this receiver, or start [AutomationActivity] with the same action
 * (that's what the launcher shortcuts and Routines use).
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEXT = "com.chrisalvis.rotato.action.NEXT"
        const val ACTION_PREVIOUS = "com.chrisalvis.rotato.action.PREVIOUS"
        const val ACTION_SAVE = "com.chrisalvis.rotato.action.SAVE"
        const val ACTION_SWITCH_COLLECTION = "com.chrisalvis.rotato.action.SWITCH_COLLECTION"
        const val EXTRA_COLLECTION = "collection"

        suspend fun handle(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_NEXT -> rotateNow(context)
                ACTION_PREVIOUS -> WorkManager.getInstance(context).enqueueUniqueWork(
                    "automation_previous",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<PreviousWallpaperWorker>().build()
                )
                ACTION_SAVE -> context.sendBroadcast(Intent(context, FavoriteWallpaperReceiver::class.java))
                ACTION_SWITCH_COLLECTION -> switchCollection(context, intent.getStringExtra(EXTRA_COLLECTION).orEmpty())
            }
        }

        private fun rotateNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "automation_next",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
                    .build(),
            )
        }

        private suspend fun switchCollection(context: Context, name: String) {
            val listPrefs = LocalListsPreferences(context)
            val lists = listPrefs.lists.first()
            val target = lists.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            // Locked collections stay out of reach while NSFW features are hidden.
            val blocked = target?.isLocked == true && RotatoPreferences(context).nsfwHidden.first()
            if (target == null || blocked) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Rotato: no collection named \"$name\"", Toast.LENGTH_SHORT).show()
                }
                return
            }
            lists.filter { it.useAsRotation && it.id != target.id }.forEach { other ->
                listPrefs.setUseAsRotation(other.id, false)
                ScheduleReceiver.removeRotationFiles(context, other.id, listPrefs)
            }
            listPrefs.setUseAsRotation(target.id, true)
            ScheduleReceiver.syncRotationPool(context, setOf(target.id), listPrefs)
            rotateNow(context)
        }
    }
}

/** No-UI entry point for launcher shortcuts and Routines; forwards to [AutomationReceiver]. */
class AutomationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forwarded = Intent(intent).setClass(this, AutomationReceiver::class.java)
        sendBroadcast(forwarded)
        val label = when (intent.action) {
            AutomationReceiver.ACTION_NEXT -> "Next wallpaper"
            AutomationReceiver.ACTION_PREVIOUS -> "Previous wallpaper"
            AutomationReceiver.ACTION_SAVE -> "Saving current wallpaper"
            AutomationReceiver.ACTION_SWITCH_COLLECTION -> "Switching collection"
            else -> null
        }
        label?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        finish()
    }
}
