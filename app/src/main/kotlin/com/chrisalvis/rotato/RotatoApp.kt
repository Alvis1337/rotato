package com.chrisalvis.rotato

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RotatoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WALLPAPER_SET,
                "Wallpaper Changed",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown when a new wallpaper is set by rotation" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOW_QUEUE,
                "Low Wallpaper Queue",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Shown when the rotation pool is running low" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKER,
                "Wallpaper Worker",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Background worker for wallpaper rotation" }
        )
    }

    companion object {
        const val CHANNEL_WALLPAPER_SET = "rotato_wallpaper_set"
        const val CHANNEL_LOW_QUEUE = "rotato_low_queue"
        const val CHANNEL_WORKER = "rotato_worker"
    }
}

