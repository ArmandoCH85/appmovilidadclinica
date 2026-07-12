package com.appmovilidadclinica.passenger.presentation.myreservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appmovilidadclinica.passenger.domain.model.Reservation
import com.appmovilidadclinica.passenger.domain.repository.ReservationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lista viene de Room (fuente de verdad local), no de una llamada de red —
 * ver diseño técnico. Inyecta `ReservationsRepository` directo (ver memoria
 * "android-passenger-module/ponytail-audit").
 */
@HiltViewModel
class MyReservationsViewModel @Inject constructor(
    reservationsRepository: ReservationsRepository,
) : ViewModel() {
    val reservations: StateFlow<List<Reservation>> = reservationsRepository.observeReservations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
