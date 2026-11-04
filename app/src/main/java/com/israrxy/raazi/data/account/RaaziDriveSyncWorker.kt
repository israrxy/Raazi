package com.israrxy.raazi.data.account

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.israrxy.raazi.RaaziApplication
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that safely performs debounced or periodic
 * backups to the user's private Google Drive appDataFolder.
 */
class RaaziDriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "RaaziDriveSyncWorker"
        private const val UNIQUE_WORK_NAME = "RaaziDriveSyncDebounced"

        /**
         * Schedule a debounced background sync. Multiple calls within the delay window
         * replace previous pending work so only a single batch sync executes.
         */
        fun scheduleDebouncedSync(context: Context, delayMinutes: Long = 10L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<RaaziDriveSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            Log.d(TAG, "Scheduled debounced Drive sync in $delayMinutes minutes")
        }

        /**
         * Trigger an immediate background sync if network is connected.
         */
        fun triggerImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<RaaziDriveSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${UNIQUE_WORK_NAME}_Immediate",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? RaaziApplication ?: return Result.failure()
            val result = GoogleDriveSyncManager.sync(
                context = applicationContext,
                settingsDataStore = app.settingsDataStore,
                database = app.database
            )

            if (result.isSuccess) {
                Log.d(TAG, "Background Google Drive sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Background Google Drive sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in RaaziDriveSyncWorker", e)
            Result.retry()
        }
    }
}
