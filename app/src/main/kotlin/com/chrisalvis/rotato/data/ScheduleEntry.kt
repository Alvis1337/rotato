package com.chrisalvis.rotato.data

import java.util.UUID

data class ScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Day-of-week values using [java.util.Calendar] constants: SUNDAY=1 … SATURDAY=7. */
    val days: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val listId: String,
    val enabled: Boolean = true,
    /** Non-zero when the last fire was blocked by a locked collection (epoch ms). Cleared on success. */
    val lastLockedMs: Long = 0L,
    /** Epoch ms of the last time the alarm receiver ran for this entry (0 = never). */
    val lastFiredMs: Long = 0L,
    /** Human-readable result of the last receiver run, e.g. "applied", "locked", "empty pool". */
    val lastFiredResult: String = "",
)
