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
 * `init` dispara un sync contra `GET /api/reservations` para que la cache
 * local refleje el estado real del backend. Sin esto, una reserva creada
 * en otro dispositivo o sesion nunca aparece (Room esta vacia al
 * respecto). El `observeReservations()` sigue siendo la fuente de verdad
 * para la UI — el sync solo escribe en Room, no emite directo.
 */
@HiltViewModel
class MyReservationsViewModel @Inject constructor(
    private val reservationsRepository: ReservationsRepository,
) : ViewModel() {
    val reservations: StateFlow<List<Reservation>> = reservationsRepository.observeReservations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            reservationsRepository.syncFromBackend()
        }
    }
}
