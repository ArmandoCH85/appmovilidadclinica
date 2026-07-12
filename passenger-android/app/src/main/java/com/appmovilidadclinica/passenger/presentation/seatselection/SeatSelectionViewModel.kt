package com.appmovilidadclinica.passenger.presentation.seatselection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.domain.model.TripDetail
import com.appmovilidadclinica.passenger.domain.model.TripSeat
import com.appmovilidadclinica.passenger.domain.model.TripStop
import com.appmovilidadclinica.passenger.domain.repository.ReservationTripContext
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import com.appmovilidadclinica.passenger.domain.repository.TripsRepository
import com.appmovilidadclinica.passenger.domain.usecase.ListSeatsUseCase
import com.appmovilidadclinica.passenger.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeatSelectionUiState(
    val loading: Boolean = true,
    val tripDetail: TripDetail? = null,
    val origin: TripStop? = null,
    val destination: TripStop? = null,
    val seats: List<TripSeat> = emptyList(),
    val selectedSeatId: Long? = null,
    val confirming: Boolean = false,
    val errorMessage: String? = null,
    val confirmedReservationId: Long? = null,
)

/**
 * `GetTripDetailUseCase`/`ConfirmReservationUseCase` se eliminaron (solo
 * delegaban) — este ViewModel inyecta `TripsRepository`/`ReservationsRepository`
 * directo. `ListSeatsUseCase` SÍ se conserva: valida que origen preceda a
 * destino antes de pegarle al backend (ver esa clase). Ver memoria
 * "android-passenger-module/ponytail-audit".
 */
@HiltViewModel
class SeatSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripsRepository: TripsRepository,
    private val listSeatsUseCase: ListSeatsUseCase,
    private val reservationsRepository: ReservationsRepository,
) : ViewModel() {

    private val route: Screen.SeatSelection = savedStateHandle.toRoute<Screen.SeatSelection>()

    private val _uiState = MutableStateFlow(SeatSelectionUiState())
    val uiState: StateFlow<SeatSelectionUiState> = _uiState

    init {
        loadTripAndSeats()
    }

    private fun loadTripAndSeats() {
        viewModelScope.launch {
            when (val detailResult = tripsRepository.getDetail(route.tripId)) {
                is AppResult.Failure -> {
                    _uiState.update { it.copy(loading = false, errorMessage = "No se pudo cargar el viaje.") }
                    return@launch
                }
                is AppResult.Success -> {
                    val detail = detailResult.data
                    val origin = detail.stops.find { it.stopId == route.originStopId }
                    val destination = detail.stops.find { it.stopId == route.destinationStopId }
                    if (origin == null || destination == null) {
                        _uiState.update {
                            it.copy(loading = false, errorMessage = "Las paradas elegidas no pertenecen a este viaje.")
                        }
                        return@launch
                    }
                    _uiState.update { it.copy(tripDetail = detail, origin = origin, destination = destination) }

                    when (val seatsResult = listSeatsUseCase(route.tripId, origin, destination)) {
                        is AppResult.Success -> _uiState.update { it.copy(loading = false, seats = seatsResult.data) }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(loading = false, errorMessage = "No se pudieron cargar los asientos.")
                        }
                    }
                }
            }
        }
    }

    fun selectSeat(tripSeatId: Long) {
        _uiState.update { it.copy(selectedSeatId = tripSeatId, errorMessage = null) }
    }

    fun confirm() {
        val state = _uiState.value
        val origin = state.origin
        val destination = state.destination
        val seatId = state.selectedSeatId
        val trip = state.tripDetail
        if (origin == null || destination == null || seatId == null || trip == null) return

        val seatLabel = state.seats.find { it.tripSeatId == seatId }?.seatLabel.orEmpty()
        _uiState.update { it.copy(confirming = true, errorMessage = null) }
        viewModelScope.launch {
            val request = ReservationRequest(
                tripId = trip.tripId,
                tripSeatId = seatId,
                originTripStopTimeId = origin.tripStopTimeId,
                destinationTripStopTimeId = destination.tripStopTimeId,
            )
            val context = ReservationTripContext(
                routeName = trip.tripCode,
                originName = origin.stopName,
                destinationName = destination.stopName,
                originDepartureAt = origin.scheduledDepartureAt,
                seatLabel = seatLabel,
            )
            when (val result = reservationsRepository.confirm(request, context)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(confirming = false, confirmedReservationId = result.data.reservationId)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(confirming = false, errorMessage = messageFor(result.error))
                }
            }
        }
    }

    private fun messageFor(error: com.appmovilidadclinica.passenger.domain.error.AppError): String = when (error) {
        is com.appmovilidadclinica.passenger.domain.error.AppError.Conflict -> error.message
        else -> "No se pudo confirmar la reserva. Intente nuevamente."
    }
}
