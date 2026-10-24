package com.chrisalvis.rotato.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Offline pack: on Wi-Fi, downloads the wallpapers that schedules will need ahead of time, so a
 * scheduled change still works on a plane or with no signal (the rotation pool is already on the
 * phone). Files live in their own folder so they never join the everyday rotation.
 */
object OfflinePack {
    private const val PREFS = "offline_pack"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_RUN = "last_run"
    private const val KEY_LAST_COUNT = "last_count"
    private const val WORK = "offline_pack"
    private const val WORK_NOW = "offline_pack_now"

    /** Most files the pack keeps, so a huge collection doesn't fill the phone. */
    const val MAX_FILES = 400

    fun dir(context: Context): File = File(context.filesDir, "offline_pack").also { it.mkdirs() }

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)
    fun lastRun(context: Context) = sp(context).getLong(KEY_LAST_RUN, 0L)
    fun lastCount(context: Context) = sp(context).getInt(KEY_LAST_COUNT, 0)

    fun setEnabled(context: Context, on: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, on).apply()
        val wm = WorkManager.getInstance(context)
        if (on) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<OfflinePackWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints())
                    .build(),
            )
            refreshNow(context)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OfflinePackWorker>().setConstraints(constraints()).build(),
        )
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /** The pack's copy of [entry], if it has one. */
    fun fileFor(context: Context, entry: LocalWallpaperEntry): File? =
        dir(context).listFiles()?.toList().orEmpty().findPoolFile(entry.source, entry.sourceId)
            ?.takeIf { it.length() > 0 }

    /** Downloads what enabled schedules need and drops what they no longer use. Returns the file count. */
    internal suspend fun sync(context: Context): Int {
        val dir = dir(context)
        val schedules = SchedulePreferences(context).entries.first().filter { it.enabled && it.listIds.isNotEmpty() }
        val listIds = schedules.flatMapTo(HashSet()) { it.listIds }
        val entries = LocalListsPreferences(context).allWallpapers.first()
            .filter { it.listId in listIds && !it.isVideo && it.fullUrl.startsWith("http") }
            .distinctBy { it.source to it.sourceId }
            .take(MAX_FILES)
        val keep = entries.flatMapTo(HashSet()) { poolKeys(it.source, it.sourceId) }
        dir.listFiles()?.forEach { f -> if (f.nameWithoutExtension !in keep) f.delete() }
        val repo = FeedRepository(dir)
        val prefs = RotatoPreferences(context)
        entries.forEach { e ->
            if (fileFor(context, e) != null) return@forEach
            val name = runCatching {
                repo.downloadWallpaper(e.sourceId, e.fullUrl, e.sampleUrl.ifBlank { e.thumbUrl }, source = e.source)
            }.getOrNull()
            if (name != null && e.isNsfw) prefs.setFileNsfw(name, true)
        }
        val count = dir.listFiles()?.count { it.isFile && !it.name.endsWith(".part") } ?: 0
        sp(context).edit().putLong(KEY_LAST_RUN, System.currentTimeMillis()).putInt(KEY_LAST_COUNT, count).apply()
        return count
    }
}

class OfflinePackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!OfflinePack.enabled(applicationContext)) return Result.success()
        return runCatching { OfflinePack.sync(applicationContext) }.fold({ Result.success() }, { Result.retry() })
    }
}
