package com.chrisalvis.rotato.worker

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chrisalvis.rotato.data.SchedulePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms schedule alarms as exact ones once the user grants the exact-alarm permission. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleManager.scheduleAll(context, SchedulePreferences(context).entries.first())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
