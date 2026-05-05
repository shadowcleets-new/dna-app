package com.dna.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.local.util.ImageFiles
import com.dna.app.data.repo.DressRepository
import com.dna.app.data.work.UploadImageWorker
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Upload pipeline (local-first):
 *   1. Stage the picked Uri into thumb/display/original tiers (originals are
 *      bit-exact copies; videos are not transcoded).
 *   2. Insert a placeholder DressItem into Room with SyncState.PENDING_UPLOAD
 *      so the Library grid updates instantly with a local thumb.
 *   3. Enqueue UploadImageWorker to push tiers to Firebase Storage and write
 *      the Firestore doc. The worker flips the row to SYNCED when done.
 */
class UploadDressUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val repo: DressRepository,
    private val imageFiles: ImageFiles,
) {

    suspend operator fun invoke(uri: Uri, garmentType: GarmentType): Result<String> = runCatching {
        val uid = auth.currentUid ?: error("Not signed in")
        val prepared = imageFiles.prepare(uri)

        val localOriginalUri = android.net.Uri.fromFile(prepared.original).toString()
        val placeholder = DressItem(
            id = prepared.id,
            ownerUid = uid,
            // Local file:// URIs — Coil renders them before upload completes.
            imageThumbUrl = android.net.Uri.fromFile(prepared.thumb).toString(),
            imageDisplayUrl = android.net.Uri.fromFile(prepared.display).toString(),
            imageOriginalUrl = localOriginalUri,
            garmentType = garmentType,
            source = Source.UPLOADED,
            syncState = SyncState.PENDING_UPLOAD,
            createdAt = System.currentTimeMillis(),
            mediaType = prepared.mediaType,
            mimeType = prepared.mimeType,
            videoOriginalUrl = if (prepared.mediaType == MediaType.VIDEO) localOriginalUri else null,
            durationMs = prepared.durationMs,
            width = prepared.width,
            height = prepared.height,
        )
        repo.insertLocal(placeholder)

        UploadImageWorker.enqueue(
            context = context,
            dressId = prepared.id,
            thumb = prepared.thumb,
            display = prepared.display,
            original = prepared.original,
            originalContentType = prepared.originalContentType,
            mediaType = prepared.mediaType,
        )
        prepared.id
    }
}
