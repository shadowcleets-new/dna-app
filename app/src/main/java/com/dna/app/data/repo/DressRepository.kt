package com.dna.app.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.dna.app.data.local.db.DressDao
import com.dna.app.data.local.db.toDomain
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
}
