package com.dna.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dna.app.data.local.db.DressDao
import com.dna.app.data.local.db.toDomain
import com.dna.app.data.local.db.toEntity
import com.dna.app.data.remote.firebase.FirestoreSource
import com.dna.app.data.remote.firebase.StorageSource
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Picks up a dress row that's already in Room with `syncState = PENDING_UPLOAD`
 * and pushes its tiers to Firebase Storage + a doc to Firestore, then flips
 * the row to SYNCED. Branches on [MediaType] so videos upload their raw bytes
 * (poster frames go through the same display/thumb tiers).
 */
@HiltWorker
class UploadImageWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DressDao,
    private val storage: StorageSource,
    private val firestore: FirestoreSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dressId = inputData.getString(KEY_DRESS_ID) ?: return Result.failure()
        val thumbPath = inputData.getString(KEY_THUMB_PATH) ?: return Result.failure()
        val displayPath = inputData.getString(KEY_DISPLAY_PATH) ?: return Result.failure()
        val originalPath = inputData.getString(KEY_ORIGINAL_PATH) ?: return Result.failure()
        val originalContentType = inputData.getString(KEY_ORIGINAL_CONTENT_TYPE) ?: "application/octet-stream"
        val mediaTypeName = inputData.getString(KEY_MEDIA_TYPE) ?: MediaType.IMAGE.name
        val mediaType = runCatching { MediaType.valueOf(mediaTypeName) }.getOrDefault(MediaType.IMAGE)

        val row = dao.byId(dressId) ?: return Result.failure()

        return runCatching {
            val uploaded = storage.uploadDress(
                uid = row.ownerUid,
                dressId = dressId,
                thumb = StorageSource.TierFile(File(thumbPath), "image/jpeg"),
                display = StorageSource.TierFile(File(displayPath), "image/jpeg"),
                original = StorageSource.TierFile(File(originalPath), originalContentType),
                isVideo = mediaType == MediaType.VIDEO,
            )
            val synced = row.toDomain().copy(
                imageThumbUrl = uploaded.thumbUrl,
                imageDisplayUrl = uploaded.displayUrl,
                imageOriginalUrl = uploaded.originalUrl,
                videoOriginalUrl = uploaded.videoOriginalUrl ?: row.videoOriginalUrl,
                syncState = SyncState.SYNCED,
            )
            firestore.putDress(synced)
            dao.upsert(synced.toEntity())

            // Clean up staged files — originals live remotely now.
            listOf(thumbPath, displayPath, originalPath).forEach { runCatching { File(it).delete() } }

            // Tagging only makes sense for stills (Gemini Image input).
            if (mediaType == MediaType.IMAGE) {
                TagDressWorker.enqueue(applicationContext, dressId)
            }
            Result.success()
        }.getOrElse { e ->
            val failed = row.copy(syncState = SyncState.FAILED)
            dao.upsert(failed)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(
                workDataOf(KEY_ERROR to (e.message ?: "upload failed")),
            )
        }
    }

    companion object {
        private const val KEY_DRESS_ID = "dressId"
        private const val KEY_THUMB_PATH = "thumb"
        private const val KEY_DISPLAY_PATH = "display"
        private const val KEY_ORIGINAL_PATH = "original"
        private const val KEY_ORIGINAL_CONTENT_TYPE = "originalContentType"
        private const val KEY_MEDIA_TYPE = "mediaType"
        const val KEY_ERROR = "error"

        private const val MAX_RETRIES = 4
        private const val UNIQUE_PREFIX = "upload-dress-"

        fun enqueue(
            context: Context,
            dressId: String,
            thumb: File,
            display: File,
            original: File,
            originalContentType: String,
            mediaType: MediaType,
        ) {
            val input = Data.Builder()
                .putString(KEY_DRESS_ID, dressId)
                .putString(KEY_THUMB_PATH, thumb.absolutePath)
                .putString(KEY_DISPLAY_PATH, display.absolutePath)
                .putString(KEY_ORIGINAL_PATH, original.absolutePath)
                .putString(KEY_ORIGINAL_CONTENT_TYPE, originalContentType)
                .putString(KEY_MEDIA_TYPE, mediaType.name)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadImageWorker>()
                .setInputData(input)
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
