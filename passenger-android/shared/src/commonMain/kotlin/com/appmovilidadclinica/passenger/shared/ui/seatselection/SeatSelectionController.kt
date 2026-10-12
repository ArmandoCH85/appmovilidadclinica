package com.appmovilidadclinica.passenger.shared.ui.seatselection

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationCreated
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationRequest
import com.appmovilidadclinica.passenger.shared.domain.model.TripDetail
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat
import com.appmovilidadclinica.passenger.shared.domain.model.TripStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class SeatSelectionUiState(
    val loading: Boolean = true,
    val tripDetail: TripDetail? = null,
    val origin: TripStop? = null,
    val destination: TripStop? = null,
    val seats: List<TripSeat> = emptyList(),
    val selectedSeatId: Long? = null,
    val confirming: Boolean = false,
    val errorMessage: String? = null,
    val confirmed: SeatSelectionConfirmation? = null,
)

/** Datos para mostrar la confirmacion (el `qrToken` solo se puede guardar una vez). */
data class SeatSelectionConfirmation(
    val reservationId: Long,
    val reservationCode: String,
    val qrToken: String,
    val originName: String,
    val destinationName: String,
    val seatLabel: String,
    val departureAt: Instant?,
)

/**
 * Seleccion de asiento + confirmacion de reserva, comun a Android e iOS.
 *
 * Conserva las reglas que ya tenia el modulo Android:
 *  - el origen debe preceder al destino en `stop_order` (se valida aca, antes
 *    de gastar la llamada: el backend responde 409 igual);
 *  - las paradas elegidas tienen que pertenecer al viaje;
 *  - no se puede confirmar sin asiento elegido, y no se envia dos veces.
 */
class SeatSelectionController(
    private val tripId: Long,
    private val originStopId: Long,
    private val destinationStopId: Long,
    private val loadTripDetail: suspend (Long) -> AppResult<TripDetail>,
    private val listSeats: suspend (Long, Long, Long) -> AppResult<List<TripSeat>>,
    private val confirmReservation: suspend (ReservationRequest) -> AppResult<ReservationCreated>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(SeatSelectionUiState())
    val uiState: StateFlow<SeatSelectionUiState> = _uiState

    private var started = false

    fun load() {
        if (started) return
        started = true
        scope.launch {
            when (val detailResult = loadTripDetail(tripId)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = "No se pudo cargar el viaje.")
                }

                is AppResult.Success -> {
                    val detail = detailResult.data
                    val origin = detail.stops.find { it.stopId == originStopId }
                    val destination = detail.stops.find { it.stopId == destinationStopId }
                    if (origin == null || destination == null) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                errorMessage = "Las paradas elegidas no pertenecen a este viaje.",
                            )
                        }
                        return@launch
                    }
                    if (origin.stopOrder >= destination.stopOrder) {
                        _uiState.update {
                            it.copy(
                                tripDetail = detail,
                                origin = origin,
                                destination = destination,
                                loading = false,
                                errorMessage = "El origen debe ir antes que el destino en la ruta.",
                            )
                        }
                        return@launch
                    }

                    _uiState.update {
                        it.copy(tripDetail = detail, origin = origin, destination = destination)
                    }

                    when (val seatsResult = listSeats(tripId, origin.tripStopTimeId, destination.tripStopTimeId)) {
                        is AppResult.Success -> _uiState.update {
                            it.copy(loading = false, seats = seatsResult.data)
                        }

                        is AppResult.Failure -> _uiState.update {
                            it.copy(loading = false, errorMessage = "No se pudieron cargar los asientos.")
                        }
                    }
                }
            }
        }
    }

    fun selectSeat(tripSeatId: Long) {
        val seat = _uiState.value.seats.find { it.tripSeatId == tripSeatId }
        if (seat != null && !seat.isSelectable) return
        _uiState.update { it.copy(selectedSeatId = tripSeatId, errorMessage = null) }
    }

    fun confirm() {
        val state = _uiState.value
        if (state.confirming || state.confirmed != null) return

        val origin = state.origin
        val destination = state.destination
        val seatId = state.selectedSeatId
        if (origin == null || destination == null || seatId == null) {
            _uiState.update { it.copy(errorMessage = "Elija un asiento disponible.") }
            return
        }

        val seatLabel = state.seats.find { it.tripSeatId == seatId }?.seatLabel.orEmpty()
        _uiState.update { it.copy(confirming = true, errorMessage = null) }

        scope.launch {
            val request = ReservationRequest(
                tripId = tripId,
                tripSeatId = seatId,
                originTripStopTimeId = origin.tripStopTimeId,
                destinationTripStopTimeId = destination.tripStopTimeId,
            )
            when (val result = confirmReservation(request)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        confirming = false,
                        confirmed = SeatSelectionConfirmation(
                            reservationId = result.data.reservationId,
                            reservationCode = result.data.reservationCode,
                            qrToken = result.data.qrToken,
                            originName = origin.stopName,
                            destinationName = destination.stopName,
                            seatLabel = seatLabel,
                            departureAt = origin.scheduledDepartureAt,
                        ),
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(confirming = false, errorMessage = messageFor(result.error))
                }
            }
        }
    }

    /** Tras confirmar, la pantalla vuelve al inicio (o al detalle en la Etapa 4). */
    fun consumeConfirmation() {
        _uiState.update { it.copy(confirmed = null) }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun messageFor(error: AppError): String = when (error) {
        // Los 409 traen el mensaje del backend en espanol (asiento ocupado, etc).
        is AppError.Conflict -> error.message
        is AppError.Network -> "Sin conexión a internet."
        else -> "No se pudo confirmar la reserva. Intente nuevamente."
    }
}
