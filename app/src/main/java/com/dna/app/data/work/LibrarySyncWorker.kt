package com.dna.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.repo.DressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic Firestore → Room reconcile. Handles:
 *   - dresses uploaded from another device (pulls them in)
 *   - dresses deleted from another device (drops the stale local row)
 *   - designSpec filled in server-side by tagDress after the client went
 *     offline before the callable returned
 *
 * Runs every 6h with a connected constraint; [kickOnce] is called on
 * sign-in / app resume for snappy fresh-device reconciliation.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val repo: DressRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUid ?: return Result.success() // no-op when signed out
        return runCatching {
            repo.reconcile(uid)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_ONCE = "library-sync-once"
        private const val UNIQUE_PERIODIC = "library-sync-periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun kickOnce(context: Context) {
            val req = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_ONCE, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
