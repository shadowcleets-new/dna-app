package com.dna.app.data.remote.firebase

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Uploads media tiers to Firebase Storage under `users/{uid}/dresses/{dressId}/`.
 *
 * Image-only tiers:
 *   - thumb.jpg
 *   - display.jpg
 *   - original.<ext>           (real source extension; bit-exact copy of source bytes)
 *
 * Video tiers (in addition to thumb/display poster JPEGs):
 *   - original.<ext>           (raw video, never re-encoded)
 */
@Singleton
class StorageSource @Inject constructor(
    private val storage: FirebaseStorage,
) {

    data class TierFile(val file: File, val contentType: String)

    data class UploadedDress(
        val thumbUrl: String,
        val displayUrl: String,
        val originalUrl: String,
        /** Only set for video uploads — same value as [originalUrl] currently, kept distinct for clarity. */
        val videoOriginalUrl: String?,
    )

    suspend fun uploadDress(
        uid: String,
        dressId: String,
        thumb: TierFile,
        display: TierFile,
        original: TierFile,
        isVideo: Boolean,
    ): UploadedDress {
        val base = storage.reference.child("users/$uid/dresses/$dressId")
        val thumbUrl = uploadAndGetUrl(base.child("thumb.jpg"), thumb)
        val displayUrl = uploadAndGetUrl(base.child("display.jpg"), display)
        val originalName = "original.${original.file.extension.ifEmpty { defaultExt(original.contentType) }}"
        val originalUrl = uploadAndGetUrl(base.child(originalName), original)
        return UploadedDress(
            thumbUrl = thumbUrl,
            displayUrl = displayUrl,
            originalUrl = originalUrl,
            videoOriginalUrl = if (isVideo) originalUrl else null,
        )
    }

    /**
     * Best-effort delete of every object under a dress. Listing the prefix
     * means we don't have to know the exact original extension.
     */
    suspend fun deleteDress(uid: String, dressId: String) {
        val base = storage.reference.child("users/$uid/dresses/$dressId")
        runCatching {
            val list = base.listAll().await()
            list.items.forEach { item -> runCatching { item.delete().await() } }
        }
    }

    private suspend fun uploadAndGetUrl(ref: StorageReference, tier: TierFile): String {
        val metadata = StorageMetadata.Builder()
            .setContentType(tier.contentType)
            .build()
        ref.putFile(android.net.Uri.fromFile(tier.file), metadata).await()
        return ref.downloadUrl.await().toString()
    }

    private fun defaultExt(contentType: String): String = when (contentType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        else -> "bin"
    }
}
