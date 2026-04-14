package com.dna.app.domain.usecase

import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.remote.firebase.FirestoreSource
import com.dna.app.data.remote.firebase.StorageSource
import com.dna.app.data.repo.DressRepository
import javax.inject.Inject

/**
 * Remove a dress from Storage (all tiers), Firestore, and Room. Room goes
 * last so a retriable partial failure still leaves the row visible in the
 * Library rather than orphaning the remote state.
 */
class DeleteDressUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val repo: DressRepository,
    private val firestore: FirestoreSource,
    private val storage: StorageSource,
) {

    suspend operator fun invoke(dressId: String): Result<Unit> = runCatching {
        val uid = auth.currentUid ?: error("Not signed in")
        storage.deleteDress(uid, dressId)
        firestore.deleteDress(dressId)
        repo.delete(dressId)
    }
}
