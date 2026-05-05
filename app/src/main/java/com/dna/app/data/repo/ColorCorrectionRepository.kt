package com.dna.app.data.repo

import com.dna.app.data.local.db.ColorCorrectionDao
import com.dna.app.data.local.db.toDomain
import com.dna.app.data.local.db.toEntity
import com.dna.app.data.remote.firebase.FirestoreSource
import com.dna.app.domain.color.ColorCorrection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-dress colour-correction settings. Room is the source of truth for the
 * UI; Firestore mirrors writes so corrections roam across devices. Reads
 * never block on Firestore — a one-shot fetch on first access lazily backfills
 * Room when needed.
 */
@Singleton
class ColorCorrectionRepository @Inject constructor(
    private val dao: ColorCorrectionDao,
    private val firestore: FirestoreSource,
) {

    /** Live updates for the detail screen — emits NEUTRAL while no row exists. */
    fun observe(dressId: String): Flow<ColorCorrection> =
        dao.observe(dressId).map { it?.toDomain() ?: ColorCorrection.NEUTRAL }

    /** Fetch from Firestore once and cache to Room. Safe to call on screen open. */
    suspend fun ensureLoaded(dressId: String) {
        if (dao.byId(dressId) != null) return
        val remote = runCatching { firestore.getColorCorrection(dressId) }.getOrNull() ?: return
        dao.upsert(remote.toEntity(dressId, System.currentTimeMillis()))
    }

    /** Persist locally first (instant UI confirmation), then mirror to Firestore. */
    suspend fun save(dressId: String, correction: ColorCorrection) {
        val now = System.currentTimeMillis()
        dao.upsert(correction.toEntity(dressId, now))
        runCatching { firestore.putColorCorrection(dressId, correction) }
    }

    suspend fun reset(dressId: String) {
        dao.delete(dressId)
        runCatching { firestore.putColorCorrection(dressId, ColorCorrection.NEUTRAL) }
    }
}
