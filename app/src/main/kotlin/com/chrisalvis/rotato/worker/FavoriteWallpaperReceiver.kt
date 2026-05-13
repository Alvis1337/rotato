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
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
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
                val current = history.firstOrNull()
                val lastThumbUrl = prefs.lastWallpaperThumbUrl.first()
                val lastFullUrl = prefs.lastWallpaperFullUrl.first()
                val lastSource = prefs.lastWallpaperSource.first()
                val wallpaper = if (current != null) {
                    wallpaperFromUrls(
                        thumbUrl = lastThumbUrl.ifBlank { current.thumbUrl },
                        fullUrl = lastFullUrl.ifBlank { current.fullUrl },
                        source = lastSource.ifBlank { current.source },
                        pageUrl = current.pageUrl,
                        tags = current.tags,
                    )
                } else {
                    wallpaperFromUrls(
                        thumbUrl = lastThumbUrl,
                        fullUrl = lastFullUrl,
                        source = lastSource,
                    )
                }

                when {
                    wallpaper == null -> postConfirmation(context, "Nothing to save", "Wallpaper details are unavailable")
                    saveWallpaperToFavorites(listsPrefs, wallpaper) -> {
                        postConfirmation(context, "Saved to Favorites!", "Added to your Favorites collection")
                    }
                    else -> postConfirmation(context, "Already in Favorites", "This wallpaper is already saved")
                }
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
        const val FAVORITES_LIST_NAME = "Favorites"
        private const val NOTIF_ID_FAVORITED = 1003

        suspend fun saveWallpaperToFavorites(
            listsPrefs: LocalListsPreferences,
            wallpaper: BrainrotWallpaper,
        ): Boolean {
            val favorites = getOrCreateFavoritesList(listsPrefs)
            return listsPrefs.addWallpaper(favorites.id, wallpaper)
        }

        fun wallpaperFromHistory(item: WallpaperHistoryItem): BrainrotWallpaper? = wallpaperFromUrls(
            thumbUrl = item.thumbUrl,
            fullUrl = item.fullUrl,
            source = item.source,
            pageUrl = item.pageUrl,
            tags = item.tags,
        )

        fun wallpaperFromUrls(
            thumbUrl: String,
            fullUrl: String,
            source: String,
            pageUrl: String = "",
            tags: List<String> = emptyList(),
        ): BrainrotWallpaper? {
            val resolvedFullUrl = fullUrl.ifBlank { thumbUrl }.trim()
            val resolvedThumbUrl = thumbUrl.ifBlank { resolvedFullUrl }.trim()
            val resolvedSource = source.ifBlank { "local" }
            val sourceId = favoriteSourceId(resolvedSource, resolvedFullUrl, resolvedThumbUrl)
            if (sourceId.isBlank() || resolvedFullUrl.isBlank()) return null
            return BrainrotWallpaper(
                id = sourceId,
                source = resolvedSource,
                thumbUrl = resolvedThumbUrl,
                sampleUrl = resolvedThumbUrl,
                fullUrl = resolvedFullUrl,
                resolution = "",
                pageUrl = pageUrl,
                tags = tags,
            )
        }

        private suspend fun getOrCreateFavoritesList(listsPrefs: LocalListsPreferences): LocalList {
            return listsPrefs.lists.first().firstOrNull { it.name.equals(FAVORITES_LIST_NAME, ignoreCase = true) }
                ?: listsPrefs.createList(FAVORITES_LIST_NAME)
        }

        private fun favoriteSourceId(source: String, fullUrl: String, thumbUrl: String): String {
            val key = fullUrl.ifBlank { thumbUrl }
            return if (source == "local" || source == "device") {
                File(key).nameWithoutExtension
            } else {
                key
            }
        }
    }
}
