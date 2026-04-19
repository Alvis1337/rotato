package com.chrisalvis.rotato.worker

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.ui.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RotationTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        scope.launch {
            val prefs = RotatoPreferences(applicationContext)
            val enabled = prefs.settings.first().isEnabled
            updateTile(enabled)
        }
    }

    override fun onClick() {
        scope.launch {
            val prefs = RotatoPreferences(applicationContext)
            val repository = ImageRepository(applicationContext)
            val settings = prefs.settings.first()
            val newEnabled = !settings.isEnabled

            prefs.setEnabled(newEnabled)

            val workManager = WorkManager.getInstance(applicationContext)
            if (newEnabled && repository.getImages().isNotEmpty()) {
                scheduleRotation(workManager, settings.intervalMinutes.toLong())
            } else {
                workManager.cancelUniqueWork(HomeViewModel.WORK_NAME)
                workManager.cancelUniqueWork(WallpaperWorker.CHAIN_WORK_NAME)
            }

            updateTile(newEnabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "On" else "Off"
        }
        tile.updateTile()
    }

    private fun scheduleRotation(workManager: WorkManager, intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        if (intervalMinutes < 15) {
            workManager.cancelUniqueWork(HomeViewModel.WORK_NAME)
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(WallpaperWorker.KEY_INTERVAL_MINUTES to intervalMinutes))
                .build()
            workManager.enqueueUniqueWork(WallpaperWorker.CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        } else {
            val flexMinutes = (intervalMinutes / 4)
                .coerceAtLeast(PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS / 60_000)
                .coerceAtMost(15L)
            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                flexMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(WallpaperWorker.KEY_INTERVAL_MINUTES to intervalMinutes))
                .build()
            workManager.cancelUniqueWork(WallpaperWorker.CHAIN_WORK_NAME)
            workManager.enqueueUniquePeriodicWork(
                HomeViewModel.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
