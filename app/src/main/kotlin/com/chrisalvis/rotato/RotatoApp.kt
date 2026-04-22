package com.chrisalvis.rotato

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RotatoApp : Application(), ImageLoaderFactory {

    private val crashClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashReporting()
        createNotificationChannels()
    }

    private fun setupCrashReporting() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                val body = "Thread: ${thread.name}\n\n$trace"
                    .toRequestBody("text/plain".toMediaType())
                val req = Request.Builder()
                    .url("https://ntfy.sh/$CRASH_TOPIC")
                    .post(body)
                    .addHeader("Title", "Rotato Crash")
                    .addHeader("Priority", "high")
                    .addHeader("Tags", "rotating_light")
                    .build()
                val t = Thread { runCatching { crashClient.newCall(req).execute().close() } }
                t.start()
                t.join(6_000)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                // Gelbooru's image CDN requires a Referer header or it redirects to hotlink.php
                val newReq = if (host.endsWith("gelbooru.com")) {
                    req.newBuilder().header("Referer", "https://gelbooru.com/").build()
                } else req
                chain.proceed(newReq)
            })
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .build()
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
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCKED_LIST,
                "Locked Collections",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alert when a scheduled collection is locked" }
        )
    }

    companion object {
        const val CHANNEL_WALLPAPER_SET = "rotato_wallpaper_set"
        const val CHANNEL_LOW_QUEUE = "rotato_low_queue"
        const val CHANNEL_WORKER = "rotato_worker"
        const val CHANNEL_LOCKED_LIST = "rotato_locked_list"
        const val CRASH_TOPIC = "rotato-crash-x7p2q9k4"
    }
}

