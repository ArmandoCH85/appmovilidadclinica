package com.appmovilidadclinica.passenger.shared.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Rutas de la app compartida (Android + iOS). Navegacion por estado, sin
 * `androidx.navigation` (que es Android-only), igual que la app del conductor.
 */
sealed interface PassengerRoute {
    /** Pantalla autenticada de inicio (accesos a cada flujo). */
    data object Home : PassengerRoute

    /** Busqueda de viajes. */
    data object TripSearch : PassengerRoute

    /** Seleccion de asiento del viaje elegido. */
    data class TripDetail(
        val tripId: Long,
        val originStopId: Long,
        val destinationStopId: Long,
    ) : PassengerRoute

    /** Mis reservas. */
    data object Reservations : PassengerRoute

    /** Detalle de una reserva. */
    data class ReservationDetail(val reservationId: Long) : PassengerRoute

    /** Cambio de contraseña. */
    data object ChangePassword : PassengerRoute

    /** Reporte de incidencia del viaje en curso. */
    data object ReportIncident : PassengerRoute
}

/**
 * Pila de navegacion comun a las dos plataformas. Mismo contrato que el
 * `DriverNavState` del conductor: push / pop / replace / resetTo.
 */
class PassengerNavState(initial: PassengerRoute = PassengerRoute.Home) {

    private val _stack = MutableStateFlow(listOf(initial))
    val stack: StateFlow<List<PassengerRoute>> = _stack

    val current: PassengerRoute get() = _stack.value.last()

    fun push(route: PassengerRoute) {
        _stack.update { it + route }
    }

    fun pop() {
        _stack.update { if (it.size > 1) it.dropLast(1) else it }
    }

    fun replace(route: PassengerRoute) {
        _stack.update { it.dropLast(1) + route }
    }

    fun resetTo(route: PassengerRoute) {
        _stack.value = listOf(route)
    }
}
