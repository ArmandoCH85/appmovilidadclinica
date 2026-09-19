package com.appmovilidadclinica.driver.shared.ui.screens.incident

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.IncidentType
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val INCIDENT_DESCRIPTION_MAX_LENGTH = 1000

data class IncidentUiState(
    val incidentType: IncidentType? = null,
    val description: String = "",
    val submitting: Boolean = false,
    val showConfirm: Boolean = false,
    val errorMessage: String? = null,
    val submitted: Boolean = false,
)

class IncidentViewModel(
    private val tripId: Long,
    private val driverRepository: DriverRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(IncidentUiState())
    val uiState: StateFlow<IncidentUiState> = _uiState

    fun onTypeSelected(type: IncidentType) {
        _uiState.update { it.copy(incidentType = type, errorMessage = null) }
    }

    fun onDescriptionChange(value: String) {
        if (value.length <= INCIDENT_DESCRIPTION_MAX_LENGTH) {
            _uiState.update { it.copy(description = value, errorMessage = null) }
        }
    }

    fun askSubmit() {
        val state = _uiState.value
        if (state.incidentType == null) {
            _uiState.update { it.copy(errorMessage = "Seleccione el tipo de incidencia.") }
            return
        }
        if (state.description.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingrese una descripción.") }
            return
        }
        _uiState.update { it.copy(showConfirm = true) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(showConfirm = false) }
    }

    fun confirmSubmit() {
        val state = _uiState.value
        val type = state.incidentType ?: return
        _uiState.update { it.copy(showConfirm = false, submitting = true, errorMessage = null) }
        scope.launch {
            val result = driverRepository.reportIncident(tripId, type.name, state.description.trim())
            result.fold(
                onSuccess = { _uiState.update { it.copy(submitting = false, submitted = true) } },
                onFailure = { error ->
                    _uiState.update { it.copy(submitting = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Validation -> error.message
        is AppError.Network -> "Sin conexión a internet."
        else -> "No se pudo reportar la incidencia."
    }
}
