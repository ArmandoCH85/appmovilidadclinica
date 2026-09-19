package com.appmovilidadclinica.driver.shared.ui.screens.seatmap

import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeatMapUiState(
    val stops: List<TripStop> = emptyList(),
    val originStopId: Long? = null,
    val destinationStopId: Long? = null,
    val seats: List<SeatAvailability> = emptyList(),
    val loadingStops: Boolean = true,
    val loadingSeats: Boolean = false,
    val selectedSeatId: Long? = null,
    val firstName: String = "",
    val lastName: String = "",
    val showGuestDialog: Boolean = false,
    val fieldError: String? = null,
    val errorMessage: String? = null,
    val submitting: Boolean = false,
    val registered: Boolean = false,
)

class SeatMapViewModel(
    private val tripId: Long,
    private val driverRepository: DriverRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(SeatMapUiState())
    val uiState: StateFlow<SeatMapUiState> = _uiState

    fun load() {
        _uiState.update { it.copy(loadingStops = true, errorMessage = null) }
        scope.launch {
            val result = driverRepository.getTripStops(tripId)
            result.fold(
                onSuccess = { stops ->
                    val ordered = stops.sortedBy { it.stopOrder }
                    _uiState.update {
                        it.copy(
                            loadingStops = false,
                            stops = ordered,
                            originStopId = ordered.firstOrNull()?.id,
                            destinationStopId = ordered.lastOrNull()?.id,
                            errorMessage = null,
                        )
                    }
                    loadSeats()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(loadingStops = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    fun onOriginSelected(stopId: Long) {
        _uiState.update { it.copy(originStopId = stopId, errorMessage = null) }
        loadSeats()
    }

    fun onDestinationSelected(stopId: Long) {
        _uiState.update { it.copy(destinationStopId = stopId, errorMessage = null) }
        loadSeats()
    }

    fun onSeatSelected(seat: SeatAvailability) {
        if (!seat.isAvailable) return
        _uiState.update {
            it.copy(
                selectedSeatId = seat.tripSeatId,
                showGuestDialog = true,
                fieldError = null,
                errorMessage = null,
            )
        }
    }

    fun onFirstNameChange(value: String) {
        _uiState.update { it.copy(firstName = value, fieldError = null) }
    }

    fun onLastNameChange(value: String) {
        _uiState.update { it.copy(lastName = value, fieldError = null) }
    }

    fun dismissGuestDialog() {
        _uiState.update { it.copy(showGuestDialog = false, selectedSeatId = null, fieldError = null) }
    }

    fun submit() {
        val state = _uiState.value
        val seatId = state.selectedSeatId
        val originId = state.originStopId
        val destinationId = state.destinationStopId
        if (state.firstName.trim().isEmpty() || state.lastName.trim().isEmpty()) {
            _uiState.update { it.copy(fieldError = "Ingrese nombre y apellido.") }
            return
        }
        if (seatId == null || originId == null || destinationId == null) return
        _uiState.update { it.copy(submitting = true, fieldError = null, errorMessage = null) }
        scope.launch {
            val result = driverRepository.registerGuest(
                tripId = tripId,
                tripSeatId = seatId,
                originTripStopTimeId = originId,
                destinationTripStopTimeId = destinationId,
                firstName = state.firstName.trim(),
                lastName = state.lastName.trim(),
            )
            result.fold(
                onSuccess = { _uiState.update { it.copy(submitting = false, registered = true) } },
                onFailure = { error ->
                    _uiState.update { it.copy(submitting = false, errorMessage = messageFor(error)) }
                },
            )
        }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun loadSeats() {
        val state = _uiState.value
        val originId = state.originStopId
        val destinationId = state.destinationStopId
        val originOrder = state.stops.firstOrNull { it.id == originId }?.stopOrder
        val destinationOrder = state.stops.firstOrNull { it.id == destinationId }?.stopOrder
        if (originId == null || destinationId == null || originOrder == null || destinationOrder == null) return
        if (originOrder >= destinationOrder) {
            _uiState.update { it.copy(seats = emptyList(), loadingSeats = false, selectedSeatId = null) }
            return
        }
        _uiState.update { it.copy(loadingSeats = true, errorMessage = null, selectedSeatId = null) }
        scope.launch {
            val result = driverRepository.getSeats(tripId, originId, destinationId)
            result.fold(
                onSuccess = { seats ->
                    _uiState.update {
                        it.copy(loadingSeats = false, seats = seats.sortedBy { s -> s.seatNumber })
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(loadingSeats = false, seats = emptyList(), errorMessage = messageFor(error))
                    }
                },
            )
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is AppError.Network -> "Sin conexión a internet."
        is AppError -> error.message ?: "No se pudo registrar al pasajero."
        else -> "No se pudo registrar al pasajero."
    }
}
