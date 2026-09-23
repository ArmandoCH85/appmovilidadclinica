package com.appmovilidadclinica.passenger.presentation.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.repository.AuthRepository
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val currentError: String? = null,
    val newError: String? = null,
    val confirmError: String? = null,
    /** Error general (ej. mensaje del backend si la actual no coincide). */
    val formError: String? = null,
    val submitting: Boolean = false,
    /** Se consume una sola vez: la pantalla muestra el snackbar y navega atras. */
    val saved: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState

    fun onCurrentChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, currentError = null, formError = null) }
    }

    fun onNewChange(value: String) {
        _uiState.update { it.copy(newPassword = value, newError = null, confirmError = null, formError = null) }
    }

    fun onConfirmChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }
    }

    fun toggleCurrentVisibility() {
        _uiState.update { it.copy(currentVisible = !it.currentVisible) }
    }

    fun toggleNewVisibility() {
        _uiState.update { it.copy(newVisible = !it.newVisible) }
    }

    fun toggleConfirmVisibility() {
        _uiState.update { it.copy(confirmVisible = !it.confirmVisible) }
    }

    fun submit() {
        val s = _uiState.value
        val currentError = if (s.currentPassword.isBlank()) "Ingrese su clave actual." else null
        val newError = when {
            s.newPassword.isBlank() -> "Ingrese la nueva clave."
            s.newPassword.length < 8 -> "Mínimo 8 caracteres."
            s.newPassword == s.currentPassword -> "La nueva clave debe ser distinta a la actual."
            else -> null
        }
        val confirmError = when {
            s.confirmPassword.isBlank() -> "Confirme la nueva clave."
            s.confirmPassword != s.newPassword -> "Las claves no coinciden."
            else -> null
        }
        if (currentError != null || newError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    currentError = currentError,
                    newError = newError,
                    confirmError = confirmError,
                )
            }
            return
        }
        _uiState.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.changePassword(s.currentPassword, s.newPassword)) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false, saved = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, formError = messageFor(result.error))
                }
            }
        }
    }

    /** Los datos escritos se conservan: solo se setea formError, nunca se limpian los campos. */
    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> error.message.ifBlank { "La clave actual es incorrecta." }
        is AppError.Validation -> error.message
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        is AppError.Forbidden -> error.message
        is AppError.NotFound -> error.message
        is AppError.Conflict -> error.message
        is AppError.Unknown -> error.message.ifBlank { "Ocurrió un error inesperado. Intente nuevamente." }
    }
}
