package com.appmovilidadclinica.passenger.presentation.reportincident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportIncidentUiState(
    val selectedReservationId: Long? = null,
    val incidentType: String = "",
    val description: String = "",
    val reservationError: String? = null,
    val typeError: String? = null,
    val descriptionError: String? = null,
    val formError: String? = null,
    val submitting: Boolean = false,
    val sent: Boolean = false,
)

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    private val reservationsRepository: ReservationsRepository,
) : ViewModel() {

    /** Reservas activas (fuente: Room, igual que Mis reservas). */
    val activeReservations: StateFlow<List<Reservation>> = reservationsRepository.observeReservations()
        .map { list ->
            list.filter {
                it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.BOARDED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(ReportIncidentUiState())
    val uiState: StateFlow<ReportIncidentUiState> = _uiState

    fun onReservationSelected(id: Long) {
        _uiState.update { it.copy(selectedReservationId = id, reservationError = null, formError = null) }
    }

    fun onTypeChange(value: String) {
        _uiState.update { it.copy(incidentType = value, typeError = null, formError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value.take(1000), descriptionError = null, formError = null) }
    }

    fun submit() {
        val s = _uiState.value
        val reservationError = if (s.selectedReservationId == null) "Elige el viaje sobre el que reportas." else null
        val typeError = if (s.incidentType.isBlank()) "Elige el tipo de incidente." else null
        val descriptionError = when {
            s.description.isBlank() -> "Describe lo ocurrido."
            s.description.trim().length < 10 -> "Mínimo 10 caracteres."
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
        viewModelScope.launch {
            when (
                val result = reservationsRepository.reportIncident(
                    s.selectedReservationId!!,
                    s.incidentType,
                    s.description.trim(),
                )
            ) {
                is AppResult.Success -> _uiState.update { it.copy(submitting = false, sent = true) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, formError = messageFor(result.error))
                }
            }
        }
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
