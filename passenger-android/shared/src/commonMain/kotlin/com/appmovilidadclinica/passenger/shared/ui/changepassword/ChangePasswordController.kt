package com.appmovilidadclinica.passenger.shared.ui.changepassword

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentError: String? = null,
    val newError: String? = null,
    val confirmError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Cambio de contraseña, comun a Android e iOS (antes solo en el modulo
 * Android). Se conservan las mismas validaciones y mensajes.
 */
class ChangePasswordController(
    private val changePassword: suspend (current: String, new: String) -> AppResult<Unit>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
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

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return

        val currentError = if (state.currentPassword.isBlank()) "Ingrese su clave actual." else null
        val newError = when {
            state.newPassword.isBlank() -> "Ingrese la nueva clave."
            state.newPassword.length < 8 -> "Mínimo 8 caracteres."
            state.newPassword == state.currentPassword -> "La nueva clave debe ser distinta a la actual."
            else -> null
        }
        val confirmError = when {
            state.confirmPassword.isBlank() -> "Confirme la nueva clave."
            state.confirmPassword != state.newPassword -> "Las claves no coinciden."
            else -> null
        }
        if (currentError != null || newError != null || confirmError != null) {
            _uiState.update {
                it.copy(currentError = currentError, newError = newError, confirmError = confirmError)
            }
            return
        }

        _uiState.update { it.copy(submitting = true, formError = null) }
        scope.launch {
            when (val result = changePassword(state.currentPassword, state.newPassword)) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false, saved = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, formError = messageFor(result.error))
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    /** Los campos escritos se conservan: solo se marca el error. */
    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> error.message.ifBlank { "La clave actual es incorrecta." }
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        is AppError.Validation -> error.message
        is AppError.Forbidden -> error.message
        is AppError.NotFound -> error.message
        is AppError.Conflict -> error.message
        is AppError.Unknown -> error.message.ifBlank { "Ocurrió un error inesperado. Intente nuevamente." }
    }
}
