package com.chrisalvis.rotato.worker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RotatoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            CoroutineScope(Dispatchers.IO).launch {
                val prefs = RotatoPreferences(context)
                val settings = prefs.settings.first()
                val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(workDataOf(WallpaperWorker.KEY_INTERVAL_MINUTES to settings.intervalMinutes))
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(WallpaperWorker.CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        } else if (intent.action == ACTION_REFRESH_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RotatoWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }
    }

    companion object {
        const val ACTION_NEXT = "com.chrisalvis.rotato.WIDGET_NEXT"
        const val ACTION_REFRESH_WIDGET = "com.chrisalvis.rotato.WIDGET_REFRESH"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_rotato)

            // Show current wallpaper drawable from WallpaperManager
            try {
                val wm = android.app.WallpaperManager.getInstance(context)
                val drawable = wm.drawable
                if (drawable != null) {
                    val bitmap = if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                    views.setImageViewBitmap(R.id.widget_image, bitmap)
                }
            } catch (_: Exception) { /* no READ_EXTERNAL_STORAGE in some versions */ }

            // Next button
            val nextIntent = Intent(context, RotatoWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 0, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshAll(context: Context) {
            context.sendBroadcast(Intent(context, RotatoWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            })
        }
    }
}
