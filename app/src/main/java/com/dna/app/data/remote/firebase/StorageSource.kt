package com.dna.app.data.remote.firebase

import com.google.firebase.storage.FirebaseStorage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Uploads dress image tiers to Firebase Storage under
 *   `users/{uid}/dresses/{dressId}/{tier}.{ext}`
 *
 * Tier layout matches `ImageTiers`:
 *   - original (original extension preserved as `.bin`; contentType detected on server)
 *   - display (.jpg)
 *   - thumb   (.jpg)
 */
@Singleton
class StorageSource @Inject constructor(
    private val storage: FirebaseStorage,
) {

    data class UploadedTiers(
        val thumbUrl: String,
        val displayUrl: String,
        val originalUrl: String,
    )

    suspend fun uploadDress(
        uid: String,
        dressId: String,
        thumb: File,
        display: File,
        original: File,
    ): UploadedTiers {
        val base = storage.reference.child("users/$uid/dresses/$dressId")
        val thumbUrl = uploadAndGetUrl(base.child("thumb.jpg"), thumb, "image/jpeg")
        val displayUrl = uploadAndGetUrl(base.child("display.jpg"), display, "image/jpeg")
        val originalUrl = uploadAndGetUrl(base.child("original.bin"), original, null)
        return UploadedTiers(thumbUrl, displayUrl, originalUrl)
    }

    /**
     * Best-effort delete of all tiers for a dress. Individual missing objects
     * are ignored — the goal is "nothing lingering in our bucket".
     */
    suspend fun deleteDress(uid: String, dressId: String) {
        val base = storage.reference.child("users/$uid/dresses/$dressId")
        listOf("thumb.jpg", "display.jpg", "original.bin").forEach { name ->
            runCatching { base.child(name).delete().await() }
        }
    }

    private suspend fun uploadAndGetUrl(
        ref: com.google.firebase.storage.StorageReference,
        file: File,
        contentType: String?,
    ): String {
        val metadata = com.google.firebase.storage.StorageMetadata.Builder().apply {
            contentType?.let { setContentType(it) }
        }.build()
        ref.putFile(android.net.Uri.fromFile(file), metadata).await()
        return ref.downloadUrl.await().toString()
    }
}
