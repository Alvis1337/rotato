package com.chrisalvis.rotato.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chrisalvis.rotato.data.ScheduleEntry
import java.util.Calendar

object ScheduleManager {

    /** Intent action for the alarm that fires the day after a seasonal schedule's range ends. */
    const val ACTION_SEASON_END = "com.chrisalvis.rotato.SCHEDULE_SEASON_END"

    fun scheduleAll(context: Context, entries: List<ScheduleEntry>) {
        entries.forEach { cancel(context, it.id) }
        entries.filter { it.enabled }.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, entry: ScheduleEntry) {
        nextTriggerTime(entry)?.let { setAlarm(context, pendingIntent(context, entry.id), it) }
        val endPi = pendingIntent(context, entry.id, seasonEnd = true)
        val endMs = if (entry.isSeasonal && entry.revertAtEnd) nextSeasonEndTime(entry) else null
        if (endMs != null) setAlarm(context, endPi, endMs) else alarmManager(context).cancel(endPi)
    }

    /** Re-arm only if the entry is still enabled; cancel otherwise. */
    fun scheduleIfEnabled(context: Context, entry: ScheduleEntry) {
        if (entry.enabled) schedule(context, entry) else cancel(context, entry.id)
    }

    fun scheduleAt(context: Context, entryId: String, triggerMs: Long) {
        setAlarm(context, pendingIntent(context, entryId), triggerMs)
    }

    private fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun setAlarm(context: Context, pi: PendingIntent, triggerMs: Long) {
        val am = alarmManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancel(context: Context, entryId: String) {
        val am = alarmManager(context)
        am.cancel(pendingIntent(context, entryId))
        am.cancel(pendingIntent(context, entryId, seasonEnd = true))
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
        // A seasonal range can be most of a year away.
        repeat(if (entry.isSeasonal) 375 else 8) {
            if (cal.get(Calendar.DAY_OF_WEEK) in entry.days && entry.inDateRange(cal) && cal.after(now)) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /**
     * The most recent time (ms since epoch, at or before now) this entry would have fired, or
     * null if it hasn't in the past week (or, for a seasonal entry, since its range began).
     */
    fun previousTriggerTime(entry: ScheduleEntry, now: Calendar = Calendar.getInstance()): Long? {
        if (entry.days.isEmpty()) return null
        val cal = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, entry.startHour)
            set(Calendar.MINUTE, entry.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (cal.get(Calendar.DAY_OF_WEEK) in entry.days && !cal.after(now)) {
                // A seasonal entry only counts while its range is still on.
                return if (entry.inDateRange(cal) && entry.inDateRange(now)) cal.timeInMillis else null
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return null
    }

    /** Midnight at the start of the day after this seasonal entry's range next ends. */
    fun nextSeasonEndTime(entry: ScheduleEntry): Long? {
        val range = entry.dateRange ?: return null
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, range.endMonth - 1)
            set(Calendar.DAY_OF_MONTH, range.endDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            add(Calendar.DAY_OF_YEAR, 1)
        }
        if (!cal.after(now)) cal.add(Calendar.YEAR, 1)
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context, entryId: String, seasonEnd: Boolean = false): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_ENTRY_ID, entryId)
            // A distinct action keeps the season-end alarm separate from the start alarm.
            if (seasonEnd) action = ACTION_SEASON_END
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
