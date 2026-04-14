package com.dna.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.local.util.ImageFiles
import com.dna.app.data.repo.DressRepository
import com.dna.app.data.work.UploadImageWorker
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * M3 upload pipeline (local-first):
 *   1. Downsample the PhotoPicker Uri into thumb/display/original tiers.
 *   2. Insert a placeholder DressItem into Room with SyncState.PENDING_UPLOAD
 *      — so the Library grid updates instantly with a local thumb.
 *   3. Enqueue UploadImageWorker to push the three tiers to Firebase Storage
 *      and write the Firestore doc. The worker flips the row to SYNCED when
 *      done, so the grid re-resolves its image URLs from the remote tiers.
 *
 * Gemini auto-tagging lands in a follow-up: a second Worker that calls the
 * `tagDress` Cloud Function once the upload completes.
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

        val placeholder = DressItem(
            id = prepared.id,
            ownerUid = uid,
            // Local file:// URIs — Coil renders them before upload completes.
            imageThumbUrl = android.net.Uri.fromFile(prepared.thumb).toString(),
            imageDisplayUrl = android.net.Uri.fromFile(prepared.display).toString(),
            imageOriginalUrl = android.net.Uri.fromFile(prepared.original).toString(),
            garmentType = garmentType,
            source = Source.UPLOADED,
            syncState = SyncState.PENDING_UPLOAD,
            createdAt = System.currentTimeMillis(),
        )
        repo.insertLocal(placeholder)

        UploadImageWorker.enqueue(
            context = context,
            dressId = prepared.id,
            thumb = prepared.thumb,
            display = prepared.display,
            original = prepared.original,
        )
        prepared.id
    }
}
