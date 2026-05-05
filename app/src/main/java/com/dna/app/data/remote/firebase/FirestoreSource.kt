package com.dna.app.data.remote.firebase

import com.dna.app.domain.color.ColorCorrection
import com.dna.app.domain.model.DesignSpec
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.Embellishment
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.Neckline
import com.dna.app.domain.taxonomy.Occasion
import com.dna.app.domain.taxonomy.Silhouette
import com.dna.app.domain.taxonomy.SleeveStyle
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout:
 *   dresses/{dressId}                       → fields mirror DressEntity, enums as string names.
 *   dresses/{dressId}/correction/current    → per-dress colour-correction subdoc (M5c).
 *
 * `designSpec` is written by the `tagDress` Cloud Function (never the client).
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
            "mediaType" to dress.mediaType.name,
            "mimeType" to dress.mimeType,
            "videoOriginalUrl" to dress.videoOriginalUrl,
            "durationMs" to dress.durationMs,
            "width" to dress.width,
            "height" to dress.height,
        )
        // Use merge so a pre-existing `designSpec` (written server-side by tagDress)
        // is not clobbered when the client re-writes image URLs.
        dresses.document(dress.id).set(data, SetOptions.merge()).await()
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
                mediaType = runCatching { MediaType.valueOf(doc.getString("mediaType")!!) }
                    .getOrDefault(MediaType.IMAGE),
                mimeType = doc.getString("mimeType"),
                videoOriginalUrl = doc.getString("videoOriginalUrl"),
                durationMs = doc.getLong("durationMs"),
                width = (doc.getLong("width"))?.toInt(),
                height = (doc.getLong("height"))?.toInt(),
            )
        }
    }

    suspend fun putColorCorrection(dressId: String, correction: ColorCorrection) {
        val wb = correction.whiteBalanceRgb
        val data = mapOf(
            "brightness" to correction.brightness,
            "contrast" to correction.contrast,
            "saturation" to correction.saturation,
            "exposureEv" to correction.exposureEv,
            "temperatureK" to correction.temperatureK,
            "tint" to correction.tint,
            "whiteBalanceR" to wb?.getOrNull(0),
            "whiteBalanceG" to wb?.getOrNull(1),
            "whiteBalanceB" to wb?.getOrNull(2),
            "updatedAt" to System.currentTimeMillis(),
        )
        dresses.document(dressId)
            .collection("correction")
            .document("current")
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun getColorCorrection(dressId: String): ColorCorrection? {
        val snap = dresses.document(dressId)
            .collection("correction")
            .document("current")
            .get()
            .await()
        if (!snap.exists()) return null
        return ColorCorrection(
            brightness = (snap.getDouble("brightness") ?: 0.0).toFloat(),
            contrast = (snap.getDouble("contrast") ?: 0.0).toFloat(),
            saturation = (snap.getDouble("saturation") ?: 0.0).toFloat(),
            exposureEv = (snap.getDouble("exposureEv") ?: 0.0).toFloat(),
            temperatureK = (snap.getDouble("temperatureK") ?: 6500.0).toFloat(),
            tint = (snap.getDouble("tint") ?: 0.0).toFloat(),
            whiteBalanceRgb = run {
                val r = snap.getDouble("whiteBalanceR")
                val g = snap.getDouble("whiteBalanceG")
                val b = snap.getDouble("whiteBalanceB")
                if (r != null && g != null && b != null) {
                    floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat())
                } else {
                    null
                }
            },
        )
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
