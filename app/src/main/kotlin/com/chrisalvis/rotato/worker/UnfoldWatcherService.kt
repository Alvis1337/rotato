package com.chrisalvis.rotato.worker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.currentDisplaySize
import com.chrisalvis.rotato.data.isFoldable
import com.chrisalvis.rotato.data.recordDisplaySize

/**
 * Opt-in "new wallpaper on unfold". Android sends apps no broadcast for fold changes, so this
 * keeps a minimal foreground service listening for the default display switching from the
 * narrow outer panel to the wide inner one, then runs one rotation.
 */
class UnfoldWatcherService : Service() {

    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private var wasUnfolded = false
    private var lastTriggerMs = 0L

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onDisplayMaybeFolded()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        wasUnfolded = isUnfolded()
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isUnfolded(): Boolean {
        val size = currentDisplaySize(this)
        return size.width.toFloat() / size.height >= UNFOLDED_MIN_ASPECT
    }

    private fun onDisplayMaybeFolded() {
        recordDisplaySize(this)
        val unfolded = isUnfolded()
        val justUnfolded = unfolded && !wasUnfolded
        wasUnfolded = unfolded
        if (!justUnfolded) return
        // Hinge bounces and rotations while opening can fire several changes in a row.
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerMs < DEBOUNCE_MS) return
        lastTriggerMs = now
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RotatoApp.CHANNEL_UNFOLD)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New wallpaper on unfold")
            .setContentText("Rotato changes your wallpaper when you open the phone")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val WORK_NAME = "unfold_rotate"
        private const val DEBOUNCE_MS = 3_000L
        private const val UNFOLDED_MIN_ASPECT = 0.7f

        /** Starts or stops the watcher to match [enabled]. Only runs on foldables. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, UnfoldWatcherService::class.java)
            if (!enabled || !isFoldable(context)) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (e: RuntimeException) {
                // Android 12+ refuses to start foreground services while the app is in the
                // background (e.g. the process woke for a rotation); the next app open,
                // boot or toggle starts it instead.
                Log.w("UnfoldWatcher", "Could not start unfold watcher", e)
            }
        }
    }
}
