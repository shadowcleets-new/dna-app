package com.dna.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dna.app.data.auth.AuthRepository
import com.dna.app.data.auth.SignInException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SignInState {
    data object Idle : SignInState
    data object InProgress : SignInState
    data class Error(val message: String) : SignInState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    /** Null until the auth system emits; then uid string when signed in. */
    val currentUid: StateFlow<String?> = repo.observeUid()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.currentUid)

    fun signIn(activityContext: Context) {
        if (_state.value is SignInState.InProgress) return
        _state.value = SignInState.InProgress
        viewModelScope.launch {
            try {
                repo.signInWithGoogle(activityContext)
                _state.value = SignInState.Idle
            } catch (e: SignInException) {
                _state.value = SignInState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repo.signOut() }
    }

    fun dismissError() {
        if (_state.value is SignInState.Error) _state.value = SignInState.Idle
    }
}
