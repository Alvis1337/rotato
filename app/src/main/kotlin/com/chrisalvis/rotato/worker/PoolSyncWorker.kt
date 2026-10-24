package com.chrisalvis.rotato.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chrisalvis.rotato.data.LocalListsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Finishes filling the rotation pool after a collection switch whose quick download ran out of
 * time (slow or offline network). Waits for a connection and retries with backoff while images
 * are still missing. It's unique work queued with REPLACE, so a later switch supersedes it.
 */
class PoolSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val listIds = inputData.getStringArray(KEY_LIST_IDS)?.toSet().orEmpty()
        if (listIds.isEmpty()) return Result.success()
        val listPrefs = LocalListsPreferences(applicationContext)
        // The rotation collections changed since this was queued (the user or another switch):
        // syncing the old set now would delete the files of the collections in use.
        val activeThen = inputData.getStringArray(KEY_ACTIVE)?.toSet().orEmpty()
        if (activeRotationIds(listPrefs) != activeThen) return Result.success()

        val before = ScheduleReceiver.countPooled(applicationContext, listIds, listPrefs)
        val images = try {
            ScheduleReceiver.syncRotationPool(applicationContext, listIds, listPrefs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pool sync failed", e)
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        // The switch rotated from an empty pool, so show the new collection once something arrives.
        if (before == 0 && images > 0 && inputData.getBoolean(KEY_ROTATE, false)) {
            ScheduleReceiver.enqueueRotation(applicationContext)
        }
        val expected = listPrefs.allWallpapers.first().count { it.listId in listIds }
        return if (images < expected && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "PoolSyncWorker"
        private const val WORK_NAME = "rotation_pool_sync"
        private const val KEY_LIST_IDS = "list_ids"
        private const val KEY_ROTATE = "rotate_when_done"
        private const val KEY_ACTIVE = "active_at_switch"
        private const val MAX_ATTEMPTS = 5

        /** Finishes syncing [listIds] in the background, replacing any earlier pending sync. */
        suspend fun enqueue(context: Context, listIds: Set<String>, rotateWhenDone: Boolean) {
            if (listIds.isEmpty()) return
            val active = activeRotationIds(LocalListsPreferences(context))
            val request = OneTimeWorkRequestBuilder<PoolSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_LIST_IDS to listIds.toTypedArray(),
                        KEY_ROTATE to rotateWhenDone,
                        KEY_ACTIVE to active.toTypedArray(),
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private suspend fun activeRotationIds(listPrefs: LocalListsPreferences): Set<String> =
            listPrefs.lists.first().filter { it.useAsRotation }.mapTo(HashSet()) { it.id }

        /** Drops a pending sync; a switch that finished its own download supersedes it. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
