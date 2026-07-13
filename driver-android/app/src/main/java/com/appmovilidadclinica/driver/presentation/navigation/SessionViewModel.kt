package com.appmovilidadclinica.driver.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Vive en la raiz de la navegacion — decide Login vs. resto de la app segun
 * la sesion guardada (mismo espiritu que el SessionViewModel de la app pasajero).
 */
class SessionViewModel(
    authRepository: AuthRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
