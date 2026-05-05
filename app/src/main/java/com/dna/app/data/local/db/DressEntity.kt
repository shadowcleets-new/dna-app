package com.dna.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dna.app.domain.model.DesignSpec
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState
import kotlinx.serialization.json.Json

@Entity(tableName = "dresses")
data class DressEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val imageThumbUrl: String,
    val imageDisplayUrl: String,
    val imageOriginalUrl: String?,
    val garmentType: GarmentType,
    val source: Source,
    val syncState: SyncState,
    val createdAt: Long,
    /** JSON-serialized [DesignSpec]. Null until the M3b tagging round-trip finishes. */
    val designSpecJson: String? = null,
    /** Image vs video. Defaults to IMAGE for rows migrated from the M4 schema. */
    val mediaType: MediaType = MediaType.IMAGE,
    val mimeType: String? = null,
    val videoOriginalUrl: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

private val json: Json = Json { ignoreUnknownKeys = true }

fun DressEntity.toDomain(): DressItem = DressItem(
    id = id,
    ownerUid = ownerUid,
    imageThumbUrl = imageThumbUrl,
    imageDisplayUrl = imageDisplayUrl,
    imageOriginalUrl = imageOriginalUrl,
    garmentType = garmentType,
    source = source,
    syncState = syncState,
    createdAt = createdAt,
    designSpec = designSpecJson?.let {
        runCatching { json.decodeFromString(DesignSpec.serializer(), it) }.getOrNull()
    },
    mediaType = mediaType,
    mimeType = mimeType,
    videoOriginalUrl = videoOriginalUrl,
    durationMs = durationMs,
    width = width,
    height = height,
)

fun DressItem.toEntity(): DressEntity = DressEntity(
    id = id,
    ownerUid = ownerUid,
    imageThumbUrl = imageThumbUrl,
    imageDisplayUrl = imageDisplayUrl,
    imageOriginalUrl = imageOriginalUrl,
    garmentType = garmentType,
    source = source,
    syncState = syncState,
    createdAt = createdAt,
    designSpecJson = designSpec?.let { json.encodeToString(DesignSpec.serializer(), it) },
    mediaType = mediaType,
    mimeType = mimeType,
    videoOriginalUrl = videoOriginalUrl,
    durationMs = durationMs,
    width = width,
    height = height,
)
