package com.appmovilidadclinica.passenger.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.model.User
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vive en la raiz de la navegacion (ver NavGraph.kt) — decide Login vs.
 * pantallas de pasajero, y centraliza el aviso T-2min + logout forzado por
 * 401, mismo patron que `useAuth.ts` + `AppLayout.vue` del panel admin.
 *
 * Inyecta `AuthRepository` directo (no un UseCase intermedio) — ver
 * memoria "android-passenger-module/ponytail-audit": los use cases que solo
 * delegaban sin logica propia se eliminaron.
 *
 * El NavGraph siempre arranca en Login. Si hay sesion guardada en
 * DataStore, el Flow emite el user casi inmediatamente y el
 * LaunchedEffect(user) navega a TripSearch. Puede haber un flash
 * breve Login -> TripSearch, pero es preferible a crashear.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val user: StateFlow<User?> = authRepository.observeSession()
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