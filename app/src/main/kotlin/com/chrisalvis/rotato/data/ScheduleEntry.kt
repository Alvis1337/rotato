package com.chrisalvis.rotato.data

import java.util.Calendar
import java.util.UUID

/**
 * A yearly date range, such as Dec 1 – Dec 26. Months are 1–12. A range may wrap the new year
 * (Dec 20 – Jan 5). The year is never stored, so it repeats every year.
 */
data class DateRange(
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
) {
    private val start get() = startMonth * 100 + startDay
    private val end get() = endMonth * 100 + endDay

    /** True when the date of [cal] falls inside the range. */
    fun contains(cal: Calendar): Boolean {
        val md = (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        return if (start <= end) md in start..end else md >= start || md <= end
    }

    val label: String get() = "${monthName(startMonth)} $startDay – ${monthName(endMonth)} $endDay"

    companion object {
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        fun monthName(month: Int) = MONTHS.getOrElse(month - 1) { "?" }
    }
}

data class ScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Day-of-week values using [java.util.Calendar] constants: SUNDAY=1 … SATURDAY=7. */
    val days: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val listIds: Set<String> = emptySet(),
    val enabled: Boolean = true,
    /** Non-zero when the last fire was blocked by a locked collection (epoch ms). Cleared on success. */
    val lastLockedMs: Long = 0L,
    /** Epoch ms of the last time the alarm receiver ran for this entry (0 = never). */
    val lastFiredMs: Long = 0L,
    /** Human-readable result of the last receiver run, e.g. "applied", "locked", "empty pool". */
    val lastFiredResult: String = "",
    /**
     * Seasonal schedule: only runs on dates inside this yearly range (null = all year). While a
     * seasonal schedule's range is on, it takes priority over weekly schedules.
     */
    val dateRange: DateRange? = null,
    /** Seasonal only: the day after the range ends, go back to what the weekly schedules call for. */
    val revertAtEnd: Boolean = true,
) {
    val usesMainQueue: Boolean get() = listIds.isEmpty()

    val isSeasonal: Boolean get() = dateRange != null

    /** True when [cal]'s date is inside this entry's date range, or it has none. */
    fun inDateRange(cal: Calendar = Calendar.getInstance()): Boolean = dateRange?.contains(cal) ?: true

    /** True when this enabled seasonal entry's range includes today. */
    fun seasonActive(cal: Calendar = Calendar.getInstance()): Boolean = enabled && dateRange?.contains(cal) == true
}
