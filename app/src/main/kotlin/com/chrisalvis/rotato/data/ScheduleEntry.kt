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
)
