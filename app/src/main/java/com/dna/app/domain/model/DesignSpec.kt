package com.dna.app.domain.model

import com.dna.app.domain.taxonomy.Embellishment
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Neckline
import com.dna.app.domain.taxonomy.Occasion
import com.dna.app.domain.taxonomy.Silhouette
import com.dna.app.domain.taxonomy.SleeveStyle
import kotlinx.serialization.Serializable

/**
 * Auto-tagged structured attributes of a dress. Narrow M3b subset — the full
 * spec (sleeveDetail, length, placement, fit, notes) lands in M5 when the
 * generation pipeline needs it.
 *
 * Schema is mirrored in `functions/src/schemas/taxonomy.ts` and used as
 * Gemini's responseSchema constraint, so enum values MUST stay in sync.
 *
 * Persisted as JSON on `DressEntity.designSpecJson` (opaque to Room).
 */
@Serializable
data class DesignSpec(
    val garmentType: GarmentType,
    val neckline: Neckline,
    val sleeve: SleeveStyle,
    val silhouette: Silhouette,
    val occasion: Occasion,
    val embellishments: List<Embellishment>,
    /** 1-3 hex strings like "#A03F52". */
    val dominantColors: List<String>,
)
