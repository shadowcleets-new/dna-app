package com.dna.app.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.dna.app.data.local.db.DressDao
import com.dna.app.data.local.db.toDomain
import com.dna.app.data.local.db.toEntity
import com.dna.app.data.remote.firebase.FirestoreSource
import com.dna.app.domain.model.DressItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Library repository — Room is the source of truth. Firestore ↔ Room sync lands
 * in M4 via `LibrarySyncWorker`.
 */
@Singleton
class DressRepository @Inject constructor(
    private val dao: DressDao,
    private val firestore: FirestoreSource,
) {

    fun pagedLibrary(uid: String): Flow<PagingData<DressItem>> =
        Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 10,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { dao.pagedByOwner(uid) },
        ).flow.map { page -> page.map { it.toDomain() } }

    fun countForOwner(uid: String): Flow<Int> = dao.countByOwner(uid)

    /** Insert a placeholder row immediately so the grid reflects the upload. */
    suspend fun insertLocal(dress: DressItem) = dao.upsert(dress.toEntity())

    suspend fun findById(id: String): DressItem? = dao.byId(id)?.toDomain()

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Reconcile Room against Firestore for [uid]. Adds rows missing locally,
     * updates diverging rows, and drops locally-SYNCED rows that no longer
     * exist remotely (other device deleted them). Rows still PENDING_UPLOAD
     * are left alone — WorkManager still owns them.
     */
    suspend fun reconcile(uid: String) {
        val remote = firestore.listByOwner(uid)
        dao.upsertAll(remote.map { it.toEntity() })

        // Drop synced-locally rows that vanished remotely (other-device delete).
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        dao.syncedIdsForOwner(uid).forEach { id ->
            if (id !in remoteIds) dao.delete(id)
        }
    }
}
