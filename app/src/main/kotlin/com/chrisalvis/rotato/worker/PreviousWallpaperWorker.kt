package com.chrisalvis.rotato.worker

import android.content.Context
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chrisalvis.rotato.data.applyPreviousWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Restores the previous wallpaper; used by the widget's Back button. */
class PreviousWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val error = try {
            applyPreviousWallpaper(applicationContext)
        } catch (e: Exception) {
            e.message ?: "Couldn't restore the previous wallpaper"
        }
        if (error != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
            }
            return Result.failure()
        }
        RotatoWidgetProvider.refreshAll(applicationContext)
        return Result.success()
    }
}
