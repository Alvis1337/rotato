package com.chrisalvis.rotato.worker

import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.sanitizeFilename
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val widgetRequestCounter = AtomicInteger(0)
        private val latestRequestByWidget = ConcurrentHashMap<Int, Int>()

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val requestId = widgetRequestCounter.incrementAndGet()
            latestRequestByWidget[appWidgetId] = requestId

            val fallbackViews = buildViews(context, loadCurrentWallpaperBitmap(context))
            appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)

            widgetScope.launch {
                val bitmap = loadCollectionBitmap(context) ?: return@launch
                if (latestRequestByWidget[appWidgetId] != requestId) return@launch
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, bitmap))
            }
        }

        fun refreshAll(context: Context) {
            context.sendBroadcast(Intent(context, RotatoWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            })
        }

        private fun buildViews(context: Context, bitmap: Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_rotato)
            bitmap?.let { views.setImageViewBitmap(R.id.widget_image, it) }

            val nextIntent = Intent(context, RotatoWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)
            return views
        }

        private suspend fun loadCollectionBitmap(context: Context): Bitmap? {
            val collectionId = RotatoPreferences(context).widgetCollectionId.first()
            if (collectionId.isBlank()) return null

            val entry = LocalListsPreferences(context)
                .wallpapersForList(collectionId)
                .first()
                .randomOrNull()
                ?: return null

            return loadEntryBitmap(context, entry)
        }

        private fun loadEntryBitmap(context: Context, entry: LocalWallpaperEntry): Bitmap? {
            val url = resolveWidgetEntryUrl(context, entry)
            val parsed = Uri.parse(url)
            return when {
                parsed.scheme == "file" -> parsed.path?.let(::decodeSampledFile)
                url.startsWith("/") -> decodeSampledFile(url)
                url.isBlank() -> null
                else -> downloadBitmap(url)
            }
        }

        private fun resolveWidgetEntryUrl(context: Context, entry: LocalWallpaperEntry): String {
            val preferredUrl = entry.thumbUrl.ifBlank { entry.fullUrl }
            if (preferredUrl.startsWith("list_images/") || preferredUrl.startsWith("rotato_images/")) {
                return File(context.filesDir, preferredUrl).toURI().toString()
            }
            if (preferredUrl.startsWith("file://")) return preferredUrl

            val localFile = File(context.filesDir, "rotato_images").listFiles()
                ?.firstOrNull { it.nameWithoutExtension == sanitizeFilename(entry.sourceId) }
            if (localFile?.exists() == true) return localFile.toURI().toString()

            return preferredUrl.ifBlank { entry.fullUrl }
        }

        private fun downloadBitmap(url: String): Bitmap? {
            return try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                if (url.contains("cdn.donmai.us") || url.contains("danbooru.donmai.us")) {
                    requestBuilder.header("Referer", "https://danbooru.donmai.us/")
                }
                if (url.contains("gelbooru.com") || url.contains("img2.gelbooru.com")) {
                    requestBuilder.header("Referer", "https://gelbooru.com/")
                }
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val bytes = response.body?.bytes() ?: return null
                    decodeSampledBytes(bytes)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun decodeSampledFile(path: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds)
            })
        }

        private fun decodeSampledBytes(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds)
            })
        }

        private fun calculateInSampleSize(bounds: BitmapFactory.Options, maxDimension: Int = 1024): Int {
            var sampleSize = 1
            var width = bounds.outWidth.coerceAtLeast(1)
            var height = bounds.outHeight.coerceAtLeast(1)
            while (width > maxDimension || height > maxDimension) {
                sampleSize *= 2
                width /= 2
                height /= 2
            }
            return sampleSize
        }

        private fun loadCurrentWallpaperBitmap(context: Context): Bitmap? {
            return try {
                val drawable = WallpaperManager.getInstance(context).drawable ?: return null
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
