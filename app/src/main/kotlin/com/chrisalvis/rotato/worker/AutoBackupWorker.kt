package com.chrisalvis.rotato.worker

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chrisalvis.rotato.data.buildBackupJson
import java.util.concurrent.TimeUnit

/**
 * Rewrites the backup file the user picked (on Google Drive or anywhere the system file picker
 * reaches) once a day, so a reinstall or new phone can restore everything with Import.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = backupUri(applicationContext) ?: return Result.success()
        return try {
            val json = buildBackupJson(applicationContext)
            applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return Result.retry()
            store(applicationContext).edit {
                putLong(KEY_LAST_MS, System.currentTimeMillis())
                remove(KEY_ERROR)
            }
            Result.success()
        } catch (e: SecurityException) {
            // Access to the file was revoked (or it was deleted): stop and tell the user in Settings.
            store(applicationContext).edit { putString(KEY_ERROR, "Lost access to the backup file. Choose it again.") }
            Result.failure()
        } catch (e: Exception) {
            store(applicationContext).edit { putString(KEY_ERROR, e.message ?: "Backup failed") }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "rotato_auto_backup"
        private const val NOW_NAME = "rotato_auto_backup_now"
        private const val PREFS = "rotato_auto_backup"
        private const val KEY_URI = "uri"
        const val KEY_LAST_MS = "last_ms"
        const val KEY_ERROR = "error"

        fun store(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun backupUri(context: Context): Uri? = store(context).getString(KEY_URI, null)?.let(Uri::parse)

        /** Remembers [uri] (keeping access across reboots), backs up now and then daily. */
        fun enable(context: Context, uri: Uri) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            store(context).edit { putString(KEY_URI, uri.toString()); remove(KEY_ERROR) }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            )
            runNow(context)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            )
        }

        fun disable(context: Context) {
            backupUri(context)?.let { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            store(context).edit { clear() }
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
