package com.chrisalvis.rotato.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.historyFromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class FavoriteWallpaperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = RotatoPreferences(context)
                val listsPrefs = LocalListsPreferences(context)

                val history = historyFromJson(prefs.historyJson.first())
                val current = history.firstOrNull() ?: return@launch

                val lists = listsPrefs.lists.first()
                val liked = lists.firstOrNull { it.name == LIKED_LIST_NAME }
                    ?: listsPrefs.createList(LIKED_LIST_NAME)

                val sourceId = if (current.source == "local") {
                    File(current.fullUrl).nameWithoutExtension
                } else {
                    current.fullUrl
                }

                val alreadySaved = listsPrefs.allWallpapers.first()
                    .any { it.listId == liked.id && it.sourceId == sourceId }
                if (alreadySaved) {
                    postConfirmation(context, "Already in Liked", "This wallpaper is already saved")
                    return@launch
                }

                listsPrefs.addWallpaperEntry(
                    LocalWallpaperEntry(
                        listId = liked.id,
                        sourceId = sourceId,
                        source = current.source,
                        thumbUrl = current.thumbUrl,
                        fullUrl = current.fullUrl,
                        resolution = "",
                        pageUrl = current.pageUrl,
                        tags = current.tags
                    )
                )

                postConfirmation(context, "Saved to Liked!", "Added to your Liked collection")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postConfirmation(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, RotatoApp.CHANNEL_WALLPAPER_SET)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIF_ID_FAVORITED, notif)
    }

    companion object {
        const val LIKED_LIST_NAME = "Liked"
        private const val NOTIF_ID_FAVORITED = 1003
    }
}
