package com.dna.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorCorrectionDao {

    @Query("SELECT * FROM color_corrections WHERE dressId = :dressId")
    fun observe(dressId: String): Flow<ColorCorrectionEntity?>

    @Query("SELECT * FROM color_corrections WHERE dressId = :dressId")
    suspend fun byId(dressId: String): ColorCorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ColorCorrectionEntity)

    @Query("DELETE FROM color_corrections WHERE dressId = :dressId")
    suspend fun delete(dressId: String)
}
