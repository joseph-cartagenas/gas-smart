package com.smartgas.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartgas.app.domain.repository.FuelTransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs pending (offline) [FuelTransaction] records
 * to Firestore once network connectivity is restored.
 *
 * Annotated with [@HiltWorker] so that repository dependencies can be
 * injected via Hilt's [HiltWorkerFactory].
 *
 * Retry strategy: exponential back-off starting at 30 seconds.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: FuelTransactionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID)
            ?: return Result.failure()

        return runCatching {
            repository.syncPendingTransactions(userId)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        private const val WORK_NAME = "sync_fuel_transactions"

        /**
         * Enqueues a one-time sync work request that runs only when the
         * device has network connectivity. Duplicate requests are collapsed
         * (KEEP policy) to avoid redundant syncs.
         */
        fun enqueue(context: Context, userId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    androidx.work.ExistingWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
