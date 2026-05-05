package com.dna.app.domain.model

import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState

/**
 * Domain model for a dress (or video clip) in the user's library.
 *
 * `designSpec` is null until the M3b tagging worker completes (Gemini 2.5
 * Flash round-trip). For [MediaType.VIDEO] items, the `image*Url` fields hold
 * poster-frame tiers and the playable file lives at [videoOriginalUrl].
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
    val mediaType: MediaType = MediaType.IMAGE,
    val mimeType: String? = null,
    val videoOriginalUrl: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)
