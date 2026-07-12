package com.appmovilidadclinica.passenger.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.error.AppError
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val documentNumber: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onDocumentNumberChange(value: String) {
        _uiState.update { it.copy(documentNumber = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.documentNumber.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Complete el documento y la contraseña.") }
            return
        }
        _uiState.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            // Session se actualiza sola via ObserveSessionUseCase (NavGraph
            // reacciona al cambio) — este ViewModel no navega, solo reporta error.
            when (val result = loginUseCase(state.documentNumber.trim(), state.password)) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, errorMessage = messageFor(result.error))
                }
            }
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> "Documento o contraseña incorrectos."
        is AppError.Forbidden -> error.message
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}
