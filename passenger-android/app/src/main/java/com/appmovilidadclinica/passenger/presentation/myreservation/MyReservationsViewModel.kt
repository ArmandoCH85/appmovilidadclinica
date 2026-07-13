package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lista viene de Room (fuente de verdad local), no de una llamada de red —
 * ver diseño técnico. Inyecta `ReservationsRepository` directo (ver memoria
 * "android-passenger-module/ponytail-audit").
 *
 * El sync contra `GET /api/reservations` se dispara desde la pantalla via
 * `repeatOnLifecycle(RESUMED)`, no en el `init` del ViewModel — asi se
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

    /**
     * Llama al backend para sincronizar la cache local con la lista de
     * reservas del WORKER autenticado. Disparado desde la pantalla en cada
     * ON_RESUME via `repeatOnLifecycle`. Fire-and-forget: si falla (red,
     * 5xx), la UI queda con lo que ya tenia en Room; no hay error visual
     * salvo si el usuario quiere forzarlo con un pull-to-refresh futuro.
     */
    fun sync() {
        viewModelScope.launch {
            reservationsRepository.syncFromBackend()
        }
    }
}
