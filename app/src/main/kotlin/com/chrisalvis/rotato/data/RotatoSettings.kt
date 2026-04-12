package com.chrisalvis.rotato.data

data class RotatoSettings(
    val isEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val shuffleMode: Boolean = true,
    val currentIndex: Int = 0
)

enum class RotationInterval(val minutes: Int, val label: String) {
    FIFTEEN(15, "15 min"),
    THIRTY(30, "30 min"),
    ONE_HOUR(60, "1 hour"),
    TWO_HOURS(120, "2 hours"),
    FOUR_HOURS(240, "4 hours"),
    EIGHT_HOURS(480, "8 hours"),
    TWELVE_HOURS(720, "12 hours"),
    ONE_DAY(1440, "Daily")
}
