package com.chrisalvis.rotato.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.DailyPick
import com.chrisalvis.rotato.data.LearnedTaste
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.applyWallpaperFromUrl
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Daily pick: finds the day's "For you" image, posts it as a notification (when that's on) and
 * refreshes the Daily pick widget. The same worker carries out the Set, Save and Another taps
 * from the notification and widget, since setting a wallpaper can outlast a broadcast.
 */
class DailyPickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return when (inputData.getString(KEY_ACTION) ?: ACTION_PICK) {
            ACTION_SET -> { applyCurrent(ctx); Result.success() }
            ACTION_SAVE -> { saveCurrent(ctx); Result.success() }
            ACTION_ANOTHER -> {
                DailyPick.current(ctx)?.let { LearnedTaste(ctx).record(it.tags, LearnedTaste.Signal.SKIPPED) }
                // From the widget with the notification off, just refresh the widget.
                pick(ctx, notify = DailyPick.isEnabled(ctx) || notificationShowing(ctx))
            }
            else -> {
                val manual = inputData.getBoolean(KEY_MANUAL, false)
                // Nothing to do once both the notification and every widget are gone.
                if (!manual && !DailyPick.isEnabled(ctx) && !hasWidgets(ctx)) return Result.success()
                pick(ctx, notify = manual || DailyPick.isEnabled(ctx))
            }
        }
    }

    private suspend fun pick(ctx: Context, notify: Boolean): Result {
        val result = DailyPick.refresh(ctx)
        if (result == null) {
            DailyPickWidgetProvider.refreshAll(ctx)
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        DailyPickWidgetProvider.refreshAll(ctx)
        if (notify) postPick(ctx, result.first, result.second, status = null)
        return Result.success()
    }

    private suspend fun applyCurrent(ctx: Context) {
        val wp = DailyPick.current(ctx) ?: return
        val err = applyWallpaperFromUrl(ctx, wp.fullUrl.ifBlank { wp.sampleUrl }, false, DailyPick.historyItem(wp))
        if (err == null) {
            DailyPick.markApplied(ctx)
            LearnedTaste(ctx).record(wp.tags, LearnedTaste.Signal.SET_WALLPAPER)
            RotatoWidgetProvider.refreshAll(ctx)
        }
        DailyPickWidgetProvider.refreshAll(ctx)
        if (err != null) toast(ctx, err)
        updateNotification(ctx, wp, err ?: "Set as your wallpaper")
    }

    private suspend fun saveCurrent(ctx: Context) {
        val wp = DailyPick.current(ctx) ?: return
        val added = FavoriteWallpaperReceiver.saveWallpaperToFavorites(LocalListsPreferences(ctx), wp)
        DailyPick.markSaved(ctx)
        if (added) LearnedTaste(ctx).record(wp.tags, LearnedTaste.Signal.SAVED)
        DailyPickWidgetProvider.refreshAll(ctx)
        updateNotification(ctx, wp, if (added) "Saved to Favorites" else "Already in Favorites")
    }

    /** Re-posts the pick's notification with [status], if it's still showing. */
    private fun updateNotification(ctx: Context, wp: BrainrotWallpaper, status: String) {
        if (!notificationShowing(ctx)) return
        postPick(ctx, wp, DailyPick.loadPreview(ctx), status)
    }

    private fun notificationShowing(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NOTIF_ID }
    }.getOrDefault(false)

    private fun toast(ctx: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_MANUAL = "manual"
        const val ACTION_PICK = "pick"
        const val ACTION_SET = "set"
        const val ACTION_SAVE = "save"
        const val ACTION_ANOTHER = "another"

        private const val PERIODIC_NAME = "rotato_daily_pick"
        private const val NOW_NAME = "rotato_daily_pick_now"
        private const val ACTION_NAME = "rotato_daily_pick_action"
        const val NOTIF_ID = 1010

        private fun hasWidgets(context: Context): Boolean = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DailyPickWidgetProvider::class.java)).isNotEmpty()
        }.getOrDefault(false)

        private suspend fun constraints(context: Context): Constraints {
            val wifiOnly = runCatching { RotatoPreferences(context).wifiOnlyDiscover.first() }.getOrDefault(false)
            return Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
        }

        /**
         * Keeps the daily run scheduled while the notification is on or a widget is placed, and
         * cancels it otherwise. [reschedule] moves an existing run to the chosen hour.
         */
        suspend fun sync(context: Context, reschedule: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (!DailyPick.isEnabled(context) && !hasWidgets(context)) {
                wm.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(DailyPick.hour(context), 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val request = PeriodicWorkRequestBuilder<DailyPickWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setConstraints(constraints(context))
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Finds a pick right away and shows it (Settings' "Show one now", a new widget). */
        suspend fun runNow(context: Context, notify: Boolean = true) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DailyPickWorker>()
                    .setInputData(workDataOf(KEY_MANUAL to notify))
                    .setConstraints(constraints(context))
                    .build()
            )
        }

        fun runAction(context: Context, action: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$ACTION_NAME:$action", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DailyPickWorker>()
                    .setInputData(workDataOf(KEY_ACTION to action))
                    .build()
            )
        }

        /** Broadcast that runs [action]; used by the notification's and the widget's buttons. */
        fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, DailyPickReceiver::class.java).setAction(DailyPickReceiver.PREFIX + action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** Tapping the pick opens its post page, where there is one, else Rotato. */
        fun openIntent(context: Context, wp: BrainrotWallpaper?, requestCode: Int): PendingIntent {
            val page = wp?.pageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val intent = if (page != null) Intent(Intent.ACTION_VIEW, Uri.parse(page))
                else Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context, requestCode, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun postPick(context: Context, wp: BrainrotWallpaper, bitmap: Bitmap?, status: String?) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            val sourceName = wp.source.replaceFirstChar { it.uppercase() }
            val builder = NotificationCompat.Builder(context, RotatoApp.CHANNEL_DAILY_PICK)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Today's pick for you")
                .setContentText(status ?: "From $sourceName · tap to see the post")
                .setContentIntent(openIntent(context, wp, 20))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as Bitmap?))
            }
            if (!DailyPick.isApplied(context)) builder.addAction(0, "Set", actionIntent(context, ACTION_SET, 21))
            if (!DailyPick.isSaved(context)) builder.addAction(0, "⭐ Save", actionIntent(context, ACTION_SAVE, 22))
            builder.addAction(0, "Another", actionIntent(context, ACTION_ANOTHER, 23))
            nm.notify(NOTIF_ID, builder.build())
        }
    }
}

/** Turns a Daily pick button tap into a [DailyPickWorker] run. */
class DailyPickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(PREFIX) ?: return
        if (action !in setOf(DailyPickWorker.ACTION_SET, DailyPickWorker.ACTION_SAVE, DailyPickWorker.ACTION_ANOTHER)) return
        DailyPickWorker.runAction(context, action)
    }

    companion object {
        const val PREFIX = "com.chrisalvis.rotato.DAILY_PICK_"
    }
}
