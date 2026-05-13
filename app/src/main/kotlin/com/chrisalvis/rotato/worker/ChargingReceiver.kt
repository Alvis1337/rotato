package com.chrisalvis.rotato.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = RotatoPreferences(context)
                val settings = prefs.settings.first()
                val chargingTriggerEnabled = prefs.chargingTriggerEnabled.first()
                if (!chargingTriggerEnabled || !settings.isEnabled) return@launch

                WorkManager.getInstance(context).enqueueUniqueWork(
                    CHARGING_TRIGGER_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WallpaperWorker>().build(),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHARGING_TRIGGER_WORK_NAME = "charging_trigger_rotation"
    }
}
