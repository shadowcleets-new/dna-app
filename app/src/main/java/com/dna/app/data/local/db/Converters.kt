package com.dna.app.data.local.db

import androidx.room.TypeConverter
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.domain.taxonomy.Source
import com.dna.app.domain.taxonomy.SyncState

/**
 * Store enums as their `name()` strings so re-ordering / adding values never
 * corrupts existing rows.
 */
class Converters {
    @TypeConverter fun garmentTypeToString(v: GarmentType): String = v.name
    @TypeConverter fun stringToGarmentType(v: String): GarmentType = GarmentType.valueOf(v)

    @TypeConverter fun sourceToString(v: Source): String = v.name
    @TypeConverter fun stringToSource(v: String): Source = Source.valueOf(v)

    @TypeConverter fun syncStateToString(v: SyncState): String = v.name
    @TypeConverter fun stringToSyncState(v: String): SyncState = SyncState.valueOf(v)

    @TypeConverter fun mediaTypeToString(v: MediaType): String = v.name
    @TypeConverter fun stringToMediaType(v: String): MediaType = MediaType.valueOf(v)
}
