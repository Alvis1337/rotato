package com.chrisalvis.rotato.worker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import kotlin.math.roundToInt
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ImageRepository(applicationContext)
        val prefs = RotatoPreferences(applicationContext)

        val images = repository.getImages()
        if (images.isEmpty()) return Result.success()

        val settings = prefs.settings.first()

        // Auto-pause: night window
        val autoPause = prefs.autoPauseSettings.first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (autoPause.isInNightWindow(currentHour)) return Result.success()

        // Auto-pause: charging
        if (autoPause.chargingEnabled) {
            val bm = applicationContext.getSystemService(BatteryManager::class.java)
            if (bm?.isCharging == true) return Result.success()
        }

        // Duplicate guard in shuffle mode: avoid recently shown wallpapers
        val history = historyFromJson(prefs.historyJson.first())
        val targetFile = if (settings.shuffleMode) {
            val recentPaths = history
                .take((images.size - 1).coerceAtLeast(0).coerceAtMost(10))
                .map { it.thumbUrl }
                .toSet()
            val fresh = images.filter { it.absolutePath !in recentPaths }
            fresh.ifEmpty { images }.random()
        } else {
            val nextIndex = settings.currentIndex % images.size
            prefs.setCurrentIndex((nextIndex + 1) % images.size)
            images[nextIndex]
        }

        return try {
            val bitmap = loadScaledBitmap(targetFile.absolutePath)
                ?: return Result.failure()

            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val flags = when (settings.wallpaperTarget) {
                WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            val metrics = applicationContext.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels

            val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
            val scaledW = (bitmap.width * scale).roundToInt()
            val scaledH = (bitmap.height * scale).roundToInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            val srcX = ((scaledW - screenW) / 2).coerceAtLeast(0)
            val srcY = ((scaledH - screenH) / 2).coerceAtLeast(0)
            val screenBitmap = Bitmap.createBitmap(scaled, srcX, srcY, screenW, screenH)
            if (scaled != bitmap) scaled.recycle()

            try {
                wallpaperManager.setBitmap(screenBitmap, null, true, flags)

                prefs.recordRotation()
                prefs.incrementTotalRotations()

                // Look up original source info from collections for richer history
                val allEntries = LocalListsPreferences(applicationContext).allWallpapers.first()
                val fileKey = targetFile.nameWithoutExtension
                val matchingEntry = allEntries.find { sanitize(it.sourceId) == fileKey }

                val historyItem = WallpaperHistoryItem(
                    thumbUrl = targetFile.absolutePath,
                    fullUrl = matchingEntry?.fullUrl ?: targetFile.absolutePath,
                    source = matchingEntry?.source ?: "local",
                    timestamp = System.currentTimeMillis(),
                    tags = matchingEntry?.tags ?: emptyList(),
                    pageUrl = matchingEntry?.pageUrl ?: ""
                )
                val updatedHistory = history.toMutableList().apply { add(0, historyItem) }
                prefs.setHistoryJson(updatedHistory.take(50).toJson())

                postWallpaperSetNotification(screenBitmap)
            } finally {
                bitmap.recycle()
                screenBitmap.recycle()
            }

            RotatoWidgetProvider.refreshAll(applicationContext)

            if (images.size in 1..4) {
                postLowQueueNotification(images.size)
            }

            val intervalMinutes = inputData.getLong(KEY_INTERVAL_MINUTES, 0L)
            if (intervalMinutes in 1..14 && settings.isEnabled) {
                scheduleNextRun(intervalMinutes)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun postWallpaperSetNotification(bitmap: Bitmap) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipIntent = PendingIntent.getBroadcast(
            applicationContext, 1,
            Intent(applicationContext, SkipWallpaperReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val keepIntent = PendingIntent.getBroadcast(
            applicationContext, 2,
            Intent(applicationContext, FavoriteWallpaperReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val thumb = Bitmap.createScaledBitmap(bitmap, 128, 72, true)
        val notif = NotificationCompat.Builder(applicationContext, RotatoApp.CHANNEL_WALLPAPER_SET)
            .setContentTitle("Wallpaper changed")
            .setContentText("Tap to open Rotato")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(thumb)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumb))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, "Skip", skipIntent)
            .addAction(0, "Keep", keepIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(NOTIF_ID_WALLPAPER_SET, notif)
    }

    private fun postLowQueueNotification(count: Int) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            applicationContext, 3,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, RotatoApp.CHANNEL_LOW_QUEUE)
            .setContentTitle("Wallpaper queue is low")
            .setContentText("Only $count photo${if (count == 1) "" else "s"} left — add more to keep rotating")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIF_ID_LOW_QUEUE, notif)
    }

    private fun scheduleNextRun(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val next = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_INTERVAL_MINUTES to intervalMinutes))
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, next)
    }

    private fun loadScaledBitmap(path: String): Bitmap? {
        val metrics = applicationContext.resources.displayMetrics
        val targetWidth = metrics.widthPixels
        val targetHeight = metrics.heightPixels

        val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOnly)

        var sampleSize = 1
        var w = boundsOnly.outWidth
        var h = boundsOnly.outHeight
        while (w / 2 >= targetWidth && h / 2 >= targetHeight) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

    companion object {
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val CHAIN_WORK_NAME = "rotato_chain"
        private const val NOTIF_ID_WALLPAPER_SET = 1001
        private const val NOTIF_ID_LOW_QUEUE = 1002
    }
}
