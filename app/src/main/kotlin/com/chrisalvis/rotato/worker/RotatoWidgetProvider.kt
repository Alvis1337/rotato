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
import android.os.Build
import android.util.SizeF
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
import com.chrisalvis.rotato.data.poolKey
import com.chrisalvis.rotato.data.poolKeys
import com.chrisalvis.rotato.data.findPoolFile
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.live.LiveWallpaper
import com.chrisalvis.rotato.MainActivity
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
            // Own work name: replacing CHAIN_WORK_NAME here cancelled the pending interval
            // chain, and its Int interval extra was unreadable by the worker's getLong().
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(workDataOf(WallpaperWorker.KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_next", ExistingWorkPolicy.REPLACE, request)
        } else if (intent.action == ACTION_BACK) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_back",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<PreviousWallpaperWorker>().build()
            )
        } else if (intent.action == ACTION_REFRESH_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RotatoWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }
    }

    companion object {
        const val ACTION_NEXT = "com.chrisalvis.rotato.WIDGET_NEXT"
        const val ACTION_BACK = "com.chrisalvis.rotato.WIDGET_BACK"
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

            // Controls show straight away; the picture and caption follow once loaded off the main thread.
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, null, null))

            widgetScope.launch {
                val caption = currentCaption(context)
                val bitmap = loadCollectionBitmap(context) ?: loadCurrentWallpaperBitmap(context)
                if (latestRequestByWidget[appWidgetId] != requestId) return@launch
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, bitmap, caption))
            }
        }

        fun refreshAll(context: Context) {
            context.sendBroadcast(Intent(context, RotatoWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            })
        }

        /**
         * Glass widget: the wallpaper fills it, with Previous / Save / Next in frosted circles.
         * On Android 12+ a smaller variant without the caption is used at cover-screen sizes.
         */
        private fun buildViews(context: Context, bitmap: Bitmap?, caption: String?): RemoteViews {
            val scaled = bitmap?.let { scaledForWidget(it) }
            val full = layoutViews(context, R.layout.widget_rotato, scaled, caption)
            if (Build.VERSION.SDK_INT < 31) return full
            val small = layoutViews(context, R.layout.widget_rotato_small, scaled, null)
            return RemoteViews(mapOf(SizeF(100f, 80f) to small, SizeF(170f, 160f) to full))
        }

        private fun layoutViews(context: Context, layout: Int, bitmap: Bitmap?, caption: String?): RemoteViews {
            val views = RemoteViews(context.packageName, layout)
            bitmap?.let { views.setImageViewBitmap(R.id.widget_image, it) }
            if (layout == R.layout.widget_rotato) {
                views.setTextViewText(R.id.widget_caption, caption?.takeIf { it.isNotBlank() } ?: "Rotato")
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                PendingIntent.getBroadcast(context, 0, Intent(context, RotatoWidgetProvider::class.java).apply { action = ACTION_NEXT }, flags)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_back,
                PendingIntent.getBroadcast(context, 1, Intent(context, RotatoWidgetProvider::class.java).apply { action = ACTION_BACK }, flags)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_save,
                PendingIntent.getBroadcast(context, 2, Intent(context, FavoriteWallpaperReceiver::class.java), flags)
            )
            views.setOnClickPendingIntent(
                R.id.widget_image,
                PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            )
            return views
        }

        /** Where the wallpaper on screen came from (tags are left off: the widget is on show to anyone). */
        private suspend fun currentCaption(context: Context): String? = try {
            historyFromJson(RotatoPreferences(context).historyJson.first()).firstOrNull()
                ?.source?.takeIf { it.isNotBlank() }
                ?.let { "Now showing · " + it.replaceFirstChar { c -> c.uppercase() } }
        } catch (_: Exception) {
            null
        }

        private suspend fun loadCollectionBitmap(context: Context): Bitmap? {
            val collectionId = RotatoPreferences(context).widgetCollectionId.first()
            if (collectionId.isBlank()) return null

            val listsPrefs = LocalListsPreferences(context)
            // The widget sits on the home screen for anyone to see; never show a locked collection.
            if (listsPrefs.lists.first().firstOrNull { it.id == collectionId }?.isLocked != false) return null
            val entry = listsPrefs
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
                ?.toList()?.findPoolFile(entry.source, entry.sourceId)
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

        /**
         * RemoteViews bitmaps are capped at roughly the screen's size in bytes x 1.5. The current
         * wallpaper is rendered to cover both screens of a foldable, so passed through as-is it
         * exceeds the cap on the smaller outer screen and the widget silently stops updating.
         */
        private fun scaledForWidget(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= maxDimension) return bitmap
            val scale = maxDimension.toFloat() / longest
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
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
            if (LiveWallpaper.isActive(context)) {
                val sp = context.getSharedPreferences(LiveWallpaper.PREFS, Context.MODE_PRIVATE)
                val path = sp.getString(LiveWallpaper.KEY_PATH, null)
                if (path != null && !sp.getBoolean(LiveWallpaper.KEY_VIDEO, false)) return decodeSampledFile(path)
                return null
            }
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
