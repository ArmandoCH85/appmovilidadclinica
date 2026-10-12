package com.appmovilidadclinica.passenger.shared.ui.reservations

import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.shared.domain.model.ReservationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReservationsUiState(
    val loading: Boolean = true,
    val reservations: List<Reservation> = emptyList(),
    val errorMessage: String? = null,
    val cancelling: Boolean = false,
    val cancelledReservationId: Long? = null,
    /** Aviso puntual (exito o error) al cancelar. */
    val notice: String? = null,
) {
    /** Reservas que todavia se pueden cancelar. */
    val cancellableStatuses = setOf(ReservationStatus.CONFIRMED, ReservationStatus.BOARDED)
}

/**
 * Mis reservas (lista) + cancelar, comun a Android e iOS.
 * Mismas reglas que el modulo Android: se listan todas las del trabajador y
 * solo se puede cancelar una reserva activa.
 */
class ReservationsController(
    private val loadReservations: suspend () -> AppResult<List<Reservation>>,
    private val cancelReservation: suspend (Long) -> AppResult<Unit>,
    private var scope: CoroutineScope = newScope(),
) {
    private val _uiState = MutableStateFlow(ReservationsUiState())
    val uiState: StateFlow<ReservationsUiState> = _uiState

    private var loaded = false

    /**
     * Si el scope se cancelo (p.ej. tras cerrar sesion), se crea uno nuevo:
     * asi la pantalla nunca queda trabada en "cargando".
     */
    private fun activeScope(): CoroutineScope {
        if (scope.coroutineContext[Job]?.isActive != true) scope = newScope()
        return scope
    }

    fun load(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        _uiState.update { it.copy(loading = true) }
        activeScope().launch {
            when (val result = loadReservations()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(loading = false, reservations = result.data, errorMessage = null)
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorMessage = messageFor(result.error))
                }
            }
        }
    }

    fun reservationById(reservationId: Long): Reservation? =
        _uiState.value.reservations.find { it.reservationId == reservationId }

    fun cancel(reservationId: Long) {
        val state = _uiState.value
        if (state.cancelling) return
        _uiState.update { it.copy(cancelling = true, notice = null) }
        activeScope().launch {
            when (val result = cancelReservation(reservationId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            cancelling = false,
                            cancelledReservationId = reservationId,
                            notice = "Reserva cancelada.",
                            // Se refleja al toque; el detalle del backend lo confirma
                            // en el proximo load().
                            reservations = it.reservations.map { reservation ->
                                if (reservation.reservationId == reservationId) {
                                    reservation.copy(status = ReservationStatus.CANCELLED)
                                } else {
                                    reservation
                                }
                            },
                        )
                    }
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(cancelling = false, notice = messageFor(result.error))
                }
            }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun dispose() {
        scope.cancel()
    }
    private fun messageFor(error: AppError): String = when (error) {
        is AppError.Network -> "No se pudo conectar con el servidor. Verifique su conexión."
        is AppError.Unauthorized -> error.message.ifBlank { "Sesión expirada. Inicie sesión nuevamente." }
        is AppError.Conflict -> error.message.ifBlank { "La reserva ya no se puede cancelar." }
        is AppError.NotFound -> error.message.ifBlank { "La reserva ya no existe." }
        else -> error.messageOrEmpty().ifBlank { "No se pudieron cargar sus reservas." }
    }
}

private fun AppError.messageOrEmpty(): String = when (this) {
    is AppError.Unauthorized -> message
    is AppError.Forbidden -> message
    is AppError.NotFound -> message
    is AppError.Conflict -> message
    is AppError.Validation -> message
    is AppError.Network -> message
    is AppError.Unknown -> message
}

private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
