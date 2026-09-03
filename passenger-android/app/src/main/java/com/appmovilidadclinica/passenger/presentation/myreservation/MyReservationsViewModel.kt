package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.shared.domain.error.AppError
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado transitorio del boton "Confirmar abordaje" de una fila puntual de la lista. */
data class ReservationRowState(
    val checkingIn: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Lista viene de Room (fuente de verdad local), no de una llamada de red â€”
 * ver diseÃ±o tÃ©cnico. Inyecta `ReservationsRepository` directo (ver memoria
 * "android-passenger-module/ponytail-audit").
 *
 * El sync contra `GET /api/reservations` se dispara desde la pantalla via
 * `repeatOnLifecycle(RESUMED)`, no en el `init` del ViewModel â€” asi se
 * re-ejecuta cada vez que el usuario vuelve a "Mis reservas" (otra app al
 * frente, navegacion de ida y vuelta, etc.). Sin esto, una reserva creada
 * en otro dispositivo/sesion solo apareceria la primera vez que se abre la
 * pantalla; el re-sync al resumir asegura que la cache local refleje el
 * estado actual del backend sin que el usuario tenga que tocar nada.
 */
@HiltViewModel
class MyReservationsViewModel @Inject constructor(
    private val reservationsRepository: ReservationsRepository,
) : ViewModel() {
    val reservations: StateFlow<List<Reservation>> = reservationsRepository.observeReservations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _rowStates = MutableStateFlow<Map<Long, ReservationRowState>>(emptyMap())
    val rowStates: StateFlow<Map<Long, ReservationRowState>> = _rowStates

    fun sync() {
        viewModelScope.launch {
            reservationsRepository.syncFromBackend()
        }
    }

    /**
     * Auto-confirmacion de abordaje directo desde la lista, sin entrar al
     * detalle de la reserva â€” mismo endpoint que
     * `MyReservationDetailViewModel.selfCheckin` (ver Specs #5, ventana
     * +-30min en `Reservation.canSelfCheckin()`).
     */
    fun selfCheckin(reservationId: Long) {
        if (_rowStates.value[reservationId]?.checkingIn == true) return
        _rowStates.update { it + (reservationId to ReservationRowState(checkingIn = true)) }
        viewModelScope.launch {
            when (val result = reservationsRepository.selfCheckin(reservationId)) {
                is AppResult.Success -> _rowStates.update { it - reservationId }
                is AppResult.Failure -> _rowStates.update {
                    it + (reservationId to ReservationRowState(checkingIn = false, errorMessage = errorMessageFor(result.error)))
                }
            }
        }
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.NotFound -> "Esta función todavía no está disponible en el servidor."
        is AppError.Conflict -> error.message
        else -> "Ocurrió un error inesperado. Intente nuevamente."
    }
}
