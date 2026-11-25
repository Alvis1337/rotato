package com.chrisalvis.rotato.worker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.data.DailyPick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget for the daily pick: today's image with Set, Save and Another. Placing one
 * schedules the daily run on its own, so the widget works without the notification.
 */
class DailyPickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        val pending = goAsync()
        scope.launch {
            try {
                DailyPickWorker.sync(context)
                // First widget and nothing picked yet: find one now rather than wait for tomorrow.
                if (DailyPick.current(context) == null) DailyPickWorker.runNow(context, notify = false)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        val pending = goAsync()
        scope.launch {
            try { DailyPickWorker.sync(context) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DailyPickWidgetProvider::class.java))
                .forEach { update(context, manager, it) }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.chrisalvis.rotato.DAILY_PICK_WIDGET_REFRESH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshAll(context: Context) {
            context.sendBroadcast(Intent(context, DailyPickWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            // Controls first; the picture is decoded off the main thread.
            manager.updateAppWidget(id, buildViews(context, null))
            scope.launch {
                val bitmap = runCatching { DailyPick.loadPreview(context) }.getOrNull()
                manager.updateAppWidget(id, buildViews(context, bitmap))
            }
        }

        private fun buildViews(context: Context, bitmap: Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_daily_pick)
            val pick = DailyPick.current(context)
            if (bitmap != null) views.setImageViewBitmap(R.id.daily_image, bitmap)
            val emptyText = when {
                pick != null && bitmap != null -> null
                pick != null -> "Loading today's pick…"
                DailyPick.lastError(context) != null -> "Nothing new from your sources yet. Tap Another to try again."
                else -> "Finding today's pick…"
            }
            views.setViewVisibility(R.id.daily_empty, if (emptyText == null) View.GONE else View.VISIBLE)
            emptyText?.let { views.setTextViewText(R.id.daily_empty, it) }
            val caption = when {
                pick == null -> "Today's pick"
                DailyPick.isApplied(context) -> "Today's pick · set"
                DailyPick.isSaved(context) -> "Today's pick · saved"
                else -> "Today's pick · " + pick.source.replaceFirstChar { it.uppercase() }
            }
            views.setTextViewText(R.id.daily_caption, caption)
            views.setOnClickPendingIntent(R.id.daily_btn_set, DailyPickWorker.actionIntent(context, DailyPickWorker.ACTION_SET, 31))
            views.setOnClickPendingIntent(R.id.daily_btn_save, DailyPickWorker.actionIntent(context, DailyPickWorker.ACTION_SAVE, 32))
            views.setOnClickPendingIntent(R.id.daily_btn_another, DailyPickWorker.actionIntent(context, DailyPickWorker.ACTION_ANOTHER, 33))
            views.setOnClickPendingIntent(R.id.daily_image, DailyPickWorker.openIntent(context, pick, 34))
            return views
        }
    }
}
