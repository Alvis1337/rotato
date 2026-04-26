package com.chrisalvis.rotato.data

enum class WallpaperTarget(val label: String) {
    HOME_ONLY("Home screen only"),
    LOCK_ONLY("Lock screen only"),
    BOTH("Home & lock screen")
}

data class RotatoSettings(
    val isEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val shuffleMode: Boolean = true,
    val currentIndex: Int = 0,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH
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

data class AutoPauseSettings(
    val nightEnabled: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    val chargingEnabled: Boolean = false
) {
    fun isInNightWindow(hour: Int): Boolean {
        if (!nightEnabled || nightStartHour == nightEndHour) return false
        return if (nightStartHour > nightEndHour) {
            hour >= nightStartHour || hour < nightEndHour
        } else {
            hour >= nightStartHour && hour < nightEndHour
        }
    }
}
