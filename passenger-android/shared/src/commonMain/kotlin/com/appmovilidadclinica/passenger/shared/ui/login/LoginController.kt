package com.appmovilidadclinica.passenger.shared.ui.login

import com.appmovilidadclinica.passenger.shared.data.local.SessionStore
import com.appmovilidadclinica.passenger.shared.data.local.StoredSession
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

data class LoginUiState(
    val documentNumber: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Logica del login, comun a Android e iOS (antes vivia solo en el ViewModel
 * Android con Hilt). No depende de `androidx`: es una clase comun con su
 * propio scope, igual que las pantallas del conductor.
 *
 * @param signIn la operacion de red (la provee el grafo compartido); asi el
 *   controlador se puede probar con un fake, sin backend.
 */
class LoginController(
    private val sessionStore: SessionStore,
    private val signIn: suspend (documentNumber: String, password: String) -> AppResult<StoredSession>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
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
        // Anti doble envio: si ya hay una peticion en curso, se ignora.
        if (state.submitting) return
        if (state.documentNumber.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Complete el usuario y la contraseña.") }
            return
        }
        _uiState.update { it.copy(submitting = true, errorMessage = null) }
        scope.launch {
            when (val result = signIn(state.documentNumber.trim(), state.password)) {
                is AppResult.Success -> {
                    // La sesion se persiste aca: el grafo observa el SessionStore
                    // y cambia solo a la pantalla autenticada.
                    sessionStore.save(result.data)
                    _uiState.update { it.copy(submitting = false) }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, errorMessage = messageFor(result.error))
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Unauthorized -> "Usuario o contraseña incorrectos."
        is AppError.Forbidden -> error.message
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}
