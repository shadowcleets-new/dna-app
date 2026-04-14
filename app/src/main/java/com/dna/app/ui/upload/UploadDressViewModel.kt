package com.dna.app.ui.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dna.app.domain.taxonomy.GarmentType
import com.dna.app.domain.usecase.UploadDressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UploadState {
    data object Idle : UploadState
    data object Working : UploadState
    data class Done(val dressId: String) : UploadState
    data class Error(val message: String) : UploadState
}

@HiltViewModel
class UploadDressViewModel @Inject constructor(
    private val useCase: UploadDressUseCase,
) : ViewModel() {

    private val _garmentType = MutableStateFlow(GarmentType.KURTI)
    val garmentType: StateFlow<GarmentType> = _garmentType.asStateFlow()

    private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
    val state: StateFlow<UploadState> = _state.asStateFlow()

    fun setGarmentType(type: GarmentType) { _garmentType.value = type }

    fun upload(uri: Uri) {
        if (_state.value is UploadState.Working) return
        _state.value = UploadState.Working
        viewModelScope.launch {
            _state.value = useCase(uri, _garmentType.value).fold(
                onSuccess = { UploadState.Done(it) },
                onFailure = { UploadState.Error(it.message ?: "Upload failed") },
            )
        }
    }

    fun reset() { _state.value = UploadState.Idle }
}
