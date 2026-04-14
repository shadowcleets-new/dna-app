package com.dna.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dna.app.data.repo.DressRepository
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.usecase.DeleteDressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Loaded(val dress: DressItem) : DetailUiState
    data object Deleted : DetailUiState
    data object NotFound : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DressDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DressRepository,
    private val delete: DeleteDressUseCase,
) : ViewModel() {

    private val dressId: String = checkNotNull(savedStateHandle["dressId"])

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val dress = repo.findById(dressId)
            _state.value = if (dress == null) DetailUiState.NotFound else DetailUiState.Loaded(dress)
        }
    }

    fun deleteDress() {
        viewModelScope.launch {
            delete(dressId)
                .onSuccess { _state.value = DetailUiState.Deleted }
                .onFailure { _state.value = DetailUiState.Error(it.message ?: "Delete failed") }
        }
    }
}
