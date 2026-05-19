package com.chrisalvis.rotato.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.MainActivity
import com.chrisalvis.rotato.R
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.ImageRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.RotationError
import com.chrisalvis.rotato.data.RotationErrorType
import com.chrisalvis.rotato.data.ScheduleEntry
import com.chrisalvis.rotato.data.SchedulePreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.ScreenRotationTarget
import com.chrisalvis.rotato.data.WallpaperFit
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.loadScaledBitmap
import com.chrisalvis.rotato.data.sanitizeFilename
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ImageRepository(applicationContext)
        val prefs = RotatoPreferences(applicationContext)
        val listPrefs = LocalListsPreferences(applicationContext)
        val schedPrefs = SchedulePreferences(applicationContext)
        val imageDir = File(applicationContext.filesDir, "rotato_images").also { it.mkdirs() }
        val feedRepository = FeedRepository(imageDir)

        val (settings, autoPause, history, allWallpapers, scheduleEntries, lists,
             autoFavoriteEnabled, autoFavoriteMinutes,
             lastWallpaperThumbUrl, lastWallpaperFullUrl, lastWallpaperSource, lastWallpaperSetMs) = coroutineScope {
            val sDeferred  = async { prefs.settings.first() }
            val aDeferred  = async { prefs.autoPauseSettings.first() }
            val hDeferred  = async { prefs.historyJson.first() }
            val wDeferred  = async { listPrefs.allWallpapers.first() }
            val eDeferred  = async { schedPrefs.entries.first() }
            val lDeferred  = async { listPrefs.lists.first() }
            val afEnDeferred  = async { prefs.autoFavoriteEnabled.first() }
            val afMinDeferred = async { prefs.autoFavoriteMinutes.first() }
            val twThumbDeferred  = async { prefs.lastWallpaperThumbUrl.first() }
            val twFullDeferred   = async { prefs.lastWallpaperFullUrl.first() }
            val twSrcDeferred    = async { prefs.lastWallpaperSource.first() }
            val twMsDeferred     = async { prefs.lastWallpaperSetMs.first() }
            Duodecuple(
                sDeferred.await(),
                aDeferred.await(),
                historyFromJson(hDeferred.await()),
                wDeferred.await(),
                eDeferred.await(),
                lDeferred.await(),
                afEnDeferred.await(),
                afMinDeferred.await(),
                twThumbDeferred.await(),
                twFullDeferred.await(),
                twSrcDeferred.await(),
                twMsDeferred.await(),
            )
        }

        // Auto-pause: night window
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (autoPause.isInNightWindow(currentHour)) return Result.success()

        // Auto-pause: charging
        if (autoPause.chargingEnabled) {
            val bm = applicationContext.getSystemService(BatteryManager::class.java)
            if (bm?.isCharging == true) return Result.success()
        }

        // Auto-pause: screen on (skip rotation while device is interactive)
        if (!autoPause.rotateScreenOn) {
            val pm = applicationContext.getSystemService(PowerManager::class.java)
            if (pm?.isInteractive == true) return Result.success()
        }

        val activeScheduledListId = findActiveScheduledListId(scheduleEntries, lists)
        val scheduledEntry = activeScheduledListId?.let { listId ->
            val wallpapers = listPrefs.wallpapersForList(listId).first()
            selectScheduledWallpaper(wallpapers, settings.shuffleMode, settings.currentIndex, prefs)
        }

        val allImages = withContext(Dispatchers.IO) { repository.getImages() }

        // Determine if any rotation collections have per-screen targets.
        val rotationLists = lists.filter { it.useAsRotation }
        val hasPerScreen = rotationLists.any { it.rotationTarget != ScreenRotationTarget.BOTH }

        // Build per-screen file sets when per-screen pools are configured and target is BOTH.
        val homeFiles: List<File>
        val lockFiles: List<File>
        if (hasPerScreen && settings.wallpaperTarget == WallpaperTarget.BOTH && scheduledEntry == null) {
            val homeListIds = rotationLists
                .filter { it.rotationTarget == ScreenRotationTarget.HOME_ONLY || it.rotationTarget == ScreenRotationTarget.BOTH }
                .map { it.id }.toSet()
            val lockListIds = rotationLists
                .filter { it.rotationTarget == ScreenRotationTarget.LOCK_ONLY || it.rotationTarget == ScreenRotationTarget.BOTH }
                .map { it.id }.toSet()
            fun entriesForLists(ids: Set<String>) = allWallpapers
                .filter { it.listId in ids }
                .mapNotNull { entry -> allImages.find { it.nameWithoutExtension == sanitizeFilename(entry.sourceId) } }
            homeFiles = entriesForLists(homeListIds).ifEmpty { allImages }
            lockFiles = entriesForLists(lockListIds).ifEmpty { allImages }
        } else {
            homeFiles = allImages
            lockFiles = allImages
        }

        var mainQueueCount: Int? = null
        val targetFile = resolveScheduledTargetFile(scheduledEntry, feedRepository, imageDir)
            ?: run {
                mainQueueCount = homeFiles.size
                if (homeFiles.isEmpty()) {
                    if (settings.isEnabled) {
                        prefs.addRotationError(RotationError(
                            RotationErrorType.POOL_EMPTY,
                            "Rotation ran but the library pool is empty — add photos or link a collection"
                        ))
                    }
                    return Result.success()
                }
                selectMainQueueFile(homeFiles, settings.shuffleMode, settings.currentIndex, history, prefs)
            }

        // For per-screen mode, pick a separate lock file (different from home when possible).
        val lockTargetFile = if (hasPerScreen && settings.wallpaperTarget == WallpaperTarget.BOTH && scheduledEntry == null) {
            val candidates = lockFiles.filter { it.absolutePath != targetFile.absolutePath }
            candidates.ifEmpty { lockFiles }.randomOrNull() ?: targetFile
        } else targetFile

        return try {
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val metrics = applicationContext.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels

            fun scaleBitmap(bitmap: Bitmap): Bitmap {
                return when (settings.wallpaperFit) {
                    WallpaperFit.STRETCH -> Bitmap.createScaledBitmap(bitmap, screenW, screenH, true)
                    WallpaperFit.FIT -> {
                        val scale = minOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
                        val scaledW = (bitmap.width * scale).roundToInt()
                        val scaledH = (bitmap.height * scale).roundToInt()
                        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                        val result = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(result)
                        canvas.drawBitmap(scaled, ((screenW - scaledW) / 2f), ((screenH - scaledH) / 2f), null)
                        if (scaled != bitmap) scaled.recycle()
                        result
                    }
                    WallpaperFit.FILL -> {
                        val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
                        val scaledW = (bitmap.width * scale).roundToInt()
                        val scaledH = (bitmap.height * scale).roundToInt()
                        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                        val srcX = ((scaledW - screenW) / 2).coerceAtLeast(0)
                        val srcY = ((scaledH - screenH) / 2).coerceAtLeast(0)
                        val cropped = Bitmap.createBitmap(scaled, srcX, srcY, screenW, screenH)
                        if (scaled != bitmap) scaled.recycle()
                        cropped
                    }
                }
            }

            val homeBitmap = loadScaledBitmap(applicationContext, targetFile.absolutePath)
                ?: run {
                    val errorType = if (targetFile.exists()) RotationErrorType.IMAGE_CORRUPT else RotationErrorType.IMAGE_MISSING
                    prefs.addRotationError(RotationError(errorType, "Could not load: ${targetFile.name}"))
                    return Result.failure()
                }

            val screenBitmap = scaleBitmap(homeBitmap)
            homeBitmap.recycle()

            try {
                when (settings.wallpaperTarget) {
                    WallpaperTarget.HOME_ONLY -> wallpaperManager.setBitmap(screenBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    WallpaperTarget.LOCK_ONLY -> wallpaperManager.setBitmap(screenBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    WallpaperTarget.BOTH -> {
                        if (hasPerScreen && lockTargetFile != targetFile) {
                            wallpaperManager.setBitmap(screenBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            val lockBitmap = loadScaledBitmap(applicationContext, lockTargetFile.absolutePath)
                            if (lockBitmap != null) {
                                val lockScreenBitmap = scaleBitmap(lockBitmap)
                                lockBitmap.recycle()
                                try { wallpaperManager.setBitmap(lockScreenBitmap, null, true, WallpaperManager.FLAG_LOCK) }
                                finally { lockScreenBitmap.recycle() }
                            }
                        } else {
                            wallpaperManager.setBitmap(screenBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        }
                    }
                }

                val now = System.currentTimeMillis()
                prefs.recordRotationAndIncrement()

                val matchingEntry = scheduledEntry
                    ?: allWallpapers.find { sanitizeFilename(it.sourceId) == targetFile.nameWithoutExtension }

                maybeAutoFavoritePreviousWallpaper(
                    listPrefs = listPrefs,
                    autoFavoriteEnabled = autoFavoriteEnabled,
                    autoFavoriteMinutes = autoFavoriteMinutes,
                    lastWallpaperThumbUrl = lastWallpaperThumbUrl,
                    lastWallpaperFullUrl = lastWallpaperFullUrl,
                    lastWallpaperSource = lastWallpaperSource,
                    lastWallpaperSetMs = lastWallpaperSetMs,
                    now = now,
                )

                val currentThumbUrl = matchingEntry?.thumbUrl ?: targetFile.absolutePath
                val currentFullUrl = matchingEntry?.fullUrl ?: targetFile.absolutePath
                val currentSource = matchingEntry?.source ?: "local"
                val historyItem = WallpaperHistoryItem(
                    thumbUrl = targetFile.absolutePath,
                    sampleUrl = matchingEntry?.sampleUrl ?: "",
                    fullUrl = currentFullUrl,
                    source = currentSource,
                    timestamp = now,
                    tags = matchingEntry?.tags ?: emptyList(),
                    pageUrl = matchingEntry?.pageUrl ?: ""
                )
                val updatedHistory = history.toMutableList().apply { add(0, historyItem) }
                prefs.setHistoryJson(updatedHistory.take(50).toJson())
                prefs.setLastWallpaperState(
                    thumbUrl = currentThumbUrl,
                    fullUrl = currentFullUrl,
                    source = currentSource,
                    setMs = now,
                )

                postWallpaperSetNotification(screenBitmap)
            } finally {
                screenBitmap.recycle()
            }

            RotatoWidgetProvider.refreshAll(applicationContext)

            mainQueueCount?.takeIf { it in 1..4 }?.let { postLowQueueNotification(it) }

            val intervalMinutes = inputData.getLong(KEY_INTERVAL_MINUTES, 0L)
            if (intervalMinutes in 1..14 && settings.isEnabled) {
                scheduleNextRun(intervalMinutes)
            }

            Result.success()
        } catch (e: Exception) {
            prefs.addRotationError(RotationError(
                RotationErrorType.SET_FAILED,
                "Failed to set wallpaper: ${e.localizedMessage ?: e.javaClass.simpleName}"
            ))
            Result.retry()
        }
    }

    private fun findActiveScheduledListId(entries: List<ScheduleEntry>, lists: List<LocalList>): String? {
        val scheduledListIds = entries.asSequence()
            .filter { it.enabled }
            .map { it.listId }
            .filter { it.isNotBlank() }
            .toSet()
        if (scheduledListIds.isEmpty()) return null
        return lists.firstOrNull { it.useAsRotation && it.id in scheduledListIds }?.id
    }

    private suspend fun selectScheduledWallpaper(
        wallpapers: List<LocalWallpaperEntry>,
        shuffleMode: Boolean,
        currentIndex: Int,
        prefs: RotatoPreferences,
    ): LocalWallpaperEntry? {
        if (wallpapers.isEmpty()) return null
        return if (shuffleMode) {
            wallpapers.random()
        } else {
            val nextIndex = currentIndex % wallpapers.size
            prefs.setCurrentIndex((nextIndex + 1) % wallpapers.size)
            wallpapers[nextIndex]
        }
    }

    private suspend fun resolveScheduledTargetFile(
        entry: LocalWallpaperEntry?,
        feedRepository: FeedRepository,
        imageDir: File,
    ): File? {
        if (entry == null) return null
        return withContext(Dispatchers.IO) {
            when {
                entry.source == "device" && entry.fullUrl.startsWith("list_images/") -> {
                    File(applicationContext.filesDir, entry.fullUrl).takeIf { it.exists() }
                }
                entry.fullUrl.isBlank() -> null
                !feedRepository.downloadWallpaper(entry.sourceId, entry.fullUrl, entry.sampleUrl.ifBlank { entry.thumbUrl }) -> null
                else -> imageDir.listFiles()?.firstOrNull {
                    it.isFile && it.nameWithoutExtension == sanitizeFilename(entry.sourceId)
                }
            }
        }
    }

    private suspend fun selectMainQueueFile(
        images: List<File>,
        shuffleMode: Boolean,
        currentIndex: Int,
        history: List<WallpaperHistoryItem>,
        prefs: RotatoPreferences,
    ): File {
        return if (shuffleMode) {
            val recentPaths = history
                .take((images.size - 1).coerceAtLeast(0).coerceAtMost(10))
                .map { it.thumbUrl }
                .toSet()
            val fresh = images.filter { it.absolutePath !in recentPaths }
            fresh.ifEmpty { images }.random()
        } else {
            val nextIndex = currentIndex % images.size
            prefs.setCurrentIndex((nextIndex + 1) % images.size)
            images[nextIndex]
        }
    }

    private suspend fun maybeAutoFavoritePreviousWallpaper(
        listPrefs: LocalListsPreferences,
        autoFavoriteEnabled: Boolean,
        autoFavoriteMinutes: Int,
        lastWallpaperThumbUrl: String,
        lastWallpaperFullUrl: String,
        lastWallpaperSource: String,
        lastWallpaperSetMs: Long,
        now: Long,
    ) {
        if (!autoFavoriteEnabled || lastWallpaperSetMs <= 0L) return
        val keepThresholdMs = TimeUnit.MINUTES.toMillis(autoFavoriteMinutes.toLong().coerceAtLeast(1L))
        if (now - lastWallpaperSetMs < keepThresholdMs) return

        val wallpaper = FavoriteWallpaperReceiver.wallpaperFromUrls(
            thumbUrl = lastWallpaperThumbUrl,
            fullUrl = lastWallpaperFullUrl,
            source = lastWallpaperSource,
        ) ?: return
        FavoriteWallpaperReceiver.saveWallpaperToFavorites(listPrefs, wallpaper)
    }

    private fun postWallpaperSetNotification(bitmap: Bitmap) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipIntent = PendingIntent.getBroadcast(
            applicationContext, 1,
            Intent(applicationContext, SkipWallpaperReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val favoriteIntent = PendingIntent.getBroadcast(
            applicationContext, 2,
            Intent(applicationContext, FavoriteWallpaperReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dislikeIntent = PendingIntent.getBroadcast(
            applicationContext, 4,
            Intent(applicationContext, DislikeWallpaperReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val thumb = centerCropBitmap(bitmap, NOTIF_THUMB_W, NOTIF_THUMB_H)
        val bigPicture = centerCropBitmap(bitmap, NOTIF_BIG_W, NOTIF_BIG_H)
        val notif = NotificationCompat.Builder(applicationContext, RotatoApp.CHANNEL_WALLPAPER_SET)
            .setContentTitle("Wallpaper changed")
            .setContentText("Tap to open Rotato")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(thumb)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bigPicture))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .addAction(0, "Skip", skipIntent)
            .addAction(0, "⭐ Favorite", favoriteIntent)
            .addAction(0, "👎 Dislike", dislikeIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(NOTIF_ID_WALLPAPER_SET, notif)
        thumb.recycle()
        bigPicture.recycle()
    }

    private fun postLowQueueNotification(count: Int) {
        if (count == lastLowQueueNotifCount) return
        lastLowQueueNotifCount = count
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            applicationContext, 3,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(applicationContext, RotatoApp.CHANNEL_LOW_QUEUE)
            .setContentTitle("Wallpaper queue is low")
            .setContentText("Only $count photo${if (count == 1) "" else "s"} left — add more to keep rotating")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIF_ID_LOW_QUEUE, notif)
    }

    private fun scheduleNextRun(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val next = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_INTERVAL_MINUTES to intervalMinutes))
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(CHAIN_WORK_NAME, ExistingWorkPolicy.REPLACE, next)
    }

    companion object {
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val CHAIN_WORK_NAME = "rotato_chain"
        private const val NOTIF_ID_WALLPAPER_SET = 1001
        private const val NOTIF_ID_LOW_QUEUE = 1002
        private const val NOTIF_THUMB_W = 128
        private const val NOTIF_THUMB_H = 128
        private const val NOTIF_BIG_W = 1024
        private const val NOTIF_BIG_H = 512

        // Only notify once per distinct count so we don't fire every rotation while queue is low.
        @Volatile private var lastLowQueueNotifCount = Int.MAX_VALUE
    }
}

private data class Duodecuple<A, B, C, D, E, F, G, H, I, J, K, L>(
    val first: A, val second: B, val third: C, val fourth: D,
    val fifth: E, val sixth: F, val seventh: G, val eighth: H,
    val ninth: I, val tenth: J, val eleventh: K, val twelfth: L,
)

private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
    val srcW = src.width
    val srcH = src.height
    val srcRatio = srcW.toFloat() / srcH
    val dstRatio = targetW.toFloat() / targetH
    val cropW: Int
    val cropH: Int
    if (srcRatio > dstRatio) {
        cropH = srcH
        cropW = (srcH * dstRatio).toInt()
    } else {
        cropW = srcW
        cropH = (srcW / dstRatio).toInt()
    }
    val x = (srcW - cropW) / 2
    val y = (srcH - cropH) / 2
    val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
    val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
    if (cropped !== src) cropped.recycle()
    return scaled
}
