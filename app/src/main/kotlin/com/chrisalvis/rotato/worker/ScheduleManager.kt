package com.chrisalvis.rotato.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chrisalvis.rotato.data.ScheduleEntry
import java.util.Calendar

object ScheduleManager {

    fun scheduleAll(context: Context, entries: List<ScheduleEntry>) {
        entries.forEach { cancel(context, it.id) }
        entries.filter { it.enabled }.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, entry: ScheduleEntry) {
        val triggerMs = nextTriggerTime(entry) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, entry.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancel(context: Context, entryId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, entryId))
    }

    /** Returns the next wall-clock time (ms since epoch) this entry should fire, or null. */
    fun nextTriggerTime(entry: ScheduleEntry): Long? {
        if (entry.days.isEmpty()) return null
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, entry.startHour)
            set(Calendar.MINUTE, entry.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (cal.get(Calendar.DAY_OF_WEEK) in entry.days && cal.after(now)) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun pendingIntent(context: Context, entryId: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_ENTRY_ID, entryId)
        }
        // Derive a stable positive int from the UUID string. Using leastSignificantBits of the
        // parsed UUID gives better distribution than String.hashCode() and avoids sign issues.
        val requestCode = try {
            java.util.UUID.fromString(entryId).leastSignificantBits.toInt() and Int.MAX_VALUE
        } catch (_: IllegalArgumentException) {
            entryId.hashCode() and Int.MAX_VALUE
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
