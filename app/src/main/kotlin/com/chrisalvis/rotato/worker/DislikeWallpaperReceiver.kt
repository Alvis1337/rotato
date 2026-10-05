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
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.sanitizeFilename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class DislikeWallpaperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = RotatoPreferences(context)
                val history = historyFromJson(prefs.historyJson.first())
                val current = history.firstOrNull()
                val lastThumbUrl = prefs.lastWallpaperThumbUrl.first()
                val lastFullUrl = prefs.lastWallpaperFullUrl.first()
                val lastSource = prefs.lastWallpaperSource.first()

                val wallpaper = if (current != null) {
                    FavoriteWallpaperReceiver.wallpaperFromUrls(
                        thumbUrl = lastThumbUrl.ifBlank { current.thumbUrl },
                        fullUrl = lastFullUrl.ifBlank { current.fullUrl },
                        source = lastSource.ifBlank { current.source },
                        pageUrl = current.pageUrl,
                        tags = current.tags,
                    )
                } else {
                    FavoriteWallpaperReceiver.wallpaperFromUrls(
                        thumbUrl = lastThumbUrl,
                        fullUrl = lastFullUrl,
                        source = lastSource,
                    )
                }

                if (wallpaper == null) {
                    postConfirmation(context, "Nothing to remove", "Wallpaper details are unavailable")
                    return@launch
                }

                prefs.blockUrl(wallpaper.fullUrl)
                prefs.blockUrl(wallpaper.thumbUrl)
                prefs.addBlockedImageKey(wallpaper.id)

                // Remove from any collections that contain this wallpaper
                val listPrefs = LocalListsPreferences(context)
                val matched = listPrefs.allWallpapers.first()
                    .filter { it.sourceId == wallpaper.id || (wallpaper.fullUrl.isNotBlank() && it.fullUrl == wallpaper.fullUrl) }

                // Pool files are named after the collection entry's sourceId (or, for direct
                // Discover downloads, the URL's file stem), not the full URL used as wallpaper.id
                // for remote sources, so match on all of them or the file keeps rotating.
                val keys = buildSet {
                    add(sanitizeFilename(wallpaper.id))
                    matched.forEach { add(sanitizeFilename(it.sourceId)) }
                    if (wallpaper.fullUrl.isNotBlank()) {
                        add(sanitizeFilename(wallpaper.fullUrl.substringAfterLast('/').substringBeforeLast('.')))
                    }
                }
                val imageDir = File(context.filesDir, "rotato_images")
                imageDir.listFiles()?.filter { it.isFile && it.nameWithoutExtension in keys }?.forEach { it.delete() }

                matched.forEach { listPrefs.removeWallpaper(it.id) }

                postConfirmation(context, "Removed", "Won't show this wallpaper again")
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
        nm.notify(NOTIF_ID_DISLIKED, notif)
    }

    companion object {
        private const val NOTIF_ID_DISLIKED = 1004
    }
}
