package com.dna.app.domain.model

import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState

/**
 * Domain model for a dress in the user's library.
 *
 * `designSpec` is null until the M3b tagging worker completes (Gemini 2.5
 * Flash round-trip).
 */
data class DressItem(
    val id: String,
    val ownerUid: String,
    val imageThumbUrl: String,
    val imageDisplayUrl: String,
    val imageOriginalUrl: String?,
    val garmentType: GarmentType,
    val source: Source,
    val syncState: SyncState,
    val createdAt: Long,
    val designSpec: DesignSpec? = null,
)
