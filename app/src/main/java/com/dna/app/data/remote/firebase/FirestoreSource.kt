package com.dna.app.data.remote.firebase

import com.dna.app.domain.model.DesignSpec
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.Embellishment
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Neckline
import com.dna.app.domain.taxonomy.Occasion
import com.dna.app.domain.taxonomy.Silhouette
import com.dna.app.domain.taxonomy.SleeveStyle
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout:
 *   dresses/{dressId} → fields mirror DressEntity, enums as string names.
 *   `designSpec` is written by the `tagDress` Cloud Function (never the client).
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
        // Use merge so a pre-existing `designSpec` (written server-side by tagDress)
        // is not clobbered when the client re-writes image URLs.
        dresses.document(dress.id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
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
                syncState = SyncState.SYNCED,
                createdAt = doc.getLong("createdAt") ?: 0L,
                designSpec = parseDesignSpec(doc.get("designSpec")),
            )
        }
    }

    /**
     * Parse a DesignSpec map written by the `tagDress` Cloud Function.
     * Any parse failure drops the spec to null — UI handles that as "untagged".
     */
    private fun parseDesignSpec(raw: Any?): DesignSpec? {
        val map = raw as? Map<*, *> ?: return null
        return runCatching {
            DesignSpec(
                garmentType = GarmentType.valueOf(map["garmentType"] as String),
                neckline = Neckline.valueOf(map["neckline"] as String),
                sleeve = SleeveStyle.valueOf(map["sleeve"] as String),
                silhouette = Silhouette.valueOf(map["silhouette"] as String),
                occasion = Occasion.valueOf(map["occasion"] as String),
                embellishments = (map["embellishments"] as? List<*>).orEmpty()
                    .mapNotNull { runCatching { Embellishment.valueOf(it as String) }.getOrNull() },
                dominantColors = (map["dominantColors"] as? List<*>).orEmpty()
                    .mapNotNull { it as? String },
            )
        }.getOrNull()
    }
}
