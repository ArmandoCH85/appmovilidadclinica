package com.appmovilidadclinica.driver.presentation.tripdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.driver.domain.model.AppError
import com.appmovilidadclinica.driver.domain.model.DriverTrip
import com.appmovilidadclinica.driver.domain.model.Passenger
import com.appmovilidadclinica.driver.domain.model.TripStop
import com.appmovilidadclinica.driver.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.presentation.common.SelectedTripHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val trip: DriverTrip? = null,
    val passengers: List<Passenger> = emptyList(),
    val stops: List<TripStop> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val stopsErrorMessage: String? = null,
    val toastMessage: String? = null,
    val pendingActionId: Long? = null,
)

class TripDetailViewModel(
    private val tripId: Long,
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TripDetailUiState(trip = SelectedTripHolder.trip?.takeIf { it.id == tripId }),
    )
    val uiState: StateFlow<TripDetailUiState> = _uiState

    fun load() {
        _uiState.update { it.copy(loading = true, errorMessage = null, stopsErrorMessage = null) }
        viewModelScope.launch {
            // Pasajeros y paradas son independientes: si el backend de paradas
            // todavia no esta desplegado (o falla), igual mostramos la lista de
            // pasajeros en vez de tapar toda la pantalla con un error.
            val passengersResult = driverRepository.getPassengers(tripId)
            val stopsResult = driverRepository.getTripStops(tripId)

            val passengersError = passengersResult.exceptionOrNull()
            if (passengersError != null) {
                _uiState.update { it.copy(loading = false, errorMessage = messageFor(passengersError)) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    loading = false,
                    passengers = passengersResult.getOrDefault(emptyList())
                        .sortedBy { p -> p.originStopOrder },
                    stops = stopsResult.getOrDefault(emptyList()).sortedBy { s -> s.stopOrder },
                    stopsErrorMessage = stopsResult.exceptionOrNull()?.let(::messageFor),
                )
            }
        }
    }

    fun board(reservationId: Long) = runAction(reservationId, "Pasajero abordado") {
        driverRepository.markBoarded(reservationId)
    }

    fun noShow(reservationId: Long) = runAction(reservationId, "No presentado registrado") {
        driverRepository.markNoShow(reservationId)
    }

    fun alight(reservationId: Long) = runAction(reservationId, "Bajada registrada") {
        driverRepository.markAlighted(reservationId)
    }

    fun markArrival(tripStopTimeId: Long) = runAction(tripStopTimeId, "Llegada marcada") {
        driverRepository.markArrival(tripStopTimeId)
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun runAction(actionId: Long, successMessage: String, action: suspend () -> Result<Unit>) {
        _uiState.update { it.copy(pendingActionId = actionId) }
        viewModelScope.launch {
            val result = action()
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(pendingActionId = null, toastMessage = successMessage) }
                    load()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(pendingActionId = null, toastMessage = messageFor(error))
                    }
                },
            )
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Conflict -> error.message
        is AppError.Forbidden -> "No está asignado a este viaje."
        is AppError.Network -> "Sin conexión a internet."
        else -> "Ocurrió un error inesperado."
    }
}
