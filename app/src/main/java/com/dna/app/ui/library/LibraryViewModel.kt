package com.dna.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.repo.DressRepository
import com.dna.app.domain.model.DressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val repo: DressRepository,
) : ViewModel() {

    val dresses: Flow<PagingData<DressItem>> = auth.observeUid()
        .flatMapLatest { uid ->
            if (uid == null) emptyFlow() else repo.pagedLibrary(uid)
        }
        .cachedIn(viewModelScope)

    /** 0 until the count stream emits. Drives the empty-state. */
    val count = auth.observeUid()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(0) else repo.countForOwner(uid)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
