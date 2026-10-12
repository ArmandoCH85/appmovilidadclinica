package com.appmovilidadclinica.passenger.shared.ui.reportincident

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipos de incidente que acepta el backend (`incident_type`). */
enum class IncidentType(val code: String, val label: String) {
    DELAY("DELAY", "Retraso / el bus no pasó"),
    BREAKDOWN("BREAKDOWN", "Problemas con la unidad"),
    ACCIDENT("ACCIDENT", "Accidente"),
    OTHER("OTHER", "Otro"),
}

data class ReportIncidentUiState(
    val loadingReservations: Boolean = true,
    val activeReservations: List<Reservation> = emptyList(),
    val selectedReservationId: Long? = null,
    val incidentType: IncidentType? = null,
    val description: String = "",
    val reservationError: String? = null,
    val typeError: String? = null,
    val descriptionError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
    val sent: Boolean = false,
)

/**
 * Reportar incidencia sobre una reserva activa, comun a Android e iOS.
 * Mismas validaciones que el modulo Android: reserva elegida, tipo elegido y
 * descripcion de al menos 10 caracteres (tope 1000).
 */
class ReportIncidentController(
    private val loadReservations: suspend () -> AppResult<List<Reservation>>,
    private val reportIncident: suspend (reservationId: Long, typeCode: String, description: String) -> AppResult<Long>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(ReportIncidentUiState())
    val uiState: StateFlow<ReportIncidentUiState> = _uiState

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        _uiState.update { it.copy(loadingReservations = true) }
        scope.launch {
            when (val result = loadReservations()) {
                is AppResult.Success -> _uiState.update { state ->
                    val active = result.data.filter {
                        it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.BOARDED
                    }
                    state.copy(
                        loadingReservations = false,
                        activeReservations = active,
                        // Si hay una sola, se elige sola (menos toques).
                        selectedReservationId = state.selectedReservationId
                            ?: active.singleOrNull()?.reservationId,
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(loadingReservations = false, formError = messageFor(result.error))
                }
            }
        }
    }

    fun onReservationSelected(id: Long) {
        _uiState.update { it.copy(selectedReservationId = id, reservationError = null, formError = null) }
    }

    fun onTypeChange(type: IncidentType) {
        _uiState.update { it.copy(incidentType = type, typeError = null, formError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update {
            it.copy(description = value.take(1000), descriptionError = null, formError = null)
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return

        val reservationError = if (state.selectedReservationId == null) {
            "Elige el viaje sobre el que reportas."
        } else {
            null
        }
        val typeError = if (state.incidentType == null) "Elige el tipo de incidente." else null
        val descriptionError = when {
            state.description.isBlank() -> "Describe lo ocurrido."
            state.description.trim().length < 10 -> "Mínimo 10 caracteres."
            else -> null
        }
        if (reservationError != null || typeError != null || descriptionError != null) {
            _uiState.update {
                it.copy(
                    reservationError = reservationError,
                    typeError = typeError,
                    descriptionError = descriptionError,
                )
            }
            return
        }

        _uiState.update { it.copy(submitting = true, formError = null) }
        scope.launch {
            when (
                val result = reportIncident(
                    state.selectedReservationId!!,
                    state.incidentType!!.code,
                    state.description.trim(),
                )
            ) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false, sent = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, formError = messageFor(result.error))
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Conflict -> error.message.ifBlank { "Ese viaje ya no está activo para reportar." }
        is AppError.Validation -> error.message
        is AppError.NotFound -> error.message.ifBlank { "La reserva ya no existe." }
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        is AppError.Unauthorized -> error.message.ifBlank { "Sesión expirada. Inicie sesión nuevamente." }
        is AppError.Forbidden -> error.message
        is AppError.Unknown -> error.message.ifBlank { "Ocurrió un error inesperado. Intente nuevamente." }
    }
}
