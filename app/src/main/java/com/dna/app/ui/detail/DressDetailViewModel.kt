package com.dna.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dna.app.data.repo.ColorCorrectionRepository
import com.dna.app.data.repo.DressRepository
import com.dna.app.domain.color.ColorCorrection
import com.dna.app.domain.color.ColorPreset
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
    private val correctionRepo: ColorCorrectionRepository,
    private val delete: DeleteDressUseCase,
) : ViewModel() {

    private val dressId: String = checkNotNull(savedStateHandle["dressId"])

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Live (uncommitted) correction the user is dragging. */
    private val _draftCorrection = MutableStateFlow(ColorCorrection.NEUTRAL)
    val draftCorrection: StateFlow<ColorCorrection> = _draftCorrection.asStateFlow()

    private val _neutralTapEnabled = MutableStateFlow(false)
    val neutralTapEnabled: StateFlow<Boolean> = _neutralTapEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            val dress = repo.findById(dressId)
            _state.value = if (dress == null) DetailUiState.NotFound else DetailUiState.Loaded(dress)
            correctionRepo.ensureLoaded(dressId)
        }
        viewModelScope.launch {
            correctionRepo.observe(dressId).collect { saved ->
                // Initial load: hydrate the draft. Subsequent emissions only sync if the
                // user hasn't started fiddling — easiest heuristic is "draft equals previous saved".
                if (_draftCorrection.value == ColorCorrection.NEUTRAL || _draftCorrection.value == saved) {
                    _draftCorrection.value = saved
                }
            }
        }
    }

    fun updateDraft(correction: ColorCorrection) {
        _draftCorrection.value = correction
    }

    fun applyPreset(preset: ColorPreset) {
        _draftCorrection.value = preset.correction
    }

    fun toggleNeutralTap(enabled: Boolean) {
        _neutralTapEnabled.value = enabled
    }

    fun applyWhiteBalance(gain: FloatArray) {
        _draftCorrection.value = _draftCorrection.value.copy(whiteBalanceRgb = gain)
        _neutralTapEnabled.value = false
    }

    fun saveCorrection() {
        val toSave = _draftCorrection.value
        viewModelScope.launch { correctionRepo.save(dressId, toSave) }
    }

    fun resetCorrection() {
        _draftCorrection.value = ColorCorrection.NEUTRAL
        viewModelScope.launch { correctionRepo.reset(dressId) }
    }

    fun deleteDress() {
        viewModelScope.launch {
            delete(dressId)
                .onSuccess { _state.value = DetailUiState.Deleted }
                .onFailure { _state.value = DetailUiState.Error(it.message ?: "Delete failed") }
        }
    }
}
