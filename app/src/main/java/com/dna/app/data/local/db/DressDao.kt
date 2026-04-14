package com.dna.app.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DressDao {

    /** Paging source for the Library grid, scoped to the signed-in user, newest first. */
    @Query("SELECT * FROM dresses WHERE ownerUid = :uid ORDER BY createdAt DESC")
    fun pagedByOwner(uid: String): PagingSource<Int, DressEntity>

    /** Lightweight count — drives the empty-state in the UI. */
    @Query("SELECT COUNT(*) FROM dresses WHERE ownerUid = :uid")
    fun countByOwner(uid: String): Flow<Int>

    @Query("SELECT * FROM dresses WHERE id = :id")
    suspend fun byId(id: String): DressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dress: DressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(dresses: List<DressEntity>)

    @Query("UPDATE dresses SET designSpecJson = :json WHERE id = :id")
    suspend fun updateDesignSpec(id: String, json: String)

    @Query("DELETE FROM dresses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM dresses WHERE ownerUid = :uid")
    suspend fun clearOwner(uid: String)
}
