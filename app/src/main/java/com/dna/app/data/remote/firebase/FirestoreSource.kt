package com.dna.app.data.remote.firebase

import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Source
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout:
 *   dresses/{dressId} → fields mirror DressEntity, enums as string names.
 *
 * Security rules enforce `ownerUid == request.auth.uid`.
 */
@Singleton
class FirestoreSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private val dresses get() = firestore.collection("dresses")

    suspend fun putDress(dress: DressItem) {
        val data = mapOf(
            "id" to dress.id,
            "ownerUid" to dress.ownerUid,
            "imageThumbUrl" to dress.imageThumbUrl,
            "imageDisplayUrl" to dress.imageDisplayUrl,
            "imageOriginalUrl" to dress.imageOriginalUrl,
            "garmentType" to dress.garmentType.name,
            "source" to dress.source.name,
            "createdAt" to dress.createdAt,
        )
        dresses.document(dress.id).set(data).await()
    }

    suspend fun deleteDress(dressId: String) {
        dresses.document(dressId).delete().await()
    }

    /** Fetch once — proper snapshot listener lands in M4's sync worker. */
    suspend fun listByOwner(uid: String): List<DressItem> {
        val snap = dresses.whereEqualTo("ownerUid", uid).get().await()
        return snap.documents.mapNotNull { doc ->
            DressItem(
                id = doc.getString("id") ?: return@mapNotNull null,
                ownerUid = doc.getString("ownerUid") ?: return@mapNotNull null,
                imageThumbUrl = doc.getString("imageThumbUrl") ?: "",
                imageDisplayUrl = doc.getString("imageDisplayUrl") ?: "",
                imageOriginalUrl = doc.getString("imageOriginalUrl"),
                garmentType = runCatching { GarmentType.valueOf(doc.getString("garmentType")!!) }
                    .getOrDefault(GarmentType.KURTI),
                source = runCatching { Source.valueOf(doc.getString("source")!!) }
                    .getOrDefault(Source.UPLOADED),
                syncState = com.dna.app.domain.taxonomy.SyncState.SYNCED,
                createdAt = doc.getLong("createdAt") ?: 0L,
            )
        }
    }
}
