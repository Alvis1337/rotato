package com.chrisalvis.rotato.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.flow.first
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

        val targetFile = if (settings.shuffleMode) {
            images.random()
        } else {
            val nextIndex = settings.currentIndex % images.size
            prefs.setCurrentIndex((nextIndex + 1) % images.size)
            images[nextIndex]
        }

        return try {
            val bitmap = loadScaledBitmap(targetFile.absolutePath)
                ?: return Result.failure()

            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            wallpaperManager.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            )
            bitmap.recycle()

            prefs.recordRotation()

            // Sub-15-min intervals can't use PeriodicWorkRequest (OS enforces 15 min floor).
            // Instead, each worker schedules its own successor.
            val intervalMinutes = inputData.getLong(KEY_INTERVAL_MINUTES, 0L)
            if (intervalMinutes in 1..14 && settings.isEnabled) {
                scheduleNextRun(intervalMinutes)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
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

    companion object {
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val CHAIN_WORK_NAME = "rotato_chain"
    }
}
