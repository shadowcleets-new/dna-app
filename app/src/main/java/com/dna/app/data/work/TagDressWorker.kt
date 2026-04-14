package com.dna.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dna.app.data.local.db.DressDao
import com.dna.app.data.remote.functions.AiFunctionsClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import com.dna.app.domain.model.DesignSpec

/**
 * M3b: after an upload settles in Firestore + Storage, ask the server to
 * classify the image and mirror the resulting [DesignSpec] into Room.
 *
 * Chained after [UploadImageWorker] so we never call Gemini on bytes that
 * aren't yet in Storage. Retries up to [MAX_RETRIES] on transient failures —
 * Gemini rate limits, transient network, etc.
 */
@HiltWorker
class TagDressWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DressDao,
    private val ai: AiFunctionsClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dressId = inputData.getString(KEY_DRESS_ID) ?: return Result.failure()
        dao.byId(dressId) ?: return Result.failure() // row gone → nothing to tag.

        return runCatching {
            val spec = ai.tagDress(dressId)
            val json = Json.encodeToString(DesignSpec.serializer(), spec)
            dao.updateDesignSpec(dressId, json)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_DRESS_ID = "dressId"
        private const val MAX_RETRIES = 3
        private const val UNIQUE_PREFIX = "tag-dress-"

        fun enqueue(context: Context, dressId: String) {
            val request = OneTimeWorkRequestBuilder<TagDressWorker>()
                .setInputData(Data.Builder().putString(KEY_DRESS_ID, dressId).build())
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PREFIX + dressId,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
