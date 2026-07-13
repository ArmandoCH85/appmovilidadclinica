package com.appmovilidadclinica.passenger.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.model.User
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vive en la raiz de la navegacion (ver NavGraph.kt) — decide Login vs.
 * pantallas de pasajero, y centraliza el aviso T-2min + logout forzado por
 * 401, mismo patron que `useAuth.ts` + `AppLayout.vue` del panel admin.
 *
 * `isLoading` es true hasta que DataStore emite el primer valor de sesion.
 * Sin esto, el NavGraph ve `user = null` (valor inicial del stateIn) y
 * arranca en Login, despues DataStore emite el user real y hay un flash
 * Login -> TripSearch. Con `isLoading`, el NavGraph espera a que DataStore
 * responda antes de decidir la pantalla inicial.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val user: StateFlow<User?> = authRepository.observeSession()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val secondsUntilExpiry: StateFlow<Long?> = authRepository.observeSecondsUntilExpiry()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessionExpiredDialogVisible = MutableStateFlow(false)
    val sessionExpiredDialogVisible: StateFlow<Boolean> = _sessionExpiredDialogVisible

    init {
        viewModelScope.launch {
            authRepository.observeSessionExpired().collect {
                authRepository.logout()
                _sessionExpiredDialogVisible.value = true
            }
        }
    }

    fun dismissSessionExpiredDialog() {
        _sessionExpiredDialogVisible.value = false
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
