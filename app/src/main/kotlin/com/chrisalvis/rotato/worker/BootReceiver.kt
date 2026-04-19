package com.chrisalvis.rotato.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.ui.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = RotatoPreferences(context)
        CoroutineScope(Dispatchers.IO).launch {
            val settings = prefs.settings.first()
            if (!settings.isEnabled) return@launch

            val intervalMinutes = settings.intervalMinutes.toLong()
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val wm = WorkManager.getInstance(context)
            if (intervalMinutes < 15) {
                wm.cancelUniqueWork(HomeViewModel.WORK_NAME)
                val req = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(WallpaperWorker.KEY_INTERVAL_MINUTES to intervalMinutes))
                    .build()
                wm.enqueueUniqueWork(WallpaperWorker.CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
            } else {
                val flexMinutes = (intervalMinutes / 4)
                    .coerceAtLeast(PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS / 60_000)
                    .coerceAtMost(15L)
                val req = PeriodicWorkRequestBuilder<WallpaperWorker>(
                    intervalMinutes, TimeUnit.MINUTES,
                    flexMinutes, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(workDataOf(WallpaperWorker.KEY_INTERVAL_MINUTES to intervalMinutes))
                    .build()
                wm.cancelUniqueWork(WallpaperWorker.CHAIN_WORK_NAME)
                wm.enqueueUniquePeriodicWork(HomeViewModel.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
            }
        }
    }
}
