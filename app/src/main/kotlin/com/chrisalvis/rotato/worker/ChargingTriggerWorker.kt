package com.chrisalvis.rotato.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * "New wallpaper when charging starts". A manifest receiver for ACTION_POWER_CONNECTED is never
 * delivered since Android 8, so the trigger is a WorkManager job instead: an armed request that
 * requires charging runs once the phone is plugged in and rotates, then checks back every
 * [UNPLUG_CHECK_MINUTES] until the phone is unplugged and only then arms again — one charge, one
 * new wallpaper. Survives reboots and process death like any other WorkManager job.
 */
class ChargingTriggerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = RotatoPreferences(applicationContext)
        if (!prefs.chargingTriggerEnabled.first()) return Result.success()
        val wm = WorkManager.getInstance(applicationContext)

        if (inputData.getBoolean(KEY_WAIT_UNPLUG, false)) {
            // Still on the charger: look again later. Unplugged: arm for the next charge.
            val next = if (isPluggedIn(applicationContext)) waitForUnplugRequest() else armedRequest()
            wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, next)
            return Result.success()
        }

        if (prefs.settings.first().isEnabled) {
            // Manual, so the "pause while charging" auto-pause doesn't swallow it.
            wm.enqueueUniqueWork(
                ROTATION_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
                    .build(),
            )
        }
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, waitForUnplugRequest())
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "charging_trigger"
        const val ROTATION_WORK_NAME = "charging_trigger_rotation"
        private const val KEY_WAIT_UNPLUG = "wait_unplug"
        private const val UNPLUG_CHECK_MINUTES = 15L

        /**
         * Arms the trigger when [enabled], keeping one that's already armed or waiting; removes it
         * otherwise. Turning it on while plugged in waits for the next charge instead of firing now.
         */
        fun sync(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val request = if (isPluggedIn(context)) waitForUnplugRequest() else armedRequest()
                wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        }

        private fun armedRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ChargingTriggerWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()

        private fun waitForUnplugRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ChargingTriggerWorker>()
                .setInitialDelay(UNPLUG_CHECK_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_WAIT_UNPLUG to true))
                .build()

        /** Plugged into any power source, whether or not the battery is still filling. */
        private fun isPluggedIn(context: Context): Boolean = runCatching {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        }.getOrDefault(false)
    }
}
