package com.appmovilidadclinica.driver.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val documentNumber: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
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
            // La sesion se actualiza sola via AuthRepository.isLoggedIn() (el NavHost
            // reacciona al cambio) — este ViewModel no navega, solo reporta error.
            val result = authRepository.login(state.documentNumber.trim(), state.password)
            result.fold(
                onSuccess = { authResult ->
                    if (authResult.user.role != "DRIVER") {
                        authRepository.clearSession()
                        _uiState.update {
                            it.copy(submitting = false, errorMessage = "Esta app es para conductores")
                        }
                    } else {
                        _uiState.update { it.copy(submitting = false) }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(submitting = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Unauthorized -> "Documento o contraseña incorrectos."
        is AppError.Forbidden -> error.message
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}
